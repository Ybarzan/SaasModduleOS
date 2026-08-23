package com.fleethub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.config.ApiKeyCrypto;
import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.*;
import com.fleethub.model.Company;
import com.fleethub.model.IntegrationConfig;
import com.fleethub.model.IntegrationProvider;
import com.fleethub.repository.IntegrationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Configuration self-service des intégrations externes d'une société cliente :
 * enregistrement des fournisseurs (GPS, tachygraphe, carburant, DHL…), test de
 * connexion et clé de webhook (push) remise au fournisseur. Les clés API sont
 * chiffrées au repos et jamais renvoyées par l'API.
 */
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

    private final IntegrationConfigRepository repository;
    private final ApiKeyCrypto crypto;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    // ---- Lecture ----

    @Transactional(readOnly = true)
    public List<IntegrationConfigDto> list(Long companyId) {
        return repository.findByCompanyId(companyId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderMetaDto> providers() {
        List<ProviderMetaDto> out = new ArrayList<>();
        for (IntegrationProvider p : IntegrationProvider.values()) {
            out.add(new ProviderMetaDto(p, p.getLabel(), p.getCategory(), p.getDefaultBaseUrl(),
                    p.usesBearerToken(), fieldsFor(p)));
        }
        return out;
    }

    // ---- Écriture ----

    @Transactional
    public IntegrationConfigDto create(Long companyId, IntegrationConfigRequest request) {
        validate(request);
        IntegrationConfig config = new IntegrationConfig();
        config.setCompany(requireCompanyEntity(companyId));
        config.setProvider(request.provider());
        config.setBaseUrl(normalizeUrl(request.baseUrl()));
        config.setApiKey(crypto.encrypt(request.apiKey()));
        config.setSettings(writeSettings(request.settings()));
        config.setWebhookKey(UUID.randomUUID().toString());
        config.setEnabled(request.isEnabled());
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(config));
    }

    @Transactional
    public IntegrationConfigDto update(Long companyId, Long id, IntegrationConfigRequest request) {
        IntegrationConfig config = requireConfig(companyId, id);
        if (request.provider() != null) {
            config.setProvider(request.provider());
        }
        if (request.baseUrl() != null) {
            config.setBaseUrl(normalizeUrl(request.baseUrl()));
        }
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.setApiKey(crypto.encrypt(request.apiKey()));
        }
        if (request.settings() != null) {
            config.setSettings(writeSettings(request.settings()));
        }
        if (request.enabled() != null) {
            config.setEnabled(request.isEnabled());
        }
        config.setUpdatedAt(LocalDateTime.now());
        return toDto(repository.save(config));
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        IntegrationConfig config = requireConfig(companyId, id);
        repository.delete(config);
    }

    // ---- Test de connexion ----

    /** Teste une configuration déjà enregistrée (utilise la clé stockée). */
    @Transactional
    public IntegrationTestResultDto testConnection(Long companyId, Long id) {
        IntegrationConfig config = requireConfig(companyId, id);
        String apiKey = crypto.decrypt(config.getApiKey());
        IntegrationTestResultDto result = performTest(config.getProvider(), config.getBaseUrl(),
                apiKey, readSettings(config.getSettings()));
        config.setLastTestAt(LocalDateTime.now());
        config.setLastTestOk(result.ok());
        config.setLastTestMessage(result.message());
        config.setUpdatedAt(LocalDateTime.now());
        repository.save(config);
        return result;
    }

    /** Teste une configuration en cours de saisie (avant enregistrement). */
    public IntegrationTestResultDto testConnection(IntegrationConfigRequest request) {
        validate(request);
        String apiKey = request.apiKey();
        IntegrationTestResultDto result = performTest(request.provider(),
                normalizeUrl(request.baseUrl()), apiKey, request.settings() == null ? Map.of() : request.settings());
        return result;
    }

    // ---- Webhook (push fournisseur) ----

    /** Résout la société à partir de la clé de webhook d'une config active. */
    @Transactional(readOnly = true)
    public Optional<Company> companyByWebhookKey(String webhookKey) {
        return repository.findByWebhookKeyAndEnabledTrue(webhookKey)
                .map(IntegrationConfig::getCompany);
    }

    // ---- Implémentation ----

    private IntegrationTestResultDto performTest(IntegrationProvider provider, String baseUrl,
                                                  String apiKey, Map<String, String> settings) {
        long start = System.currentTimeMillis();
        String healthPath = settings.getOrDefault("healthPath", "");
        String url = baseUrl + healthPath;
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(url);
            if (apiKey != null && !apiKey.isBlank()) {
                spec = provider.usesBearerToken()
                        ? spec.header("Authorization", "Bearer " + apiKey)
                        : spec.header("X-API-Key", apiKey);
            }
            HttpStatusCode status = spec.retrieve().toBodilessEntity().getStatusCode();
            long latency = System.currentTimeMillis() - start;
            boolean ok = status.is2xxSuccessful();
            String message = ok
                    ? "Connexion réussie (" + status.value() + ")"
                    : "Réponse inattendue (" + status.value() + ")";
            if (status.value() == 401 || status.value() == 403) {
                message = "Authentification refusée — vérifiez la clé API";
            }
            return new IntegrationTestResultDto(ok, message, status.value(), latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = e.getClass().getSimpleName();
            }
            if (message.length() > 300) {
                message = message.substring(0, 300);
            }
            return new IntegrationTestResultDto(false, "Échec de connexion : " + message, null, latency);
        }
    }

    private List<ProviderFieldDto> fieldsFor(IntegrationProvider provider) {
        List<ProviderFieldDto> fields = new ArrayList<>();
        fields.add(new ProviderFieldDto("healthPath", "Chemin de test (optionnel)",
                "/api/v1/…", false));
        if (provider == IntegrationProvider.DHL) {
            fields.add(new ProviderFieldDto("account", "Compte / numéro client (optionnel)",
                    "ex. 123456789", false));
        }
        return fields;
    }

    private IntegrationConfig requireConfig(Long companyId, Long id) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Intégration introuvable"));
    }

    private Company requireCompanyEntity(Long companyId) {
        // La société est déjà chargée par TenantContext ; on relit pour garantir
        // une entité attachée au contexte de persistance courant.
        return com.fleethub.security.TenantContext.require();
    }

    private void validate(IntegrationConfigRequest request) {
        if (request.provider() == null) {
            throw new IllegalArgumentException("Le fournisseur est obligatoire");
        }
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            throw new IllegalArgumentException("L'URL de base est obligatoire");
        }
        if (!request.baseUrl().startsWith("http://") && !request.baseUrl().startsWith("https://")) {
            throw new IllegalArgumentException("L'URL doit commencer par http:// ou https://");
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String writeSettings(Map<String, String> settings) {
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (Exception e) {
            throw new IllegalArgumentException("Options de fournisseur invalides", e);
        }
    }

    private Map<String, String> readSettings(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            log.warn("Settings d'intégration illisibles, ignorés : {}", e.getMessage());
            return Map.of();
        }
    }

    private IntegrationConfigDto toDto(IntegrationConfig c) {
        String raw = crypto.decrypt(c.getApiKey());
        return new IntegrationConfigDto(
                c.getId(),
                c.getProvider(),
                c.getProvider().getLabel(),
                c.getProvider().getCategory(),
                c.getBaseUrl(),
                c.isEnabled(),
                raw != null && !raw.isBlank(),
                ApiKeyCrypto.mask(raw),
                readSettings(c.getSettings()),
                c.getWebhookKey(),
                c.getLastTestAt(),
                c.getLastTestOk(),
                c.getLastTestMessage(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
