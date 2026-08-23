package com.incokalk.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpSyncLogDTO {

    private UUID id;
    private UUID erpConfigId;
    private String erpTypeName;
    private String syncType;
    private String direction;
    private String status;
    private Integer recordsTotal;
    private Integer recordsSynced;
    private Integer recordsFailed;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
