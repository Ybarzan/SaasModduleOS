package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.DeniedPartyCheck;
import com.incokalk.model.SanctionedEntity;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.DeniedPartyCheckRepository;
import com.incokalk.repository.SanctionedEntityRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DeniedPartyScreeningService — Tests screening algorithm + stats + CRUD")
class DeniedPartyScreeningServiceTest {

    @Mock DeniedPartyCheckRepository deniedPartyCheckRepo;
    @Mock SanctionedEntityRepository sanctionedEntityRepo;
    @Mock CompanyRepository companyRepo;
    @Mock NotificationService notificationService;
    @InjectMocks DeniedPartyScreeningService service;

    private UUID companyId;
    private UUID userId;
    private Company company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        TenantContext.set(companyId);
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── screen() — CLEAR ────────────────────────────────────────────────

    @Test
    @DisplayName("screen — no match returns CLEAR")
    void screenClear() {
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("Acme Corp"))
                .thenReturn(Collections.emptyList());
        when(sanctionedEntityRepo.findByCountryCodeAndIsActiveTrue("FR"))
                .thenReturn(Collections.emptyList());

        DeniedPartyCheck result = service.screen("Acme Corp", "FR",
                DeniedPartyCheck.CheckType.ENTITY, userId);

        assertThat(result.getResult()).isEqualTo(DeniedPartyCheck.CheckResult.CLEAR);
        assertThat(result.getRiskLevel()).isEqualTo(DeniedPartyCheck.RiskLevel.LOW);
        assertThat(result.getCheckedByUserId()).isEqualTo(userId);
        assertThat(result.getCheckType()).isEqualTo(DeniedPartyCheck.CheckType.ENTITY);
        verify(deniedPartyCheckRepo).save(any());
    }

    // ── screen() — BLOCKED (exact match) ────────────────────────────────

    @Test
    @DisplayName("screen — exact name match returns BLOCKED")
    void screenBlockedExactMatch() {
        SanctionedEntity entity = SanctionedEntity.builder()
                .id(UUID.randomUUID())
                .name("ALPHA Corp")
                .listSource("EU")
                .entryId("EU-001")
                .entityType(SanctionedEntity.EntityType.ENTITY)
                .countryCode("RU")
                .reason("Sanctions")
                .program("EU sanctions")
                .isActive(true)
                .build();
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("ALPHA Corp"))
                .thenReturn(List.of(entity));

        DeniedPartyCheck result = service.screen("ALPHA Corp", null,
                DeniedPartyCheck.CheckType.ENTITY, userId);

        assertThat(result.getResult()).isEqualTo(DeniedPartyCheck.CheckResult.BLOCKED);
        assertThat(result.getRiskLevel()).isEqualTo(DeniedPartyCheck.RiskLevel.CRITICAL);
        assertThat(result.getMatchedListName()).isEqualTo("EU");
        assertThat(result.getMatchedEntryId()).isEqualTo("EU-001");
        assertThat(result.getMatchedEntryDetails()).contains("EU-001");
    }

    // ── screen() — BLOCKED (alias match) ────────────────────────────────

    @Test
    @DisplayName("screen — alias match returns BLOCKED")
    void screenBlockedAliasMatch() {
        SanctionedEntity entity = SanctionedEntity.builder()
                .id(UUID.randomUUID())
                .name("BETA Inc")
                .aliases("BETA LLC, BETA International")
                .listSource("OFAC")
                .entryId("OFAC-042")
                .entityType(SanctionedEntity.EntityType.ENTITY)
                .isActive(true)
                .build();
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("BETA LLC"))
                .thenReturn(List.of(entity));

        DeniedPartyCheck result = service.screen("BETA LLC", null,
                DeniedPartyCheck.CheckType.PERSON, userId);

        assertThat(result.getResult()).isEqualTo(DeniedPartyCheck.CheckResult.BLOCKED);
        assertThat(result.getRiskLevel()).isEqualTo(DeniedPartyCheck.RiskLevel.CRITICAL);
        assertThat(result.getCheckType()).isEqualTo(DeniedPartyCheck.CheckType.PERSON);
    }

    // ── screen() — MATCH (fuzzy) ────────────────────────────────────────

    @Test
    @DisplayName("screen — fuzzy partial match returns MATCH")
    void screenFuzzyMatch() {
        SanctionedEntity entity = SanctionedEntity.builder()
                .id(UUID.randomUUID())
                .name("GAMMA Industries Ltd")
                .listSource("UN")
                .entryId("UN-100")
                .entityType(SanctionedEntity.EntityType.ENTITY)
                .isActive(true)
                .build();
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("GAMMA"))
                .thenReturn(List.of(entity));

        DeniedPartyCheck result = service.screen("GAMMA", null,
                DeniedPartyCheck.CheckType.ENTITY, userId);

        assertThat(result.getResult()).isEqualTo(DeniedPartyCheck.CheckResult.MATCH);
        assertThat(result.getRiskLevel()).isEqualTo(DeniedPartyCheck.RiskLevel.HIGH);
        assertThat(result.getMatchedEntryId()).isEqualTo("UN-100");
    }

    // ── screen() — POSSIBLE_MATCH (country) ─────────────────────────────

    @Test
    @DisplayName("screen — sanctioned country returns POSSIBLE_MATCH")
    void screenCountryMatch() {
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("SomeCo"))
                .thenReturn(Collections.emptyList());
        SanctionedEntity countryEntity = SanctionedEntity.builder()
                .id(UUID.randomUUID())
                .name("Country Entity")
                .countryCode("KP")
                .listSource("UN")
                .entryId("UN-200")
                .isActive(true)
                .build();
        when(sanctionedEntityRepo.findByCountryCodeAndIsActiveTrue("KP"))
                .thenReturn(List.of(countryEntity));

        DeniedPartyCheck result = service.screen("SomeCo", "KP",
                DeniedPartyCheck.CheckType.COUNTRY, userId);

        assertThat(result.getResult()).isEqualTo(DeniedPartyCheck.CheckResult.POSSIBLE_MATCH);
        assertThat(result.getRiskLevel()).isEqualTo(DeniedPartyCheck.RiskLevel.MEDIUM);
        assertThat(result.getMatchedEntryDetails()).contains("KP");
    }

    // ── screen() — name match takes priority over country match ──────────

    @Test
    @DisplayName("screen — name match takes priority over country match")
    void screenNamePriorityOverCountry() {
        SanctionedEntity nameEntity = SanctionedEntity.builder()
                .id(UUID.randomUUID())
                .name("DELTA Corp")
                .listSource("EU")
                .entryId("EU-999")
                .entityType(SanctionedEntity.EntityType.ENTITY)
                .isActive(true)
                .build();
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("DELTA Corp"))
                .thenReturn(List.of(nameEntity));

        DeniedPartyCheck result = service.screen("DELTA Corp", "KP",
                DeniedPartyCheck.CheckType.ENTITY, userId);

        assertThat(result.getResult()).isEqualTo(DeniedPartyCheck.CheckResult.BLOCKED);
        assertThat(result.getRiskLevel()).isEqualTo(DeniedPartyCheck.RiskLevel.CRITICAL);
    }

    // ── screen() — userId fallback to companyId ──────────────────────────

    @Test
    @DisplayName("screen — null userId falls back to companyId")
    void screenNullUserIdFallback() {
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("Test"))
                .thenReturn(Collections.emptyList());

        DeniedPartyCheck result = service.screen("Test", null,
                DeniedPartyCheck.CheckType.ENTITY, null);

        assertThat(result.getCheckedByUserId()).isEqualTo(companyId);
    }

    // ── screen() — company not found throws ─────────────────────────────

    @Test
    @DisplayName("screen — company not found throws exception")
    void screenCompanyNotFound() {
        UUID unknownId = UUID.randomUUID();
        TenantContext.set(unknownId);
        when(companyRepo.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.screen("Test", null,
                DeniedPartyCheck.CheckType.ENTITY, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Company not found");
    }

    // ── getHistory() ────────────────────────────────────────────────────

    @Test
    @DisplayName("getHistory — returns company checks ordered")
    void getHistory() {
        DeniedPartyCheck check = DeniedPartyCheck.builder()
                .id(UUID.randomUUID())
                .company(company)
                .checkedName("Test")
                .build();
        when(deniedPartyCheckRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(check));

        List<DeniedPartyCheck> result = service.getHistory();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCheckedName()).isEqualTo("Test");
    }

    // ── getById() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getById — returns check when found")
    void getByIdFound() {
        UUID checkId = UUID.randomUUID();
        DeniedPartyCheck check = DeniedPartyCheck.builder()
                .id(checkId)
                .company(company)
                .checkedName("Found")
                .build();
        when(deniedPartyCheckRepo.findByCompanyIdAndId(companyId, checkId))
                .thenReturn(Optional.of(check));

        DeniedPartyCheck result = service.getById(checkId);

        assertThat(result.getCheckedName()).isEqualTo("Found");
    }

    @Test
    @DisplayName("getById — throws when not found")
    void getByIdNotFound() {
        UUID checkId = UUID.randomUUID();
        when(deniedPartyCheckRepo.findByCompanyIdAndId(companyId, checkId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(checkId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Check not found");
    }

    // ── getStats() — UPPERCASE keys ─────────────────────────────────────

    @Test
    @DisplayName("getStats — returns UPPERCASE keys matching frontend")
    void getStatsUppercaseKeys() {
        when(deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.CLEAR)).thenReturn(5L);
        when(deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.MATCH)).thenReturn(3L);
        when(deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.POSSIBLE_MATCH)).thenReturn(1L);
        when(deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.BLOCKED)).thenReturn(2L);

        Map<String, Object> stats = service.getStats();

        assertThat(stats).containsKey("total");
        assertThat((long) stats.get("total")).isEqualTo(11L);
        assertThat(stats).containsEntry("CLEAR", 5L);
        assertThat(stats).containsEntry("MATCH", 3L);
        assertThat(stats).containsEntry("POSSIBLE_MATCH", 1L);
        assertThat(stats).containsEntry("BLOCKED", 2L);
    }

    // ── getSanctionedEntities() ─────────────────────────────────────────

    @Test
    @DisplayName("getSanctionedEntities — delegates to repository")
    void getSanctionedEntities() {
        SanctionedEntity e1 = SanctionedEntity.builder().name("Entity1").isActive(true).build();
        when(sanctionedEntityRepo.findByIsActiveTrue()).thenReturn(List.of(e1));

        List<SanctionedEntity> result = service.getSanctionedEntities();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Entity1");
    }

    // ── screen() — notification sent on CRITICAL ────────────────────────

    @Test
    @DisplayName("screen — BLOCKED sends alert notification")
    void screenBlockedSendsNotification() {
        SanctionedEntity entity = SanctionedEntity.builder()
                .id(UUID.randomUUID())
                .name("Sanctioned Corp")
                .listSource("EU")
                .entryId("EU-100")
                .entityType(SanctionedEntity.EntityType.ENTITY)
                .isActive(true)
                .build();
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("Sanctioned Corp"))
                .thenReturn(List.of(entity));

        service.screen("Sanctioned Corp", null, DeniedPartyCheck.CheckType.ENTITY, userId);

        verify(notificationService).onDpsAlert(
                any(), eq("Sanctioned Corp"), eq("CRITICAL"),
                eq("EU"), any(), eq(companyId));
    }

    // ── screen() — notification sent on HIGH ────────────────────────────

    @Test
    @DisplayName("screen — MATCH sends alert notification (HIGH)")
    void screenMatchSendsNotification() {
        SanctionedEntity entity = SanctionedEntity.builder()
                .id(UUID.randomUUID())
                .name("Suspicious Corp International")
                .listSource("OFAC")
                .entryId("OFAC-200")
                .entityType(SanctionedEntity.EntityType.ENTITY)
                .isActive(true)
                .build();
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("Suspicious Corp"))
                .thenReturn(List.of(entity));

        service.screen("Suspicious Corp", null, DeniedPartyCheck.CheckType.ENTITY, userId);

        verify(notificationService).onDpsAlert(
                any(), eq("Suspicious Corp"), eq("HIGH"),
                eq("OFAC"), any(), eq(companyId));
    }

    // ── screen() — no notification on CLEAR ─────────────────────────────

    @Test
    @DisplayName("screen — CLEAR does not send notification")
    void screenClearNoNotification() {
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("Safe Corp"))
                .thenReturn(Collections.emptyList());

        service.screen("Safe Corp", null, DeniedPartyCheck.CheckType.ENTITY, userId);

        verifyNoInteractions(notificationService);
    }

    // ── screen() — notification failure does not break screen ───────────

    @Test
    @DisplayName("screen — notification failure does not break screen")
    void screenNotificationFailureDoesNotBreak() {
        SanctionedEntity entity = SanctionedEntity.builder()
                .id(UUID.randomUUID())
                .name("Fail Corp")
                .listSource("UN")
                .entryId("UN-300")
                .entityType(SanctionedEntity.EntityType.ENTITY)
                .isActive(true)
                .build();
        when(sanctionedEntityRepo.findByNameContainingIgnoreCaseAndIsActiveTrue("Fail Corp"))
                .thenReturn(List.of(entity));
        doThrow(new RuntimeException("Mail server down"))
                .when(notificationService).onDpsAlert(
                        any(), any(), any(), any(), any(), any());

        DeniedPartyCheck result = service.screen("Fail Corp", null,
                DeniedPartyCheck.CheckType.ENTITY, userId);

        assertThat(result.getResult()).isEqualTo(DeniedPartyCheck.CheckResult.BLOCKED);
        verify(deniedPartyCheckRepo).save(any());
    }

    // ── getAlerts() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getAlerts — returns only HIGH and CRITICAL checks")
    void getAlerts() {
        DeniedPartyCheck critical = DeniedPartyCheck.builder()
                .id(UUID.randomUUID())
                .company(company)
                .checkedName("Blocked")
                .riskLevel(DeniedPartyCheck.RiskLevel.CRITICAL)
                .build();
        DeniedPartyCheck high = DeniedPartyCheck.builder()
                .id(UUID.randomUUID())
                .company(company)
                .checkedName("Matched")
                .riskLevel(DeniedPartyCheck.RiskLevel.HIGH)
                .build();
        DeniedPartyCheck low = DeniedPartyCheck.builder()
                .id(UUID.randomUUID())
                .company(company)
                .checkedName("Safe")
                .riskLevel(DeniedPartyCheck.RiskLevel.LOW)
                .build();
        when(deniedPartyCheckRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(critical, high, low));

        List<DeniedPartyCheck> alerts = service.getAlerts();

        assertThat(alerts).hasSize(2);
        assertThat(alerts).extracting("checkedName").containsExactly("Blocked", "Matched");
    }

    @Test
    @DisplayName("getAlerts — empty when no HIGH/CRITICAL")
    void getAlertsEmpty() {
        DeniedPartyCheck low = DeniedPartyCheck.builder()
                .id(UUID.randomUUID())
                .company(company)
                .checkedName("Safe")
                .riskLevel(DeniedPartyCheck.RiskLevel.LOW)
                .build();
        when(deniedPartyCheckRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(low));

        List<DeniedPartyCheck> alerts = service.getAlerts();

        assertThat(alerts).isEmpty();
    }

}
