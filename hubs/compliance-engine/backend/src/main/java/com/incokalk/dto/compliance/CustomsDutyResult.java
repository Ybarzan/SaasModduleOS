package com.incokalk.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomsDutyResult {
    private String hsCode;
    private String originCountry;
    private String destinationCountry;
    private double cifValue;
    /** null si aucun taux fiable (rateAvailable=false) — jamais un taux inventé. */
    private Double dutyRate;
    /** null si aucun taux fiable (rateAvailable=false). En `currency`. */
    private Double dutyAmount;
    private String agreement;
    private String note;
    private boolean rateAvailable;
    /** Devise de dutyAmount. cifValue et les montants saisis sont en EUR. */
    private String currency;
    /** Base de calcul du droit (CIF_EU), null si indisponible. */
    private String basisType;
    /** Montant en `currency` pour 1 EUR (1.0 si EUR). */
    private Double fxRate;
}
