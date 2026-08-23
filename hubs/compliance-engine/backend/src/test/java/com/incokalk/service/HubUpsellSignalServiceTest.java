package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.CustomRole;
import com.incokalk.model.Notification;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("HubUpsellSignalService — Tests unitaires")
class HubUpsellSignalServiceTest {

    CompanyRoleRepository companyRoleRepo;
    NotificationRepository notificationRepo;
    PlanChecker planChecker;
    HubUpsellSignalService service;

    UUID companyId;
    Company company;
    User owner;

    @BeforeEach
    void setUp() {
        companyRoleRepo = mock(CompanyRoleRepository.class);
        notificationRepo = mock(NotificationRepository.class);
        planChecker = mock(PlanChecker.class);
        service = new HubUpsellSignalService(companyRoleRepo, notificationRepo, planChecker);

        companyId = UUID.randomUUID();
        company = Company.builder().id(companyId).name("Atlas Import Export").plan(Company.Plan.STARTER).build();
        owner = User.builder().id(UUID.randomUUID()).email("karim@atlas.test").build();

        CompanyRole ownerRow = new CompanyRole();
        ownerRow.setUser(owner);
        ownerRow.setRole(CompanyRole.Role.OWNER);
        when(companyRoleRepo.findByCompanyIdAndRole(companyId, CompanyRole.Role.OWNER))
            .thenReturn(List.of(ownerRow));
    }

    private CustomRole role(String name, String description) {
        return CustomRole.builder().id(UUID.randomUUID()).company(company).name(name).description(description).build();
    }

    @Test
    @DisplayName("Nom evoquant la douane, plan insuffisant (PRO requis) → notifie l'OWNER")
    void customsKeyword_planInsufficient_notifiesOwner() {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.PRO)).thenReturn(false);

        service.onCustomRoleCreated(role("Douane", "Gere les declarations"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getUser()).isEqualTo(owner);
        assertThat(n.getEventType()).isEqualTo("HUB_UPSELL_SIGNAL");
        assertThat(n.getMessage()).contains("Douane");
        assertThat(n.getStatus()).isEqualTo("UNREAD");
    }

    @Test
    @DisplayName("Nom insensible aux accents/majuscules (Entrepôt) → detecte quand meme")
    void accentInsensitive_stillMatches() {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(false);

        service.onCustomRoleCreated(role("ENTREPÔT", null));

        verify(notificationRepo).save(any(Notification.class));
    }

    @Test
    @DisplayName("Plan deja suffisant → aucune notification")
    void planAlreadySufficient_noNotification() {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.PRO)).thenReturn(true);

        service.onCustomRoleCreated(role("Responsable Douane", null));

        verify(notificationRepo, never()).save(any());
    }

    @Test
    @DisplayName("Nom sans mot-cle connu → aucune notification")
    void noKeywordMatch_noNotification() {
        service.onCustomRoleCreated(role("Support Client", "Gere les tickets"));

        verify(notificationRepo, never()).save(any());
        verifyNoInteractions(planChecker);
    }

    @Test
    @DisplayName("Aucun OWNER trouve → n'explose pas, ne notifie personne")
    void noOwnerFound_doesNotThrow() {
        when(companyRoleRepo.findByCompanyIdAndRole(companyId, CompanyRole.Role.OWNER)).thenReturn(List.of());
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(false);

        assertThatCode(() -> service.onCustomRoleCreated(role("Magasinier Entrepôt", null)))
            .doesNotThrowAnyException();
        verify(notificationRepo, never()).save(any());
    }

    @Test
    @DisplayName("Mot-cle Finance → plan Enterprise requis")
    void financeKeyword_requiresEnterprise() {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(false);

        service.onCustomRoleCreated(role("Trésorerie", "Suivi de la facturation"));

        verify(planChecker).hasMinimumPlan(companyId, Company.Plan.ENTERPRISE);
        verify(notificationRepo).save(any(Notification.class));
    }
}
