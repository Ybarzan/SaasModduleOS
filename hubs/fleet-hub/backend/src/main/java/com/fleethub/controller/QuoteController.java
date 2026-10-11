package com.fleethub.controller;

import com.fleethub.dto.QuoteRequestDTO;
import com.fleethub.dto.QuoteResponseDTO;
import com.fleethub.security.TenantContext;
import com.fleethub.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Demandes de devis transport (multi-fournisseurs) — porté depuis compliance-engine. */
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
@Tag(name = "Quotes", description = "Demandes de devis transport")
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping
    @Operation(summary = "Obtenir des devis transport")
    public ResponseEntity<List<QuoteResponseDTO>> getQuotes(@Valid @RequestBody QuoteRequestDTO request) {
        Long companyId = TenantContext.companyId();
        return ResponseEntity.ok(quoteService.getQuotes(request, companyId));
    }
}
