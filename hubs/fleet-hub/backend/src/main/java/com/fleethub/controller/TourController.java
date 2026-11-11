package com.fleethub.controller;

import com.fleethub.dto.tour.ReorderRequest;
import com.fleethub.dto.tour.StopCompletionRequest;
import com.fleethub.dto.tour.TourDto;
import com.fleethub.dto.tour.TourRequest;
import com.fleethub.dto.tour.TourStatsDto;
import com.fleethub.dto.tour.TourStopRequest;
import com.fleethub.service.tour.TourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/tours")
@RequiredArgsConstructor
@Tag(name = "Tournées", description = "Planification, optimisation et exécution des tournées multi-arrêts")
public class TourController {

    /** Garde-fou : une requête ne couvre pas plus d'un an. */
    private static final long MAX_RANGE_DAYS = 366;

    private final TourService tourService;

    @GetMapping
    @Operation(summary = "Lister les tournées", description = "Par défaut : aujourd'hui. Paramètres from/to au format ISO.")
    public List<TourDto> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        checkRange(f, t);
        return tourService.list(f, t);
    }

    @GetMapping("/stats")
    @Operation(summary = "Indicateurs des tournées", description = "Taux de réussite, ponctualité, densité… (30 derniers jours par défaut)")
    public TourStatsDto stats(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t.minusDays(29);
        checkRange(f, t);
        return tourService.stats(f, t);
    }

    @GetMapping(value = "/traceability.csv", produces = "text/csv")
    @Operation(summary = "Export de traçabilité (CSV)",
            description = "Un passage par ligne : horodatages, signataire, échantillons/colis, codes scannés, température")
    public ResponseEntity<byte[]> traceability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = to != null ? to : LocalDate.now();
        LocalDate f = from != null ? from : t;
        checkRange(f, t);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tracabilite_" + f + "_" + t + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(tourService.traceabilityCsv(f, t).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une tournée avec ses arrêts")
    public TourDto get(@PathVariable Long id) {
        return tourService.get(id);
    }

    @PostMapping
    @Operation(summary = "Créer une tournée")
    public TourDto create(@Valid @RequestBody TourRequest req) {
        return tourService.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une tournée (chauffeur, véhicule, dépôt, heure de départ)")
    public TourDto update(@PathVariable Long id, @Valid @RequestBody TourRequest req) {
        return tourService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une tournée")
    public void delete(@PathVariable Long id) {
        tourService.delete(id);
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Dupliquer une tournée à une autre date (tournées récurrentes)")
    public TourDto duplicate(@PathVariable Long id,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return tourService.duplicate(id, date);
    }

    @PostMapping("/{id}/stops")
    @Operation(summary = "Ajouter un arrêt")
    public TourDto addStop(@PathVariable Long id, @Valid @RequestBody TourStopRequest req) {
        return tourService.addStop(id, req);
    }

    @PutMapping("/{id}/stops/{stopId}")
    @Operation(summary = "Modifier un arrêt (créneau, type, durée, plage de température)")
    public TourDto updateStop(@PathVariable Long id, @PathVariable Long stopId, @Valid @RequestBody TourStopRequest req) {
        return tourService.updateStop(id, stopId, req);
    }

    @DeleteMapping("/{id}/stops/{stopId}")
    @Operation(summary = "Retirer un arrêt")
    public TourDto removeStop(@PathVariable Long id, @PathVariable Long stopId) {
        return tourService.removeStop(id, stopId);
    }

    @PutMapping("/{id}/order")
    @Operation(summary = "Imposer l'ordre des arrêts")
    public TourDto reorder(@PathVariable Long id, @Valid @RequestBody ReorderRequest req) {
        return tourService.reorder(id, req);
    }

    @PostMapping("/{id}/optimize")
    @Operation(summary = "Optimiser l'ordre de passage",
            description = "Minimise la distance en respectant au mieux les créneaux ; recalcule les heures d'arrivée")
    public TourDto optimize(@PathVariable Long id) {
        return tourService.optimize(id);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Démarrer la tournée")
    public TourDto start(@PathVariable Long id) {
        return tourService.start(id);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Terminer la tournée")
    public TourDto complete(@PathVariable Long id) {
        return tourService.complete(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler la tournée")
    public TourDto cancel(@PathVariable Long id) {
        return tourService.cancel(id);
    }

    @PostMapping("/{id}/stops/{stopId}/arrive")
    @Operation(summary = "Signaler l'arrivée sur site")
    public TourDto arrive(@PathVariable Long id, @PathVariable Long stopId) {
        return tourService.arrive(id, stopId);
    }

    @PostMapping("/{id}/stops/{stopId}/complete")
    @Operation(summary = "Clôturer un arrêt avec sa preuve de passage")
    public TourDto completeStop(@PathVariable Long id, @PathVariable Long stopId,
                                @Valid @RequestBody StopCompletionRequest req) {
        return tourService.completeStop(id, stopId, req);
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La date de fin doit suivre la date de début");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Période limitée à un an");
        }
    }
}
