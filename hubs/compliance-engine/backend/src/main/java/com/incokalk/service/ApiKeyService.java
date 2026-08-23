package com.incokalk.service;

import com.incokalk.exception.QuotaExceededException;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.ApiKey;
import com.incokalk.model.Company;
import com.incokalk.model.User;
import com.incokalk.repository.ApiKeyRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository keyRepo;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final BCryptPasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public static final Map<User.Plan, Integer> PLAN_LIMITS = Map.of(
        User.Plan.FREE, 10,
        User.Plan.STARTER, 100,
        User.Plan.PRO, 500,
        User.Plan.API_STARTER, 2000,
        User.Plan.API_PRO, 10000,
        User.Plan.ENTERPRISE, Integer.MAX_VALUE
    );

    @Transactional
    public CreatedApiKey create(UUID userId, UUID companyId, String name, User.Plan plan) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Société introuvable"));

        String rawKey = generateKey();
        String prefix = extractPrefix(rawKey);
        int limit = PLAN_LIMITS.getOrDefault(plan, 10);

        ApiKey key = ApiKey.builder()
            .user(user)
            .company(company)
            .keyHash(encoder.encode(rawKey))
            .keyPrefix(prefix)
            .name(name)
            .plan(plan)
            .dailyLimit(limit)
            .build();
        keyRepo.save(key);
        log.info("API Key créée : user={} company={} plan={} prefix={}", userId, companyId, plan, prefix);

        return new CreatedApiKey(key.getId(), rawKey, prefix, plan.name(), limit);
    }

    public Optional<ValidatedKey> validate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith("ic_")) return Optional.empty();
        String prefix = extractPrefix(rawKey);

        List<ApiKey> candidates = keyRepo.findByKeyPrefix(prefix);
        for (ApiKey k : candidates) {
            if (!k.isActive()) continue;
            if (k.getExpiresAt() != null && k.getExpiresAt().isBefore(LocalDateTime.now())) continue;
            if (encoder.matches(rawKey, k.getKeyHash())) {
                boolean exceeded = k.getDailyLimit() != Integer.MAX_VALUE
                    && k.getCallsToday() >= k.getDailyLimit();

                if (!exceeded) keyRepo.incrementCalls(k.getId());

                return Optional.of(new ValidatedKey(
                    k.getId(), k.getUser().getId(),
                    k.getPlan().name(), k.getDailyLimit(),
                    k.getCallsToday() + 1, exceeded
                ));
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void resetDailyQuotas() {
        keyRepo.resetAllDailyQuotas();
        log.info("Quotas journaliers réinitialisés");
    }

    @Transactional
    public void revoke(UUID keyId, UUID companyId) {
        int updated = keyRepo.revokeKey(keyId, companyId);
        if (updated == 0) throw new ResourceNotFoundException("Clé introuvable ou non autorisée");
    }

    public List<ApiKey> listForCompany(UUID companyId) {
        return keyRepo.findByCompanyIdAndActiveTrue(companyId);
    }

    private String generateKey() {
        byte[] b = new byte[18];
        random.nextBytes(b);
        return "ic_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String extractPrefix(String key) {
        String[] parts = key.split("_", 3);
        if (parts.length < 3) return key.substring(0, Math.min(10, key.length()));
        return "ic_live_" + parts[2].substring(0, Math.min(8, parts[2].length()));
    }

    public record CreatedApiKey(UUID id, String rawKey, String prefix, String plan, int dailyLimit) {}
    public record ValidatedKey(UUID keyId, UUID userId, String plan, int dailyLimit, int callsToday, boolean quotaExceeded) {}
}
