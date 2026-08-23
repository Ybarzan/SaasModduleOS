package com.incokalk.service.fintech;

import com.incokalk.model.FintechConnection;

import java.util.List;
import java.util.Map;

public interface FintechAdapter {

    String getProviderType();

    boolean testConnection(FintechConnection connection);

    List<Map<String, Object>> fetchAccounts(FintechConnection connection);

    List<Map<String, Object>> fetchTransactions(FintechConnection connection);

    List<Map<String, Object>> fetchExpenses(FintechConnection connection);
}
