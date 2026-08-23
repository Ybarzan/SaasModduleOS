package com.incokalk.dto.shared;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncRequestDTO {

    @NotBlank(message = "Le type de synchronisation est obligatoire")
    private String syncType;

    @NotBlank(message = "La direction est obligatoire")
    private String direction;
}
