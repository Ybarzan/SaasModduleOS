package com.fleethub.controller;

import com.fleethub.dto.PointageRosterDto;
import com.fleethub.dto.PointageStatusDto;
import com.fleethub.dto.PointageSummaryDto;
import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.security.TenantContext;
import com.fleethub.service.PointageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Portail de pointage chauffeur (rôle CHAUFFEUR) : identification par code
 * d'accès société + PIN (voir /api/auth/login), puis début / pause / reprise
 * / fin de service. Chaque action n'agit que sur le chauffeur authentifié.
 */
@RestController
@RequestMapping("/api/pointage")
@RequiredArgsConstructor
@Tag(name = "Pointage", description = "Portail de pointage chauffeur (début/pause/fin de service)")
public class PointageController {

    private final PointageService pointageService;
    private final AppUserRepository appUserRepository;

    @GetMapping("/roster/{accessCode}")
    @Operation(summary = "Liste des chauffeurs", description = "Retourne le nom de la société et ses chauffeurs actifs pour l'écran d'identification")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    @ApiResponse(responseCode = "404", description = "Code d'accès inconnu")
    public PointageRosterDto roster(@PathVariable String accessCode) {
        return pointageService.roster(accessCode);
    }

    @GetMapping("/status")
    @Operation(summary = "Statut courant", description = "Retourne l'état du service en cours pour le chauffeur authentifié")
    @ApiResponse(responseCode = "200", description = "Statut retourné avec succès")
    public PointageStatusDto status() {
        return pointageService.status(TenantContext.companyId(), currentDriverId());
    }

    @PostMapping("/start")
    @Operation(summary = "Démarrer le service", description = "Déclare le début de service du chauffeur authentifié")
    @ApiResponse(responseCode = "200", description = "Service démarré")
    @ApiResponse(responseCode = "409", description = "Un service est déjà en cours")
    public PointageStatusDto start() {
        return pointageService.start(TenantContext.companyId(), currentDriverId());
    }

    @PostMapping("/pause")
    @Operation(summary = "Démarrer une pause", description = "Déclare le début d'une pause pendant le service en cours")
    @ApiResponse(responseCode = "200", description = "Pause démarrée")
    @ApiResponse(responseCode = "409", description = "Aucun service en cours")
    public PointageStatusDto pause() {
        return pointageService.pause(TenantContext.companyId(), currentDriverId());
    }

    @PostMapping("/resume")
    @Operation(summary = "Reprendre le service", description = "Termine la pause en cours et reprend le service")
    @ApiResponse(responseCode = "200", description = "Service repris")
    @ApiResponse(responseCode = "409", description = "Aucune pause en cours")
    public PointageStatusDto resume() {
        return pointageService.resume(TenantContext.companyId(), currentDriverId());
    }

    @PostMapping("/end")
    @Operation(summary = "Terminer le service", description = "Déclare la fin de service et retourne le récapitulatif de la journée")
    @ApiResponse(responseCode = "200", description = "Service terminé")
    @ApiResponse(responseCode = "409", description = "Aucun service en cours")
    public PointageSummaryDto end() {
        return pointageService.end(TenantContext.companyId(), currentDriverId());
    }

    private Long currentDriverId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUser account = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié"));
        if (account.getDriver() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce compte n'est associé à aucun chauffeur");
        }
        return account.getDriver().getId();
    }
}
