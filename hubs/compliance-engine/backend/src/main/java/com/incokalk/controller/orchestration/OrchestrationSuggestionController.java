package com.incokalk.controller.orchestration;

import com.incokalk.dto.shared.SuggestionDecisionDTO;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.OrchestrationSuggestion;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.OrchestrationSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Decision humaine sur les propositions d'action creees par le moteur de
 * regles (NotificationRule.actionType -> OrchestrationSuggestion, V65). Ne
 * declenche jamais d'action reelle -- seulement la transition PENDING_APPROVAL
 * -> APPROVED/REJECTED. Voir docs/04-composants-techniques.md.
 */
@RestController
@RequestMapping("/v1/orchestration-suggestions")
@RequiredArgsConstructor
@RequiresPlan(Company.Plan.PRO)
@Tag(name = "Orchestration Suggestions", description = "Propositions d'action du moteur de règles, à valider ou rejeter")
public class OrchestrationSuggestionController {

    private final OrchestrationSuggestionService suggestionService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les suggestions d'action")
    public ResponseEntity<?> listSuggestions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) OrchestrationSuggestion.Status status) {
        if (status != null) {
            return ResponseEntity.ok(suggestionService.listByStatus(status));
        }
        if (page != null && size != null && size > 0) {
            Page<OrchestrationSuggestion> result = suggestionService.listSuggestions(PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(suggestionService.listSuggestions());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Détail d'une suggestion d'action")
    public ResponseEntity<OrchestrationSuggestion> getSuggestion(@PathVariable UUID id) {
        return ResponseEntity.ok(suggestionService.getSuggestion(id));
    }

    @PostMapping("/{id}/approve")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Approuver une suggestion d'action (ne l'exécute pas — aucun exécuteur n'existe encore)")
    public ResponseEntity<OrchestrationSuggestion> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) SuggestionDecisionDTO dto,
            HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        String note = dto != null ? dto.getNote() : null;
        return ResponseEntity.ok(suggestionService.approve(id, userId, note));
    }

    @PostMapping("/{id}/reject")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Rejeter une suggestion d'action")
    public ResponseEntity<OrchestrationSuggestion> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) SuggestionDecisionDTO dto,
            HttpServletRequest httpReq) {
        UUID userId = (UUID) httpReq.getAttribute("userId");
        String note = dto != null ? dto.getNote() : null;
        return ResponseEntity.ok(suggestionService.reject(id, userId, note));
    }
}
