package com.fleethub.controller;

import com.fleethub.dto.LegalContent;
import com.fleethub.service.LegalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mentions légales publiques (sans authentification, cf. SecurityConfig :
 * {@code /api/legal/**} est permitAll).
 */
@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
@Tag(name = "Mentions légales", description = "Contenus légaux publics (CGU, RGPD, mentions légales)")
public class LegalController {

    private final LegalService legalService;

    @GetMapping("/{key}")
    @Operation(summary = "Récupérer un contenu légal", description = "Retourne un document légal par sa clé (cgu, rgpd, legal-notices)")
    public LegalContent get(@PathVariable String key) {
        return legalService.get(key);
    }
}
