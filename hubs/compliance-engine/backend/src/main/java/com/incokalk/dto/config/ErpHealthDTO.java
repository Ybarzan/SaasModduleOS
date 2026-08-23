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
public class ErpHealthDTO {

    private UUID id;
    private String erpType;
    private String name;
    private String syncStatus;
    private LocalDateTime lastSyncAt;
    private String lastError;
    private Boolean isActive;
}
