package com.incokalk.service.fintech;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.FintechConnection;
import com.incokalk.repository.FintechConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("FintechSyncService — Tests unitaires")
class FintechSyncServiceTest {

    FintechSyncService service;
    FintechConnectionRepository connectionRepository;
    FintechProviderRegistry registry;
    FintechAdapter adapter;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(FintechConnectionRepository.class);
        registry = mock(FintechProviderRegistry.class);
        adapter = mock(FintechAdapter.class);
        service = new FintechSyncService(connectionRepository, registry);
    }

    // ---------- createConnection ----------

    @Test
    @DisplayName("createConnection → succès")
    void createConnection_success() {
        UUID companyId = UUID.randomUUID();
        when(registry.getAdapter("QONTO")).thenReturn(Optional.of(adapter));
        when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FintechConnection result = service.createConnection(companyId, "qonto", "My Qonto", "key", "secret");

        assertThat(result.getCompanyId()).isEqualTo(companyId);
        assertThat(result.getProvider()).isEqualTo(FintechConnection.Provider.QONTO);
        assertThat(result.getName()).isEqualTo("My Qonto");
        assertThat(result.getApiKey()).isEqualTo("key");
        assertThat(result.getApiSecret()).isEqualTo("secret");
        assertThat(result.isActive()).isTrue();
        verify(connectionRepository).save(any());
    }

    @Test
    @DisplayName("createConnection → fournisseur inconnu → IllegalArgumentException")
    void createConnection_unknownProvider() {
        UUID companyId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createConnection(companyId, "unknown_bank", "Name", "key", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fournisseur fintech inconnu");

        verifyNoInteractions(connectionRepository);
    }

    @Test
    @DisplayName("createConnection → aucun adaptateur disponible → IllegalArgumentException")
    void createConnection_noAdapterAvailable() {
        UUID companyId = UUID.randomUUID();
        when(registry.getAdapter("SPENDESK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createConnection(companyId, "spendesk", "Name", "key", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aucun adaptateur disponible");

        verify(connectionRepository, never()).save(any());
    }

    // ---------- listConnections ----------

    @Test
    @DisplayName("listConnections → délègue au repository")
    void listConnections() {
        UUID companyId = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder().id(UUID.randomUUID()).companyId(companyId).build();
        when(connectionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(connection));

        List<FintechConnection> result = service.listConnections(companyId);

        assertThat(result).containsExactly(connection);
    }

    // ---------- updateConnection ----------

    @Test
    @DisplayName("updateConnection → tous les champs mis à jour")
    void updateConnection_allFieldsUpdated() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.QONTO)
                .name("Old").apiKey("oldKey").apiSecret("oldSecret").active(false)
                .build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FintechConnection result = service.updateConnection(id, companyId, "New Name", "newKey", "newSecret", true);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getApiKey()).isEqualTo("newKey");
        assertThat(result.getApiSecret()).isEqualTo("newSecret");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("updateConnection → aucun champ fourni → rien ne change")
    void updateConnection_noFieldsUpdated() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.QONTO)
                .name("Old").apiKey("oldKey").apiSecret("oldSecret").active(true)
                .build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FintechConnection result = service.updateConnection(id, companyId, null, null, null, null);

        assertThat(result.getName()).isEqualTo("Old");
        assertThat(result.getApiKey()).isEqualTo("oldKey");
        assertThat(result.getApiSecret()).isEqualTo("oldSecret");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("updateConnection → champs vides (blank) → rien ne change")
    void updateConnection_blankFieldsIgnored() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.QONTO)
                .name("Old").apiKey("oldKey").apiSecret("oldSecret").active(true)
                .build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FintechConnection result = service.updateConnection(id, companyId, "   ", "  ", "", null);

        assertThat(result.getName()).isEqualTo("Old");
        assertThat(result.getApiKey()).isEqualTo("oldKey");
        assertThat(result.getApiSecret()).isEqualTo("oldSecret");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("updateConnection → connexion introuvable → ResourceNotFoundException")
    void updateConnection_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConnection(id, companyId, "Name", null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- deleteConnection ----------

    @Test
    @DisplayName("deleteConnection → succès")
    void deleteConnection_success() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder().id(id).companyId(companyId).build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));

        service.deleteConnection(id, companyId);

        verify(connectionRepository).delete(connection);
    }

    @Test
    @DisplayName("deleteConnection → connexion introuvable → ResourceNotFoundException")
    void deleteConnection_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConnection(id, companyId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(connectionRepository, never()).delete(any());
    }

    // ---------- testConnection ----------

    @Test
    @DisplayName("testConnection → connecté")
    void testConnection_connected() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.QONTO).build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(registry.getAdapter("QONTO")).thenReturn(Optional.of(adapter));
        when(adapter.testConnection(connection)).thenReturn(true);

        Map<String, Object> result = service.testConnection(id, companyId);

        assertThat(result.get("connected")).isEqualTo(true);
        assertThat(result.get("provider")).isEqualTo("QONTO");
    }

    @Test
    @DisplayName("testConnection → non connecté")
    void testConnection_notConnected() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.SPENDESK).build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(registry.getAdapter("SPENDESK")).thenReturn(Optional.of(adapter));
        when(adapter.testConnection(connection)).thenReturn(false);

        Map<String, Object> result = service.testConnection(id, companyId);

        assertThat(result.get("connected")).isEqualTo(false);
        assertThat(result.get("provider")).isEqualTo("SPENDESK");
    }

    @Test
    @DisplayName("testConnection → connexion introuvable → ResourceNotFoundException")
    void testConnection_connectionNotFound() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testConnection(id, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("testConnection → aucun adaptateur pour le fournisseur → IllegalArgumentException")
    void testConnection_noAdapter() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.QONTO).build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(registry.getAdapter("QONTO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testConnection(id, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aucun adaptateur pour");
    }

    // ---------- syncConnection ----------

    @Test
    @DisplayName("syncConnection → succès avec données")
    void syncConnection_success() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.QONTO).build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(registry.getAdapter("QONTO")).thenReturn(Optional.of(adapter));
        when(adapter.fetchAccounts(connection)).thenReturn(List.of(Map.of("id", "a1"), Map.of("id", "a2")));
        when(adapter.fetchTransactions(connection)).thenReturn(List.of(Map.of("id", "t1")));
        when(adapter.fetchExpenses(connection)).thenReturn(List.of());
        when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = service.syncConnection(id, companyId);

        assertThat(result.get("provider")).isEqualTo("QONTO");
        assertThat(result.get("accounts")).isEqualTo(2);
        assertThat(result.get("transactions")).isEqualTo(1);
        assertThat(result.get("expenses")).isEqualTo(0);
        assertThat(result.get("lastSyncAt")).isNotNull();
        assertThat(connection.getLastSyncAt()).isNotNull();
        verify(connectionRepository).save(connection);
    }

    @Test
    @DisplayName("syncConnection → connexion introuvable → ResourceNotFoundException")
    void syncConnection_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncConnection(id, companyId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(connectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncConnection → aucun adaptateur → IllegalArgumentException")
    void syncConnection_noAdapter() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.SPENDESK).build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(registry.getAdapter("SPENDESK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncConnection(id, companyId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(connectionRepository, never()).save(any());
    }

    // ---------- fetchData ----------

    @Test
    @DisplayName("fetchData → succès")
    void fetchData_success() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.QONTO).build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(registry.getAdapter("QONTO")).thenReturn(Optional.of(adapter));
        List<Map<String, Object>> accounts = List.of(Map.of("id", "a1"));
        List<Map<String, Object>> transactions = List.of(Map.of("id", "t1"));
        List<Map<String, Object>> expenses = List.of(Map.of("id", "e1"));
        when(adapter.fetchAccounts(connection)).thenReturn(accounts);
        when(adapter.fetchTransactions(connection)).thenReturn(transactions);
        when(adapter.fetchExpenses(connection)).thenReturn(expenses);

        Map<String, Object> result = service.fetchData(id, companyId);

        assertThat(result.get("provider")).isEqualTo("QONTO");
        assertThat(result.get("accounts")).isEqualTo(accounts);
        assertThat(result.get("transactions")).isEqualTo(transactions);
        assertThat(result.get("expenses")).isEqualTo(expenses);
    }

    @Test
    @DisplayName("fetchData → connexion introuvable → ResourceNotFoundException")
    void fetchData_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchData(id, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("fetchData → aucun adaptateur → IllegalArgumentException")
    void fetchData_noAdapter() {
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FintechConnection connection = FintechConnection.builder()
                .id(id).companyId(companyId).provider(FintechConnection.Provider.SPENDESK).build();
        when(connectionRepository.findByCompanyIdAndId(companyId, id)).thenReturn(Optional.of(connection));
        when(registry.getAdapter("SPENDESK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchData(id, companyId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
