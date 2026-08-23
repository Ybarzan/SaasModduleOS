package com.incokalk.dto.shipment;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentStatusUpdateDTO {

    @NotBlank(message = "Le statut est obligatoire")
    private String status;

    private String location;
    private Double latitude;
    private Double longitude;
    private String description;
    private String source;
}
