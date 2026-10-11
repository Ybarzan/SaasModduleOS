package com.fleethub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** État de santé d'un fournisseur de devis transport (DHL, Shippo, interne, …). */
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
