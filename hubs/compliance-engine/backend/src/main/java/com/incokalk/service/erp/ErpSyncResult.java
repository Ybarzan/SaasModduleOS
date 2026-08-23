package com.incokalk.service.erp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpSyncResult {

    private boolean success;
    private int recordsTotal;
    private int recordsSynced;
    private int recordsFailed;
    private String errorMessage;
    @Builder.Default
    private List<String> warnings = List.of();
}
