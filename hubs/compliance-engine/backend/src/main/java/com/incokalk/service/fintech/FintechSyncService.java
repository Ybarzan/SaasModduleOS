package com.incokalk.service.fintech;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.FintechConnection;
import com.incokalk.repository.FintechConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FintechSyncService {

    private final FintechConnectionRepository connectionRepository;
    private final FintechProviderRegistry registry;

    @Transactional
    public FintechConnection createConnection(UUID companyId, String provider, String name,
                                              String apiKey, String apiSecret) {
        FintechConnection.Provider parsedProvider;
        try {
            parsedProvider = FintechConnection.Provider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Fournisseur fintech inconnu: " + provider);
        }
        if (registry.getAdapter(parsedProvider.name()).isEmpty()) {
            throw new IllegalArgumentException("Aucun adaptateur disponible pour " + parsedProvider);
        }

        FintechConnection connection = FintechConnection.builder()
            .companyId(companyId)
            .provider(parsedProvider)
            .name(name)
            .apiKey(apiKey)
            .apiSecret(apiSecret)
            .active(true)
            .build();
        return connectionRepository.save(connection);
    }

    public List<FintechConnection> listConnections(UUID companyId) {
        return connectionRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional
    public FintechConnection updateConnection(UUID id, UUID companyId, String name,
                                              String apiKey, String apiSecret, Boolean active) {
        FintechConnection connection = getConnection(id, companyId);
        if (name != null && !name.isBlank()) {
            connection.setName(name);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            connection.setApiKey(apiKey);
        }
        if (apiSecret != null && !apiSecret.isBlank()) {
            connection.setApiSecret(apiSecret);
        }
        if (active != null) {
            connection.setActive(active);
        }
        return connectionRepository.save(connection);
    }

    @Transactional
    public void deleteConnection(UUID id, UUID companyId) {
        connectionRepository.delete(getConnection(id, companyId));
    }

    public Map<String, Object> testConnection(UUID id, UUID companyId) {
        FintechConnection connection = getConnection(id, companyId);
        boolean connected = getAdapter(connection).testConnection(connection);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", connected);
        result.put("provider", connection.getProvider().name());
        return result;
    }

    @Transactional
    public Map<String, Object> syncConnection(UUID id, UUID companyId) {
        FintechConnection connection = getConnection(id, companyId);
        FintechAdapter adapter = getAdapter(connection);

        int accounts = adapter.fetchAccounts(connection).size();
        int transactions = adapter.fetchTransactions(connection).size();
        int expenses = adapter.fetchExpenses(connection).size();

        connection.setLastSyncAt(LocalDateTime.now());
        connectionRepository.save(connection);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", connection.getProvider().name());
        result.put("accounts", accounts);
        result.put("transactions", transactions);
        result.put("expenses", expenses);
        result.put("lastSyncAt", connection.getLastSyncAt());
        return result;
    }

    public Map<String, Object> fetchData(UUID id, UUID companyId) {
        FintechConnection connection = getConnection(id, companyId);
        FintechAdapter adapter = getAdapter(connection);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", connection.getProvider().name());
        result.put("accounts", adapter.fetchAccounts(connection));
        result.put("transactions", adapter.fetchTransactions(connection));
        result.put("expenses", adapter.fetchExpenses(connection));
        return result;
    }

    private FintechConnection getConnection(UUID id, UUID companyId) {
        return connectionRepository.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Connexion fintech introuvable"));
    }

    private FintechAdapter getAdapter(FintechConnection connection) {
        return registry.getAdapter(connection.getProvider().name())
            .orElseThrow(() -> new IllegalArgumentException("Aucun adaptateur pour " + connection.getProvider()));
    }
}
