package com.incokalk.service;

import com.incokalk.model.ClientUser;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.TrackingEvent;
import com.incokalk.repository.ClientUserRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ClientAuthService — Tests unitaires")
class ClientAuthServiceTest {

    @Mock ClientUserRepository clientUserRepo;
    @Mock CompanyRepository companyRepo;
    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock JwtService jwtService;
    @Mock BCryptPasswordEncoder passwordEncoder;

    @InjectMocks ClientAuthService service;

    private UUID companyId, clientId;
    private Company company;
    private ClientUser client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        clientId = UUID.randomUUID();

        company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        client = ClientUser.builder()
                .id(clientId)
                .company(company)
                .email("client@test.com")
                .password("encodedPwd")
                .fullName("Client Test")
                .phone("0601020304")
                .active(true)
                .build();
    }

    // ── login ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Connexion client réussie")
    void login_success() {
        when(clientUserRepo.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(passwordEncoder.matches("secret", "encodedPwd")).thenReturn(true);
        when(jwtService.generateClientToken(clientId, "client@test.com", companyId)).thenReturn("client-jwt");

        var result = service.login("client@test.com", "secret");

        assertThat(result.token()).isEqualTo("client-jwt");
        assertThat(result.clientId()).isEqualTo(clientId);
        assertThat(result.email()).isEqualTo("client@test.com");
        assertThat(result.fullName()).isEqualTo("Client Test");
        assertThat(result.companyId()).isEqualTo(companyId);
        verify(clientUserRepo).save(argThat(c -> c.getId().equals(clientId)));
    }

    @Test
    @DisplayName("Connexion avec mauvais mot de passe → exception")
    void login_wrongPassword_throws() {
        when(clientUserRepo.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(passwordEncoder.matches("wrong", "encodedPwd")).thenReturn(false);

        assertThatThrownBy(() -> service.login("client@test.com", "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email ou mot de passe incorrect");
    }

    @Test
    @DisplayName("Connexion avec email inconnu → exception")
    void login_unknownEmail_throws() {
        when(clientUserRepo.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("unknown@test.com", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email ou mot de passe incorrect");
    }

    @Test
    @DisplayName("Connexion avec compte désactivé → exception")
    void login_inactiveAccount_throws() {
        client.setActive(false);
        when(clientUserRepo.findByEmail("client@test.com")).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.login("client@test.com", "secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("désactivé");
    }

    // ── getProfile ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Profil client retourné correctement")
    void getProfile_success() {
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.of(client));

        var profile = service.getProfile(clientId);

        assertThat(profile.get("id")).isEqualTo(clientId);
        assertThat(profile.get("email")).isEqualTo("client@test.com");
        assertThat(profile.get("fullName")).isEqualTo("Client Test");
        assertThat(profile.get("phone")).isEqualTo("0601020304");
        assertThat(profile.get("companyId")).isEqualTo(companyId);
        assertThat(profile.get("companyName")).isEqualTo("TestCo");
    }

    @Test
    @DisplayName("Profil client introuvable → exception")
    void getProfile_notFound_throws() {
        when(clientUserRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Client introuvable");
    }

    // ── createClient ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Création client réussie")
    void createClient_success() {
        when(clientUserRepo.findByEmailAndCompanyId("new@test.com", companyId)).thenReturn(Optional.empty());
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(passwordEncoder.encode("pass123")).thenReturn("encodedPass");
        when(clientUserRepo.save(any(ClientUser.class))).thenAnswer(i -> {
            ClientUser c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        ClientUser result = service.createClient(companyId, "new@test.com", "pass123", "New Client", "0600000000");

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getFullName()).isEqualTo("New Client");
        assertThat(result.getPhone()).isEqualTo("0600000000");
        assertThat(result.getCompany()).isEqualTo(company);
        assertThat(result.isActive()).isTrue();
        verify(passwordEncoder).encode("pass123");
    }

    @Test
    @DisplayName("Création client avec email dupliqué pour même entreprise → exception")
    void createClient_duplicateEmail_throws() {
        when(clientUserRepo.findByEmailAndCompanyId("dup@test.com", companyId)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.createClient(companyId, "dup@test.com", "pass", "Dup", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("existe déjà");
    }

    @Test
    @DisplayName("Création client avec entreprise introuvable → exception")
    void createClient_companyNotFound_throws() {
        when(clientUserRepo.findByEmailAndCompanyId("x@test.com", companyId)).thenReturn(Optional.empty());
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createClient(companyId, "x@test.com", "pass", "X", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Entreprise introuvable");
    }

    // ── updateClient ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Mise à jour client partielle réussie")
    void updateClient_success() {
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(clientUserRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ClientUser result = service.updateClient(clientId, companyId, "Updated Name", "0699999999", false);

        assertThat(result.getFullName()).isEqualTo("Updated Name");
        assertThat(result.getPhone()).isEqualTo("0699999999");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("Mise à jour partielle avec null ne modifie pas les champs")
    void updateClient_partialUpdate_preservesFields() {
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(clientUserRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ClientUser result = service.updateClient(clientId, companyId, null, null, null);

        assertThat(result.getFullName()).isEqualTo("Client Test");
        assertThat(result.getPhone()).isEqualTo("0601020304");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("Mise à jour client introuvable → exception")
    void updateClient_notFound_throws() {
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateClient(clientId, companyId, "Nom", null, null))
                .isInstanceOf(RuntimeException.class);
    }

    // ── resetClientPassword ──────────────────────────────────────────────

    @Test
    @DisplayName("Réinitialisation mot de passe client réussie")
    void resetClientPassword_success() {
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.of(client));
        when(passwordEncoder.encode("newPass")).thenReturn("newEncoded");

        service.resetClientPassword(clientId, companyId, "newPass");

        assertThat(client.getPassword()).isEqualTo("newEncoded");
        verify(clientUserRepo).save(client);
    }

    @Test
    @DisplayName("Réinitialisation mot de passe client introuvable → exception")
    void resetClientPassword_notFound_throws() {
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetClientPassword(clientId, companyId, "pass"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Réinitialisation mot de passe d'un client d'une autre entreprise → exception")
    void resetClientPassword_wrongCompany_throws() {
        UUID otherCompanyId = UUID.randomUUID();
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.resetClientPassword(clientId, otherCompanyId, "pass"))
                .isInstanceOf(RuntimeException.class);
    }

    // ── deleteClient ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Suppression client réussie")
    void deleteClient_success() {
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.of(client));

        service.deleteClient(clientId, companyId);

        verify(clientUserRepo).delete(client);
    }

    @Test
    @DisplayName("Suppression client introuvable → exception")
    void deleteClient_notFound_throws() {
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteClient(clientId, companyId))
                .isInstanceOf(RuntimeException.class);

        verify(clientUserRepo, never()).delete(any());
    }

    @Test
    @DisplayName("Suppression client d'une autre entreprise → exception")
    void deleteClient_wrongCompany_throws() {
        UUID otherCompanyId = UUID.randomUUID();
        when(clientUserRepo.findById(clientId)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.deleteClient(clientId, otherCompanyId))
                .isInstanceOf(RuntimeException.class);

        verify(clientUserRepo, never()).delete(any());
    }

    // ── listClients ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Liste clients filtrée par entreprise")
    void listClients_filteredByCompany() {
        ClientUser c2 = ClientUser.builder().id(UUID.randomUUID()).company(company).email("c2@test.com").build();
        when(clientUserRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(client, c2));

        List<ClientUser> result = service.listClients(companyId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClientUser::getEmail).containsExactly("client@test.com", "c2@test.com");
    }

    // ── getMyShipments / getShipmentDetail (cloisonnement par client) ─────

    @Test
    @DisplayName("getMyShipments → scopé par companyId ET clientId, pas seulement l'entreprise")
    void getMyShipments_scopedByClient() {
        ShipmentOrder mine = ShipmentOrder.builder().id(UUID.randomUUID()).build();
        when(shipmentRepo.findByCompanyIdAndClientIdOrderByCreatedAtDesc(companyId, clientId))
                .thenReturn(List.of(mine));

        var result = service.getMyShipments(companyId, clientId);

        assertThat(result).containsExactly(mine);
        verify(shipmentRepo, never()).findByCompanyIdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("getShipmentDetail → introuvable pour un autre client de la même entreprise")
    void getShipmentDetail_deniesOtherClientSameCompany() {
        UUID shipmentId = UUID.randomUUID();
        UUID otherClientId = UUID.randomUUID();
        when(shipmentRepo.findByIdAndCompanyIdAndClientId(shipmentId, companyId, otherClientId))
                .thenReturn(Optional.empty());

        var result = service.getShipmentDetail(companyId, otherClientId, shipmentId);

        assertThat(result).isEmpty();
    }

    // ── clientStats ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Statistiques clients")
    void clientStats_returnsCorrectCounts() {
        when(clientUserRepo.countByCompanyId(companyId)).thenReturn(10L);
        when(clientUserRepo.countByCompanyIdAndActive(companyId, true)).thenReturn(7L);

        var stats = service.clientStats(companyId);

        assertThat(stats.get("totalClients")).isEqualTo(10L);
        assertThat(stats.get("activeClients")).isEqualTo(7L);
    }
}
