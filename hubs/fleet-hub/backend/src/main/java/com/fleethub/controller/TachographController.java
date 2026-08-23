package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.TachographDayDto;
import com.fleethub.dto.TachographDayRequest;
import com.fleethub.dto.TachographSummaryDto;
import com.fleethub.model.Driver;
import com.fleethub.model.TachographDay;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.TachographDayRepository;
import com.fleethub.security.TenantContext;
import com.fleethub.service.TachographService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tachograph-days")
@RequiredArgsConstructor
@Tag(name = "Tachygraphe", description = "Jours de tachygraphe et conformité des chauffeurs")
public class TachographController {

    private final TachographDayRepository tachographRepository;
    private final DriverRepository driverRepository;
    private final TachographService tachographService;

    @GetMapping
    @Operation(summary = "Lister les jours tachygraphe", description = "Retourne les données journalières avec filtres optionnels")
    public List<TachographDayDto> all(@RequestParam(required = false) Long driverId,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        LocalDate fromDate = from != null ? parseDate(from) : null;
        LocalDate toDate = to != null ? parseDate(to) : null;
        List<TachographDay> all = tachographRepository.findAllFetch(TenantContext.companyId());
        Map<Long, List<TachographDay>> byDriver = all.stream()
                .collect(Collectors.groupingBy(d -> d.getDriver().getId()));
        return all.stream()
                .filter(d -> driverId == null || d.getDriver().getId().equals(driverId))
                .filter(d -> fromDate == null || !d.getDate().isBefore(fromDate))
                .filter(d -> toDate == null || !d.getDate().isAfter(toDate))
                .map(d -> toDto(d, assess(d, byDriver.getOrDefault(d.getDriver().getId(), List.of()))))
                .toList();
    }

    /** Synthèse par chauffeur : conformité sur la période + cumuls glissants. */
    @GetMapping("/summary")
    @Operation(summary = "Synthèse par chauffeur", description = "Taux de conformité et cumuls glissants sur une période")
    public List<TachographSummaryDto> summary(@RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        LocalDate today = LocalDate.now();
        LocalDate fromDate = from != null ? parseDate(from) : today.minusDays(29);
        LocalDate toDate = to != null ? parseDate(to) : today;

        Map<Long, List<TachographDay>> byDriver = tachographRepository.findAllFetch(TenantContext.companyId())
                .stream()
                .collect(Collectors.groupingBy(d -> d.getDriver().getId()));

        return byDriver.entrySet().stream()
                .map(e -> summarize(e.getKey(), e.getValue(), fromDate, toDate))
                .sorted(Comparator.comparing(TachographSummaryDto::driverName))
                .toList();
    }

    @PostMapping
    @Operation(summary = "Créer un jour tachygraphe", description = "Enregistre les données d'une journée de conduite")
    @Transactional
    public TachographDayDto create(@Valid @RequestBody TachographDayRequest req) {
        Driver driver = driverRepository.findByIdAndCompanyId(req.driverId(), TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        TachographDay d = new TachographDay();
        d.setCompany(TenantContext.require());
        apply(d, req, driver);
        TachographService.Assessment assessment = assessWithHistory(d, driver);
        d.setCompliant(assessment.compliant());
        return toDto(tachographRepository.save(d), assessment);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un jour tachygraphe", description = "Met à jour les données d'une journée de conduite")
    @Transactional
    public TachographDayDto update(@PathVariable Long id, @Valid @RequestBody TachographDayRequest req) {
        TachographDay d = tachographRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Jour de tachygraphe introuvable"));
        Driver driver = driverRepository.findByIdAndCompanyId(req.driverId(), TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        apply(d, req, driver);
        TachographService.Assessment assessment = assessWithHistory(d, driver);
        d.setCompliant(assessment.compliant());
        return toDto(tachographRepository.save(d), assessment);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un jour tachygraphe", description = "Supprime les données d'une journée de conduite")
    @Transactional
    public void delete(@PathVariable Long id) {
        TachographDay d = tachographRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Jour de tachygraphe introuvable"));
        tachographRepository.delete(d);
    }

    private TachographSummaryDto summarize(Long driverId, List<TachographDay> driverDays,
                                           LocalDate from, LocalDate to) {
        List<TachographDay> periodDays = driverDays.stream()
                .filter(d -> !d.getDate().isBefore(from) && !d.getDate().isAfter(to))
                .toList();
        int compliantDays = 0;
        LocalDate lastNonCompliant = null;
        for (TachographDay d : periodDays) {
            if (assess(d, driverDays).compliant()) {
                compliantDays++;
            } else {
                lastNonCompliant = d.getDate();
            }
        }
        int total = periodDays.size();
        double rate = total > 0 ? round(compliantDays * 100.0 / total) : 0;
        double week = sumBetween(driverDays, to.minusDays(6), to);
        double fortnight = sumBetween(driverDays, to.minusDays(13), to);
        TachographDay first = periodDays.isEmpty() ? null : periodDays.get(0);
        return new TachographSummaryDto(
                driverId,
                first != null
                        ? first.getDriver().getFirstName() + " " + first.getDriver().getLastName()
                        : "Chauffeur " + driverId,
                total, compliantDays, total - compliantDays, round(rate),
                round(week), round(fortnight), lastNonCompliant);
    }

    private double sumBetween(List<TachographDay> days, LocalDate from, LocalDate to) {
        return days.stream()
                .filter(d -> !d.getDate().isBefore(from) && !d.getDate().isAfter(to))
                .mapToDouble(TachographDay::getDrivingHours)
                .sum();
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private TachographService.Assessment assess(TachographDay d, List<TachographDay> driverDays) {
        return tachographService.assess(d, driverDays);
    }

    /** Évalue la conformité d'une journée en tenant compte de son historique de 14 jours. */
    private TachographService.Assessment assessWithHistory(TachographDay day, Driver driver) {
        List<TachographDay> history = new ArrayList<>(tachographRepository
                .findByDriverAndDateBetween(driver, day.getDate().minusDays(13), day.getDate()));
        boolean present = history.stream()
                .anyMatch(x -> x.getId() != null && x.getId().equals(day.getId()));
        if (!present) history.add(day);
        return tachographService.assess(day, history);
    }

    private LocalDate parseDate(String value) {
        return value.length() > 10 ? LocalDateTime.parse(value).toLocalDate() : LocalDate.parse(value);
    }

    private void apply(TachographDay d, TachographDayRequest req, Driver driver) {
        d.setDriver(driver);
        d.setDate(req.date());
        d.setDrivingHours(req.drivingHours());
        d.setWorkHours(req.workHours() != null ? req.workHours() : 0);
        d.setRestMinutes(req.restMinutes() != null ? req.restMinutes() : 0);
    }

    private TachographDayDto toDto(TachographDay d, TachographService.Assessment assessment) {
        return new TachographDayDto(
                d.getId(), d.getDriver().getId(),
                d.getDriver().getFirstName() + " " + d.getDriver().getLastName(),
                d.getDate(), d.getDrivingHours(), d.getWorkHours(), d.getRestMinutes(),
                assessment.compliant(), assessment.reasons());
    }
}
