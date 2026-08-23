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
public class ErpConfigDTO {

    @NotBlank(message = "Le type d'ERP est obligatoire")
    private String erpType;

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    private String apiEndpoint;

    private String apiKey;

    private String apiSecret;

    private String databaseName;

    private String username;

    private Boolean isActive;

    private String configJson;
}
