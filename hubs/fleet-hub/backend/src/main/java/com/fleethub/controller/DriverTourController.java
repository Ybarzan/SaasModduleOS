package com.fleethub.controller;

import com.fleethub.dto.tour.StopCompletionRequest;
import com.fleethub.dto.tour.TourDto;
import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.service.tour.TourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Application chauffeur (rôle CHAUFFEUR) : le chauffeur ne voit et n'agit que
 * sur les tournées qui lui sont affectées — jamais sur le back-office.
 */
@RestController
@RequestMapping("/api/me/tours")
@RequiredArgsConstructor
@Tag(name = "Application chauffeur", description = "Tournées du chauffeur connecté : consultation, arrivée, preuve de passage")
public class DriverTourController {

    private final TourService tourService;
    private final AppUserRepository appUserRepository;

    @GetMapping
    @Operation(summary = "Mes tournées du jour", description = "Tournées affectées au chauffeur connecté (date ISO facultative, défaut : aujourd'hui)")
    public List<TourDto> myTours(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tourService.forDriver(currentDriverId(), date != null ? date : LocalDate.now());
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Démarrer ma tournée")
    public TourDto start(@PathVariable Long id) {
        tourService.requireAssignedTo(id, currentDriverId());
        return tourService.start(id);
    }

    @PostMapping("/{id}/stops/{stopId}/arrive")
    @Operation(summary = "Je suis arrivé sur le site")
    public TourDto arrive(@PathVariable Long id, @PathVariable Long stopId) {
        tourService.requireAssignedTo(id, currentDriverId());
        return tourService.arrive(id, stopId);
    }

    @PostMapping("/{id}/stops/{stopId}/complete")
    @Operation(summary = "Clôturer un arrêt", description = "Preuve de passage (signataire, colis, échantillons, codes, température) ou motif d'échec")
    public TourDto complete(@PathVariable Long id, @PathVariable Long stopId,
                            @Valid @RequestBody StopCompletionRequest req) {
        tourService.requireAssignedTo(id, currentDriverId());
        return tourService.completeStop(id, stopId, req);
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
