package com.fleethub.integration;

import com.fleethub.integration.dto.FuelTransactionDto;

import java.time.LocalDate;
import java.util.List;

public interface CostSource {

    List<FuelTransactionDto> fetchTransactions(LocalDate since);
}
