package com.fleethub.controller;

import com.fleethub.dto.medical.CustodyEventDto;
import com.fleethub.dto.medical.MedicalDashboardDto;
import com.fleethub.dto.medical.MedicalSettingsDto;
import com.fleethub.dto.medical.NonConformityDto;
import com.fleethub.dto.medical.NonConformityRequest;
import com.fleethub.dto.medical.NonConformityUpdateRequest;
import com.fleethub.dto.medical.TourTraceabilityDto;
import com.fleethub.service.medical.CustodyLedger;
import com.fleethub.service.medical.MedicalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** Espace Transport médical : traçabilité, non-conformités, indicateurs. */
@RestController
@RequestMapping("/api/medical")
@RequiredArgsConstructor
@Tag(name = "Transport médical", description = "Journal de garde, rapprochement collecté / remis, non-conformités")
public class MedicalController {

    private static final long MAX_RANGE_DAYS = 366;

    private final MedicalService medicalService;

    @GetMapping("/dashboard")
    @Operation(summary = "Indicateurs du transport médical sur une période (défaut : 30 derniers jours)")
    public MedicalDashboardDto dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        checkRange(start, end);
        return medicalService.dashboard(start, end);
    }

    @GetMapping("/custody")
    @Operation(summary = "Journal de garde (plus récents d'abord)")
    public List<CustodyEventDto> journal(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkRange(from, to);
        return medicalService.journal(from, to);
    }

    @GetMapping("/custody/verify")
    @Operation(summary = "Vérifier l'intégrité du journal de garde",
            description = "Recalcule la chaîne d'empreintes SHA-256 : toute modification ou suppression est détectée")
    public CustodyLedger.Verification verify() {
        return medicalService.verify();
    }

    @GetMapping(value = "/custody.csv", produces = "text/csv")
    @Operation(summary = "Exporter le journal de garde (CSV, avec empreintes)")
    public ResponseEntity<byte[]> journalCsv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        checkRange(from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"journal-garde_" + from + "_" + to + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(medicalService.journalCsv(from, to).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/tours/{id}/traceability")
    @Operation(summary = "Dossier de traçabilité d'une tournée", description = "Rapprochement collecté / remis, délais, journal, non-conformités")
    public TourTraceabilityDto tourTraceability(@PathVariable Long id) {
        return medicalService.tourTraceability(id);
    }

    @GetMapping("/non-conformities")
    @Operation(summary = "Non-conformités", description = "status = OUVERTES (non clôturées, toutes dates), OUVERTE, EN_TRAITEMENT ou CLOTUREE")
    public List<NonConformityDto> nonConformities(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(89);
        checkRange(start, end);
        return medicalService.nonConformities(status, start, end);
    }

    @PostMapping("/non-conformities")
    @Operation(summary = "Déclarer une non-conformité")
    public NonConformityDto declare(@Valid @RequestBody NonConformityRequest req) {
        return medicalService.declare(req);
    }

    @PutMapping("/non-conformities/{id}")
    @Operation(summary = "Traiter ou clôturer une non-conformité", description = "La clôture exige une action corrective")
    public NonConformityDto update(@PathVariable Long id, @Valid @RequestBody NonConformityUpdateRequest req) {
        return medicalService.update(id, req);
    }

    @GetMapping("/settings")
    @Operation(summary = "Réglages du transport médical")
    public MedicalSettingsDto settings() {
        return medicalService.settings();
    }

    @PutMapping("/settings")
    @Operation(summary = "Modifier les réglages du transport médical (ADMIN)")
    public MedicalSettingsDto updateSettings(@Valid @RequestBody MedicalSettingsDto req) {
        return medicalService.updateSettings(req);
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
