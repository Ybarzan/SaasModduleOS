package com.fleethub.dto.tour;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record SiteRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "DEPOT|CLIENT|PHARMACIE|LABORATOIRE|ETABLISSEMENT_SANTE|AUTRE",
                message = "Type de site inconnu") String kind,
        @Size(max = 255) String reference,
        @Size(max = 255) String address,
        @Size(max = 255) String postalCode,
        @Size(max = 255) String city,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 255) String contactName,
        @Size(max = 255) String contactPhone,
        LocalTime openingFrom,
        LocalTime openingTo,
        @Min(0) @Max(480) Integer serviceMinutes,
        @Size(max = 1000) String notes,
        Boolean active
) {}
