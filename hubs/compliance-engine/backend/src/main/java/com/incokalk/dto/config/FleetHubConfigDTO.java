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
public class FleetHubConfigDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    @NotBlank(message = "L'URL de base est obligatoire")
    private String baseUrl;

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    private String username;

    private String password;

    private Boolean isActive;
}
