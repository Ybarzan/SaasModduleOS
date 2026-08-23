package com.incokalk.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferentialDutyResult {
    private boolean preferential;
    private String agreementCode;
    private String agreementName;
    private double mfnDutyRate;
    private double preferentialDutyRate;
    private double savings;
    private String originCriterion;
    private String originExplanation;
    private double valueAddedPct;
    private boolean originating;
}