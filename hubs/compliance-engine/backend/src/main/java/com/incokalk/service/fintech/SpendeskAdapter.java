package com.incokalk.service.fintech;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.model.FintechConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class SpendeskAdapter extends RestFintechAdapter {

    @Value("${incokalk.fintech.spendesk.base-url:https://api.spendesk.com}")
    private String baseUrl;

    @Value("${incokalk.fintech.spendesk.api-key:}")
    private String defaultApiKey;

    public SpendeskAdapter(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate, objectMapper);
    }

    @Override
    public String getProviderType() {
        return "SPENDESK";
    }

    @Override
    protected String getBaseUrl() {
        return baseUrl;
    }

    @Override
    protected String buildAuthHeader(FintechConnection connection) {
        String key = connection.getApiKey() != null && !connection.getApiKey().isBlank()
            ? connection.getApiKey()
            : defaultApiKey;
        return "Bearer " + (key != null ? key : "");
    }

    @Override
    protected String getAccountsPath() {
        return "/v2/accounts";
    }

    @Override
    protected String getTransactionsPath() {
        return "/v2/transactions";
    }

    @Override
    protected String getExpensesPath() {
        return "/v2/expenses";
    }
}
