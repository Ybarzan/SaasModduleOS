package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.SharedLink;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.SharedLinkRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SharedLinkService — Tests unitaires")
class SharedLinkServiceTest {

    @Mock SharedLinkRepository sharedLinkRepo;
    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock CompanyRepository companyRepo;
    @Mock UserRepository userRepo;

    @InjectMocks SharedLinkService service;

    private UUID companyId, shipmentId, userId, linkId;
    private Company company;
    private User user;
    private ShipmentOrder shipment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();
        userId = UUID.randomUUID();
        linkId = UUID.randomUUID();

        company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        user = User.builder().id(userId).email("user@test.com").fullName("User").company(company).build();
        shipment = ShipmentOrder.builder()
                .id(shipmentId)
                .company(company)
                .user(user)
                .orderNumber("SHP-001")
                .status(ShipmentOrder.Status.DRAFT)
                .build();
    }

    private SharedLink buildLink() {
        return SharedLink.builder()
                .id(linkId)
                .company(company)
                .shipment(shipment)
                .createdBy(user)
                .token("abc-123")
                .label("test")
                .active(true)
                .accessCount(0)
                .build();
    }

    // ── createLink ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Création lien happy path — token généré, expiry définie")
    void createLink_happyPath() {
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(sharedLinkRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        SharedLink result = service.createLink(companyId, shipmentId, userId, "My Label", 24);

        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getLabel()).isEqualTo("My Label");
        assertThat(result.getExpiresAt()).isNotNull().isAfter(LocalDateTime.now());
        assertThat(result.isActive()).isTrue();
        assertThat(result.getAccessCount()).isZero();
        verify(sharedLinkRepo).save(any());
    }

    @Test
    @DisplayName("Création lien sans expiry")
    void createLink_noExpiry() {
        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(sharedLinkRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        SharedLink result = service.createLink(companyId, shipmentId, userId, "No Expiry", null);

        assertThat(result.getExpiresAt()).isNull();
    }

    @Test
    @DisplayName("Création lien avec expédition introuvable → exception")
    void createLink_shipmentNotFound_throws() {
        when(shipmentRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createLink(companyId, shipmentId, userId, "test", 24))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Expédition introuvable");
    }

    // ── listLinks ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Liste des liens par entreprise")
    void listLinks_returnsList() {
        when(sharedLinkRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(buildLink()));

        List<SharedLink> result = service.listLinks(companyId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Liste des liens par expédition — filtrée par entreprise courante")
    void listLinksForShipment_returnsFilteredList() {
        when(sharedLinkRepo.findByShipmentIdAndCompanyIdOrderByCreatedAtDesc(shipmentId, companyId))
                .thenReturn(List.of(buildLink()));

        List<SharedLink> result = service.listLinksForShipment(shipmentId, companyId);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Liste des liens par expédition — n'expose pas les liens d'une autre entreprise")
    void listLinksForShipment_doesNotLeakOtherCompanyLinks() {
        UUID otherCompanyId = UUID.randomUUID();
        when(sharedLinkRepo.findByShipmentIdAndCompanyIdOrderByCreatedAtDesc(shipmentId, otherCompanyId))
                .thenReturn(List.of());

        List<SharedLink> result = service.listLinksForShipment(shipmentId, otherCompanyId);

        assertThat(result).isEmpty();
        verify(sharedLinkRepo, never()).findByShipmentIdAndCompanyIdOrderByCreatedAtDesc(eq(shipmentId), eq(companyId));
    }

    // ── accessLink ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Accès lien happy path — incrément du compteur")
    void accessLink_happyPath() {
        SharedLink link = buildLink();
        when(sharedLinkRepo.findByToken("abc-123")).thenReturn(Optional.of(link));
        when(sharedLinkRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        SharedLink result = service.accessLink("abc-123");

        assertThat(result.getAccessCount()).isEqualTo(1);
        assertThat(result.getLastAccessedAt()).isNotNull();
        verify(sharedLinkRepo).save(link);
    }

    @Test
    @DisplayName("Accès lien révoqué → exception")
    void accessLink_revoked_throws() {
        SharedLink link = buildLink();
        link.setActive(false);
        when(sharedLinkRepo.findByToken("abc-123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.accessLink("abc-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("révoqué");
    }

    @Test
    @DisplayName("Accès lien expiré → exception")
    void accessLink_expired_throws() {
        SharedLink link = buildLink();
        link.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(sharedLinkRepo.findByToken("abc-123")).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.accessLink("abc-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    @DisplayName("Accès lien introuvable → exception")
    void accessLink_notFound_throws() {
        when(sharedLinkRepo.findByToken("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accessLink("unknown"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("introuvable");
    }

    // ── revokeLink ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Révocation lien happy path")
    void revokeLink_happyPath() {
        SharedLink link = buildLink();
        when(sharedLinkRepo.findById(linkId)).thenReturn(Optional.of(link));
        when(sharedLinkRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.revokeLink(linkId, companyId);

        assertThat(link.isActive()).isFalse();
        verify(sharedLinkRepo).save(link);
    }

    @Test
    @DisplayName("Révocation lien mauvaise entreprise → exception")
    void revokeLink_wrongCompany_throws() {
        SharedLink link = buildLink();
        when(sharedLinkRepo.findById(linkId)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.revokeLink(linkId, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("introuvable");
    }

    // ── deleteLink ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Suppression lien happy path")
    void deleteLink_happyPath() {
        SharedLink link = buildLink();
        when(sharedLinkRepo.findById(linkId)).thenReturn(Optional.of(link));

        service.deleteLink(linkId, companyId);

        verify(sharedLinkRepo).delete(link);
    }

    @Test
    @DisplayName("Suppression lien introuvable → exception")
    void deleteLink_notFound_throws() {
        when(sharedLinkRepo.findById(linkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteLink(linkId, companyId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("introuvable");
    }

    // ── linkStats ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Stats retournent totalLinks, activeLinks, totalAccesses")
    void linkStats_returnsStats() {
        SharedLink active = buildLink();
        active.setAccessCount(5);
        SharedLink inactive = SharedLink.builder()
                .id(UUID.randomUUID()).company(company).shipment(shipment)
                .token("xyz-456").active(false).accessCount(2).build();

        when(sharedLinkRepo.countByCompanyId(companyId)).thenReturn(2L);
        when(sharedLinkRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(active, inactive));

        Map<String, Object> stats = service.linkStats(companyId);

        assertThat(stats).containsEntry("totalLinks", 2L);
        assertThat(stats).containsEntry("activeLinks", 1L);
        assertThat(stats).containsEntry("totalAccesses", 7);
    }
}
