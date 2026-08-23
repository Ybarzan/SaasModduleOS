package com.incokalk.service.fintech;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.model.FintechConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class QontoAdapter extends RestFintechAdapter {

    @Value("${incokalk.fintech.qonto.base-url:https://thirdparty.qonto.com}")
    private String baseUrl;

    @Value("${incokalk.fintech.qonto.api-key:}")
    private String defaultApiKey;

    public QontoAdapter(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate, objectMapper);
    }

    @Override
    public String getProviderType() {
        return "QONTO";
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
        return key != null ? key : "";
    }

    @Override
    protected String getAccountsPath() {
        return "/v2/organizations/bank_accounts";
    }

    @Override
    protected String getTransactionsPath() {
        return "/v2/transactions";
    }

    @Override
    protected String getExpensesPath() {
        return "/v2/organizations/attachments?status=to_process";
    }
}
