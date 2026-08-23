package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.HsCodeSuggestion;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.HsCodeSuggestionService;
import com.incokalk.service.ml.HsMlService;
import com.incokalk.service.ocr.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/hs-suggestions")
@RequiredArgsConstructor
@Tag(name = "HS Code Suggestions", description = "Classification HS par IA (TARIC + ML + OCR)")
@RequiresPlan(Company.Plan.STARTER)
public class HsCodeSuggestionController {

    private final HsCodeSuggestionService hsCodeSuggestionService;
    private final OcrService ocrService;
    private final HsMlService hsMlService;

    @PostMapping("/suggest")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Suggerer des codes HS pour un produit")
    public ResponseEntity<?> suggest(@Valid @RequestBody SuggestRequest req) {
        try {
            HsCodeSuggestion result = hsCodeSuggestionService.suggest(req.productDescription());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/suggest-from-image")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Classifier un produit depuis une image ou un PDF (OCR)")
    public ResponseEntity<?> suggestFromImage(@RequestParam("file") MultipartFile file) {
        try {
            String description = ocrService.extractText(file)
                .orElse(null);
            if (description == null || description.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "message", "Impossible d extraire du texte du fichier. Veuillez saisir la description manuellement."
                ));
            }
            HsCodeSuggestion result = hsCodeSuggestionService.suggest(description);
            return ResponseEntity.ok(Map.of(
                "result", result,
                "extractedText", description
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/ml/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Statistiques du modele ML de classification HS")
    public ResponseEntity<Map<String, Object>> mlStats() {
        return ResponseEntity.ok(hsMlService.getStats());
    }

    @GetMapping("/history")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Historique des suggestions HS")
    public ResponseEntity<List<HsCodeSuggestion>> history() {
        return ResponseEntity.ok(hsCodeSuggestionService.getHistory());
    }

    @PutMapping("/{id}/confirm")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Confirmer le code HS selectionne (apprentissage ML)")
    public ResponseEntity<?> confirmSelection(@PathVariable UUID id, @Valid @RequestBody ConfirmRequest req) {
        try {
            HsCodeSuggestion updated = hsCodeSuggestionService.confirmSelection(id, req.selectedCode());
            hsMlService.recordCorrection(updated.getProductDescription(), req.selectedCode());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    record SuggestRequest(@NotNull String productDescription) {}

    record ConfirmRequest(@NotNull String selectedCode) {}
}
