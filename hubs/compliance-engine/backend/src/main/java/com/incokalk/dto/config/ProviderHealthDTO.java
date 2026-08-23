package com.incokalk.dto.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderHealthDTO {

    private String providerType;
    private String healthStatus;
    private LocalDateTime lastHealthCheck;
    private Integer consecutiveFailures;
    private boolean isActive;
}
