package com.incokalk.service.fintech;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.model.FintechConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class RestFintechAdapter implements FintechAdapter {

    protected final RestTemplate restTemplate;
    protected final ObjectMapper objectMapper;

    protected RestFintechAdapter(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    protected abstract String getBaseUrl();

    protected abstract String buildAuthHeader(FintechConnection connection);

    protected abstract String getAccountsPath();

    protected abstract String getTransactionsPath();

    protected abstract String getExpensesPath();

    @Override
    public boolean testConnection(FintechConnection connection) {
        List<Map<String, Object>> accounts = fetchAccounts(connection);
        return !accounts.isEmpty();
    }

    @Override
    public List<Map<String, Object>> fetchAccounts(FintechConnection connection) {
        return getJsonList(connection, getAccountsPath());
    }

    @Override
    public List<Map<String, Object>> fetchTransactions(FintechConnection connection) {
        return getJsonList(connection, getTransactionsPath());
    }

    @Override
    public List<Map<String, Object>> fetchExpenses(FintechConnection connection) {
        return getJsonList(connection, getExpensesPath());
    }

    protected List<Map<String, Object>> getJsonList(FintechConnection connection, String path) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, buildAuthHeader(connection));
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = trimTrailingSlash(getBaseUrl()) + path;
            log.info("[{}] GET {}", getProviderType(), url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseList(response.getBody());
            }
            log.warn("[{}] Réponse inattendue: {}", getProviderType(), response.getStatusCode());
        } catch (Exception e) {
            log.warn("[{}] Erreur GET {}: {}", getProviderType(), path, e.getMessage());
        }
        return List.of();
    }

    protected List<Map<String, Object>> parseList(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode node : data) {
                result.add(objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {}));
            }
            return result;
        } catch (Exception e) {
            log.warn("[{}] Erreur parsing liste: {}", getProviderType(), e.getMessage());
            return List.of();
        }
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
