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
    private double dutyRate;
    private double dutyAmount;
    private String agreement;
    private String note;
}
