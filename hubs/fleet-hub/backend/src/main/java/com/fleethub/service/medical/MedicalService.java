package com.fleethub.service.medical;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.medical.CustodyEventDto;
import com.fleethub.dto.medical.MedicalDashboardDto;
import com.fleethub.dto.medical.MedicalSettingsDto;
import com.fleethub.dto.medical.NonConformityDto;
import com.fleethub.dto.medical.NonConformityRequest;
import com.fleethub.dto.medical.NonConformityUpdateRequest;
import com.fleethub.dto.medical.TourTraceabilityDto;
import com.fleethub.model.Company;
import com.fleethub.model.CustodyEvent;
import com.fleethub.model.MedicalProduct;
import com.fleethub.model.NonConformity;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.CustodyEventRepository;
import com.fleethub.repository.NonConformityRepository;
import com.fleethub.repository.TourRepository;
import com.fleethub.security.TenantContext;
import com.fleethub.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Espace « Transport médical » : journal de garde, dossiers de traçabilité,
 * non-conformités, indicateurs et réglages. Réservé aux sociétés dont
 * l'activité est le transport médical.
 */
@Service
@RequiredArgsConstructor
public class MedicalService {

    private static final int MAX_EVENTS = 2000;
    private static final DateTimeFormatter CSV_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CompanyRepository companyRepository;
    private final TourRepository tourRepository;
    private final CustodyEventRepository custodyRepository;
    private final NonConformityRepository ncRepository;
    private final CustodyLedger ledger;
    private final MedicalTraceability traceability;
    private final AuditService auditService;

    // ------------------------------------------------------- journal de garde

    @Transactional(readOnly = true)
    public List<CustodyEventDto> journal(LocalDate from, LocalDate to) {
        Company company = requireMedical();
        return custodyRepository.findInPeriod(company.getId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                PageRequest.of(0, MAX_EVENTS)).stream().map(CustodyEventDto::of).toList();
    }

    @Transactional(readOnly = true)
    public CustodyLedger.Verification verify() {
        return ledger.verify(requireMedical().getId());
    }

    /** Export du journal (ordre chronologique), avec les empreintes pour contrôle externe. */
    @Transactional(readOnly = true)
    public String journalCsv(LocalDate from, LocalDate to) {
        Company company = requireMedical();
        List<CustodyEvent> events = new ArrayList<>(custodyRepository.findInPeriod(company.getId(),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), PageRequest.of(0, 100_000)));
        java.util.Collections.reverse(events);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("sequence;evenement;horodatage_terrain;horodatage_serveur;acteur;tournee;site;type_site;")
                .append("produits;quantite_totale;temperature_c;remis_a_signe_par;latitude;longitude;details;empreinte_precedente;empreinte\n");
        for (CustodyEvent e : events) {
            String[] row = {
                    String.valueOf(e.getSequenceNo()), e.getType().name(), e.getOccurredAt().format(CSV_TS),
                    e.getRecordedAt().format(CSV_TS), cell(e.getActor()), cell(e.getTourName()), cell(e.getSiteName()),
                    opt(e.getSiteKind()), opt(e.getProducts()), opt(e.getTotalQuantity()), opt(e.getTemperatureCelsius()),
                    cell(e.getSignedBy()), opt(e.getLatitude()), opt(e.getLongitude()), cell(e.getDetails()),
                    e.getPreviousHash(), e.getHash()
            };
            csv.append(String.join(";", row)).append('\n');
        }
        return csv.toString();
    }

    /** Dossier de traçabilité d'une tournée : rapprochement collecté / remis, journal, non-conformités. */
    @Transactional(readOnly = true)
    public TourTraceabilityDto tourTraceability(Long tourId) {
        Company company = requireMedical();
        Tour tour = loadTour(company, tourId);
        return new TourTraceabilityDto(tour.getId(), tour.getName(), Reconciliation.of(tour),
                custodyRepository.findByCompanyIdAndTourIdOrderBySequenceNoAsc(company.getId(), tourId).stream()
                        .map(CustodyEventDto::of).toList(),
                ncRepository.findByCompanyIdAndTourIdOrderByDetectedAtAsc(company.getId(), tourId).stream()
                        .map(NonConformityDto::of).toList());
    }

    // ------------------------------------------------------ non-conformités

    @Transactional(readOnly = true)
    public List<NonConformityDto> nonConformities(String status, LocalDate from, LocalDate to) {
        Company company = requireMedical();
        List<NonConformity> list = "OUVERTES".equals(status)
                ? ncRepository.findByCompanyIdAndStatusNotOrderByDetectedAtDesc(company.getId(), NonConformity.Status.CLOTUREE)
                : ncRepository.findInPeriod(company.getId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        if (status != null && !"OUVERTES".equals(status)) {
            NonConformity.Status wanted = NonConformity.Status.valueOf(status);
            list = list.stream().filter(n -> n.getStatus() == wanted).toList();
        }
        return list.stream().map(NonConformityDto::of).toList();
    }

    @Transactional
    public NonConformityDto declare(NonConformityRequest req) {
        Company company = requireMedical();
        Tour tour = req.tourId() != null ? loadTour(company, req.tourId()) : null;
        TourStop stop = null;
        if (req.tourStopId() != null) {
            if (tour == null) {
                throw new IllegalArgumentException("Précisez la tournée de l'arrêt concerné");
            }
            stop = tour.getStops().stream().filter(s -> s.getId().equals(req.tourStopId())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Arrêt introuvable"));
        }
        return NonConformityDto.of(traceability.open(company.getId(), tour, stop,
                NonConformity.Type.valueOf(req.type()), NonConformity.Severity.valueOf(req.severity()),
                req.description().trim(), false));
    }

    @Transactional
    public NonConformityDto update(Long id, NonConformityUpdateRequest req) {
        Company company = requireMedical();
        NonConformity nc = ncRepository.findByIdAndCompanyId(id, company.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Non-conformité introuvable"));
        if (nc.getStatus() == NonConformity.Status.CLOTUREE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Non-conformité déjà clôturée");
        }
        NonConformity.Status target = NonConformity.Status.valueOf(req.status());
        String action = req.correctiveAction() != null ? req.correctiveAction().trim() : null;
        if (action != null && !action.isEmpty()) {
            nc.setCorrectiveAction(action);
        }
        if (target == NonConformity.Status.CLOTUREE) {
            if (nc.getCorrectiveAction() == null || nc.getCorrectiveAction().length() < 5) {
                throw new IllegalArgumentException("Décrivez l'action corrective pour clôturer la non-conformité");
            }
            nc.setClosedAt(LocalDateTime.now());
            nc.setClosedBy(ledger.currentActor());
            Tour tour = nc.getTourId() != null ? tourRepository.findByIdAndCompanyId(nc.getTourId(), company.getId()).orElse(null) : null;
            ledger.append(company.getId(), new CustodyLedger.Entry(CustodyEvent.Type.NC_CLOTUREE, tour, null, null,
                    null, null, null, null, null, "NC #" + nc.getId() + " clôturée — action corrective : " + nc.getCorrectiveAction()));
        }
        nc.setStatus(target);
        return NonConformityDto.of(ncRepository.save(nc));
    }

    // ------------------------------------------------------------ indicateurs

    @Transactional(readOnly = true)
    public MedicalDashboardDto dashboard(LocalDate from, LocalDate to) {
        Company company = requireMedical();
        List<Tour> tours = tourRepository.findByCompanyIdAndDateBetweenOrderByDateAscPlannedStartAsc(company.getId(), from, to)
                .stream().filter(t -> t.getStatus() != Tour.TourStatus.ANNULEE).toList();
        Set<Long> tourIds = tours.stream().map(Tour::getId).collect(Collectors.toSet());
        List<NonConformity> ncs = tourIds.isEmpty() ? List.of() : ncRepository.findByCompanyIdAndTourIdIn(company.getId(), tourIds);
        Set<Long> toursWithNc = ncs.stream().map(NonConformity::getTourId).collect(Collectors.toSet());

        Map<MedicalProduct, Integer> collected = new EnumMap<>(MedicalProduct.class);
        int delivered = 0;
        int done = 0;
        int failed = 0;
        int excursions = 0;
        int completed = 0;
        int compliant = 0;
        List<Long> transitMinutes = new ArrayList<>();
        int exceeded = 0;
        Map<LocalDate, int[]> daily = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            daily.put(d, new int[3]);
        }
        for (Tour t : tours) {
            int[] day = daily.computeIfAbsent(t.getDate(), d -> new int[3]);
            for (TourStop s : t.getStops()) {
                if (s.isTemperatureExcursion()) excursions++;
                if (s.getType().isPickup()) {
                    if (s.getStatus() == TourStop.StopStatus.FAIT) {
                        done++;
                        Map<MedicalProduct, Integer> counts = s.productCounts();
                        MedicalProduct.addAll(collected, counts);
                        day[0] += MedicalProduct.total(counts);
                    } else if (s.getStatus() == TourStop.StopStatus.ECHEC) {
                        failed++;
                    }
                } else if (s.getStatus() == TourStop.StopStatus.FAIT) {
                    int qty = MedicalProduct.total(s.productCounts());
                    delivered += qty;
                    day[1] += qty;
                }
            }
            if (t.getStatus() == Tour.TourStatus.TERMINEE) {
                completed++;
                if (!toursWithNc.contains(t.getId())) compliant++;
                for (Reconciliation.Transit tr : Reconciliation.of(t).transits()) {
                    if (tr.minutes() != null) {
                        transitMinutes.add(tr.minutes());
                        if (tr.exceeded()) exceeded++;
                    }
                }
            }
        }
        Map<Long, LocalDate> tourDates = tours.stream().collect(Collectors.toMap(Tour::getId, Tour::getDate));
        for (NonConformity n : ncs) {
            int[] day = daily.get(tourDates.get(n.getTourId()));
            if (day != null) day[2]++;
        }
        Map<String, Long> byType = new LinkedHashMap<>();
        for (NonConformity.Type type : NonConformity.Type.values()) {
            long count = ncs.stream().filter(n -> n.getType() == type).count();
            if (count > 0) byType.put(type.name(), count);
        }
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (MedicalProduct p : MedicalProduct.values()) {
            byCategory.put(p.name(), collected.getOrDefault(p, 0));
        }
        return new MedicalDashboardDto(from, to, tours.size(), completed,
                completed == 0 ? 0 : round(compliant * 100.0 / completed),
                done, failed, MedicalProduct.total(collected), delivered, byCategory, excursions,
                transitMinutes.isEmpty() ? null : round(transitMinutes.stream().mapToLong(Long::longValue).average().orElse(0)),
                transitMinutes.stream().max(Long::compare).orElse(null),
                exceeded, company.getMedicalMaxTransitMinutes(),
                ncRepository.countByCompanyIdAndStatusNot(company.getId(), NonConformity.Status.CLOTUREE),
                byType,
                daily.entrySet().stream().map(e -> new MedicalDashboardDto.Day(e.getKey(), e.getValue()[0],
                        e.getValue()[1], e.getValue()[2])).toList());
    }

    // ---------------------------------------------------------------- réglages

    @Transactional(readOnly = true)
    public MedicalSettingsDto settings() {
        return new MedicalSettingsDto(requireMedical().getMedicalMaxTransitMinutes());
    }

    @Transactional
    public MedicalSettingsDto updateSettings(MedicalSettingsDto req) {
        Company company = requireMedical();
        company.setMedicalMaxTransitMinutes(req.maxTransitMinutes());
        companyRepository.save(company);
        auditService.log("MEDICAL_REGLAGES", "Délai maximal d'acheminement : " + req.maxTransitMinutes() + " min");
        return new MedicalSettingsDto(company.getMedicalMaxTransitMinutes());
    }

    // ------------------------------------------------------------------ outils

    private Company requireMedical() {
        Company company = companyRepository.findById(TenantContext.require().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Société introuvable"));
        if (!company.isMedical()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Module réservé à l'activité Transport médical");
        }
        return company;
    }

    private Tour loadTour(Company company, Long tourId) {
        return tourRepository.findByIdAndCompanyId(tourId, company.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tournée introuvable"));
    }

    private static String opt(Object v) {
        return v == null ? "" : v.toString();
    }

    /** Échappement CSV + neutralisation de l'injection de formules (=, +, -, @). */
    private static String cell(String v) {
        if (v == null) return "";
        String s = v.replace("\r", " ").replace("\n", " ");
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return s.contains(";") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
