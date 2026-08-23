package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.ApiKey;
import com.incokalk.model.Company;
import com.incokalk.model.User;
import com.incokalk.repository.ApiKeyRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ApiKeyService — Tests unitaires")
class ApiKeyServiceTest {

    @Mock ApiKeyRepository keyRepo;
    @Mock UserRepository userRepo;
    @Mock CompanyRepository companyRepo;
    @Mock BCryptPasswordEncoder encoder;
    @InjectMocks ApiKeyService service;

    UUID userId;
    UUID companyId;
    UUID keyId;
    User user;
    Company company;
    ApiKey apiKey;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        keyId = UUID.randomUUID();
        user = User.builder().id(userId).email("test@test.com").plan(User.Plan.PRO).build();
        company = Company.builder().id(companyId).name("Acme").build();
        apiKey = ApiKey.builder()
                .id(keyId).user(user).company(company).keyHash("hash").keyPrefix("ic_live_abc")
                .name("Test Key").plan(User.Plan.PRO).dailyLimit(500)
                .active(true).build();
    }

    @Test
    @DisplayName("create → génère une clé")
    void create() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(encoder.encode(anyString())).thenReturn("hash");
        when(keyRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.create(userId, companyId, "Ma clé", User.Plan.PRO);
        assertThat(result.rawKey()).startsWith("ic_live_");
        assertThat(result.prefix()).startsWith("ic_live_");
        assertThat(result.plan()).isEqualTo("PRO");
        assertThat(result.dailyLimit()).isEqualTo(500);
    }

    @Test
    @DisplayName("create → utilisateur introuvable")
    void create_userNotFound() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(userId, companyId, "Key", User.Plan.FREE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create → société introuvable")
    void create_companyNotFound() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(userId, companyId, "Key", User.Plan.FREE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("validate → clé valide")
    void validate_valid() {
        apiKey.setCallsToday(10);
        when(keyRepo.findByKeyPrefix("ic_live_abcdefgh")).thenReturn(List.of(apiKey));
        when(encoder.matches("ic_live_abcdefgh", "hash")).thenReturn(true);

        var result = service.validate("ic_live_abcdefgh");
        assertThat(result).isPresent();
        assertThat(result.get().plan()).isEqualTo("PRO");
        assertThat(result.get().quotaExceeded()).isFalse();
    }

    @Test
    @DisplayName("validate → clé invalide → empty")
    void validate_invalid() {
        when(keyRepo.findByKeyPrefix("ic_live_badkey")).thenReturn(List.of());
        assertThat(service.validate("ic_live_badkey")).isNotPresent();
    }

    @Test
    @DisplayName("validate → null → empty")
    void validate_null() {
        assertThat(service.validate(null)).isNotPresent();
    }

    @Test
    @DisplayName("validate → quota dépassé → quotaExceeded=true")
    void validate_quotaExceeded() {
        apiKey.setCallsToday(500);
        apiKey.setDailyLimit(500);
        when(keyRepo.findByKeyPrefix("ic_live_abcdefgh")).thenReturn(List.of(apiKey));
        when(encoder.matches(anyString(), eq("hash"))).thenReturn(true);

        var result = service.validate("ic_live_abcdefgh");
        assertThat(result).isPresent();
        assertThat(result.get().quotaExceeded()).isTrue();
    }

    @Test
    @DisplayName("resetDailyQuotas → appelle le repo")
    void resetDailyQuotas() {
        service.resetDailyQuotas();
        verify(keyRepo).resetAllDailyQuotas();
    }

    @Test
    @DisplayName("revoke → OK")
    void revoke() {
        when(keyRepo.revokeKey(keyId, companyId)).thenReturn(1);
        service.revoke(keyId, companyId);
        verify(keyRepo).revokeKey(keyId, companyId);
    }

    @Test
    @DisplayName("revoke → pas trouvé → exception")
    void revoke_notFound() {
        when(keyRepo.revokeKey(keyId, companyId)).thenReturn(0);
        assertThatThrownBy(() -> service.revoke(keyId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("revoke → clé d'une autre société → non trouvée")
    void revoke_wrongCompany() {
        UUID otherCompanyId = UUID.randomUUID();
        when(keyRepo.revokeKey(keyId, otherCompanyId)).thenReturn(0);
        assertThatThrownBy(() -> service.revoke(keyId, otherCompanyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listForCompany → liste")
    void listForCompany() {
        when(keyRepo.findByCompanyIdAndActiveTrue(companyId)).thenReturn(List.of(apiKey));
        assertThat(service.listForCompany(companyId)).hasSize(1);
    }
}
