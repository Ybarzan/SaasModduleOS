package com.incokalk.dto.config;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderConfigDTO {

    @NotBlank(message = "Le type de fournisseur est obligatoire")
    private String providerType;

    private String apiKey;

    private String apiSecret;

    private Integer priority;

    private Boolean isActive;

    private String configJson;
}
