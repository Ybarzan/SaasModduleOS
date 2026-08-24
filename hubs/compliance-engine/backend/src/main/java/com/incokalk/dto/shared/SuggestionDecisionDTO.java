package com.incokalk.dto.shared;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Corps de requete pour approuver/rejeter une OrchestrationSuggestion. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionDecisionDTO {
    private String note;
}
