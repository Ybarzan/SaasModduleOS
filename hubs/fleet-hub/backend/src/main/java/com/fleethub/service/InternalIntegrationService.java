package com.fleethub.service;

import com.fleethub.dto.ExternalShipmentCreationSettingsDto;
import com.fleethub.dto.GeneratedIntegrationKeyDto;
import com.fleethub.dto.InternalIntegrationSettingsDto;
import com.fleethub.model.Company;
import com.fleethub.model.IntegrationApiKey;
import com.fleethub.model.IntegrationConsumer;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.IntegrationApiKeyRepository;
import com.fleethub.security.ApiKeyHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Partage opt-in des données d'une société avec les autres hubs de la plateforme
 * (compliance-engine, Logistix, Finance, portail Praxio). Jamais d'accès direct
 * entre les bases : ces hubs appellent {@code /api/internal/**} avec la clé qui
 * leur est propre (voir {@link IntegrationConsumer} pour les droits de chacune),
 * authentifiée par {@link com.fleethub.security.InternalApiKeyFilter}.
 */
@Service
@RequiredArgsConstructor
public class InternalIntegrationService {

    /** lastUsedAt n'est réécrit qu'au-delà de cet écart, pas à chaque appel. */
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(5);

    private final CompanyRepository companyRepository;
    private final IntegrationApiKeyRepository keyRepository;

    @Transactional(readOnly = true)
    public InternalIntegrationSettingsDto settings(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        Map<IntegrationConsumer, IntegrationApiKey> existing = keyRepository.findByCompanyIdOrderByConsumerAsc(companyId)
                .stream().collect(Collectors.toMap(IntegrationApiKey::getConsumer, Function.identity()));
        List<InternalIntegrationSettingsDto.Key> keys = new ArrayList<>();
        for (IntegrationConsumer consumer : IntegrationConsumer.values()) {
            IntegrationApiKey key = existing.get(consumer);
            if (consumer == IntegrationConsumer.LEGACY && key == null) {
                continue; // la clé historique n'apparaît que tant qu'elle existe
            }
            keys.add(new InternalIntegrationSettingsDto.Key(
                    consumer.name(), consumer.getLabel(),
                    consumer.getScopes().stream().map(Enum::name).sorted().toList(),
                    key != null,
                    key != null ? key.getKeyHint() : null,
                    key != null ? key.getCreatedAt() : null,
                    key != null ? key.getLastUsedAt() : null));
        }
        return new InternalIntegrationSettingsDto(company.isInternalIntegrationEnabled(), keys);
    }

    /** Interrupteur général : les clés existantes redeviennent valides. */
    @Transactional
    public void enable(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setInternalIntegrationEnabled(true);
        companyRepository.save(company);
    }

    /** Interrupteur général : toutes les clés sont rejetées, mais conservées pour une réactivation. */
    @Transactional
    public void disable(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setInternalIntegrationEnabled(false);
        companyRepository.save(company);
    }

    /**
     * Génère la clé d'un hub consommateur, ou la remplace si elle existe (rotation) :
     * l'ancienne est invalide immédiatement. La clé en clair n'est renvoyée qu'ici.
     */
    @Transactional
    public GeneratedIntegrationKeyDto generateKey(Long companyId, String consumerName) {
        IntegrationConsumer consumer = parseConsumer(consumerName);
        if (consumer == IntegrationConsumer.LEGACY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La clé historique ne peut plus être générée : créez une clé par hub");
        }
        Company company = companyRepository.findById(companyId).orElseThrow();
        keyRepository.findByCompanyIdAndConsumer(companyId, consumer).ifPresent(old -> {
            keyRepository.delete(old);
            keyRepository.flush(); // libère la contrainte (company_id, consumer) avant l'insertion
        });
        String plain = Company.generateInternalIntegrationApiKey();
        IntegrationApiKey key = new IntegrationApiKey();
        key.setCompany(company);
        key.setConsumer(consumer);
        key.setKeyHash(ApiKeyHasher.hash(plain));
        key.setKeyHint(plain.substring(plain.length() - 4));
        key.setCreatedAt(LocalDateTime.now());
        keyRepository.save(key);
        return new GeneratedIntegrationKeyDto(consumer.name(), plain);
    }

    @Transactional
    public void revokeKey(Long companyId, String consumerName) {
        IntegrationConsumer consumer = parseConsumer(consumerName);
        keyRepository.findByCompanyIdAndConsumer(companyId, consumer).ifPresent(keyRepository::delete);
    }

    /**
     * Clé présentée par un hub -> clé enregistrée, si elle existe et que le partage
     * de sa société est activé. La société vient toujours de la clé, jamais d'un
     * identifiant fourni par l'appelant.
     */
    @Transactional
    public Optional<IntegrationApiKey> authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        Optional<IntegrationApiKey> key = keyRepository.findByKeyHash(ApiKeyHasher.hash(apiKey))
                .filter(k -> k.getCompany().isInternalIntegrationEnabled());
        key.ifPresent(k -> {
            LocalDateTime now = LocalDateTime.now();
            if (k.getLastUsedAt() == null || k.getLastUsedAt().isBefore(now.minus(LAST_USED_RESOLUTION))) {
                keyRepository.touch(k.getId(), now);
            }
        });
        return key;
    }

    /**
     * Opt-in SÉPARÉ (ADR-0002) : une clé ayant le droit SHIPMENTS_CREATE ne suffit
     * pas, la société doit aussi avoir autorisé la création d'expéditions externes.
     */
    @Transactional(readOnly = true)
    public void requireShipmentCreationAllowed(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        if (!company.isAllowExternalShipmentCreation()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Création d'expédition externe non activée pour cette société (voir Paramètres > Intégrations)");
        }
    }

    @Transactional(readOnly = true)
    public ExternalShipmentCreationSettingsDto shipmentCreationSettings(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        return new ExternalShipmentCreationSettingsDto(company.isAllowExternalShipmentCreation());
    }

    @Transactional
    public ExternalShipmentCreationSettingsDto setShipmentCreationEnabled(Long companyId, boolean enabled) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setAllowExternalShipmentCreation(enabled);
        companyRepository.save(company);
        return new ExternalShipmentCreationSettingsDto(enabled);
    }

    private static IntegrationConsumer parseConsumer(String name) {
        return Arrays.stream(IntegrationConsumer.values())
                .filter(c -> c.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hub consommateur inconnu : " + name));
    }
}
