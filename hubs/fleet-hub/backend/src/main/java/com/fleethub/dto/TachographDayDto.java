package com.fleethub.dto;

import java.time.LocalDate;
import java.util.List;

public record TachographDayDto(
        Long id,
        Long driverId,
        String driverName,
        LocalDate date,
        double drivingHours,
        double workHours,
        double restMinutes,
        boolean compliant,
        List<String> reasons,
        /** Provenance ("FILE_DDD_UNVALIDATED", "FILE_CSV", "SEED"...) ou null
         *  pour une saisie manuelle. Voir com.fleethub.model.TachographDay#dataSource. */
        String dataSource
) {}
