package com.fleethub.integration.dto;

import java.time.LocalDate;

public record TachographDayDto(
        String licenseNumber,
        LocalDate date,
        double drivingHours,
        double workHours,
        double restMinutes,
        boolean compliant,
        /** Provenance : "FILE_CSV", "FILE_DDD_UNVALIDATED", "API_<fournisseur>"...
         *  Null si le fournisseur n'a pas encore été mis à jour pour la renseigner. */
        String source
) {
}
