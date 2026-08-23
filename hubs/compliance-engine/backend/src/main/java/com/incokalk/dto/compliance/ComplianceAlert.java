package com.incokalk.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceAlert {
    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    private Severity severity;
    private String message;
    private String category; // e.g., "INCOTERM", "TRANSPORT", "COUNTRY", "HS_CODE"
}
