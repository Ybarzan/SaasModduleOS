package com.fleethub.service.tour;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.tour.ReorderRequest;
import com.fleethub.dto.tour.StopCompletionRequest;
import com.fleethub.dto.tour.TourDto;
import com.fleethub.dto.tour.TourRequest;
import com.fleethub.dto.tour.TourStatsDto;
import com.fleethub.dto.tour.TourStopRequest;
import com.fleethub.model.Site;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;
import com.fleethub.repository.DeliveryOrderRepository;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.SiteRepository;
import com.fleethub.repository.TourRepository;
import com.fleethub.repository.TourStopRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Planification et exécution des tournées : composition, optimisation de
 * l'ordre de passage, suivi d'exécution et preuves de passage.
 * Toutes les opérations sont restreintes à la société courante.
 */
@Service
@RequiredArgsConstructor
public class TourService {

    private static final LocalTime DEFAULT_START = LocalTime.of(8, 0);
    private static final int DEFAULT_SERVICE_MINUTES = 5;
    private static final DateTimeFormatter CSV_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TourRepository tourRepository;
    private final TourStopRepository stopRepository;
    private final SiteRepository siteRepository;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;
    private final RouteOptimizer optimizer;
    private final DeliveryOrderRepository orderRepository;

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public List<TourDto> list(LocalDate from, LocalDate to) {
        return tourRepository.findByCompanyIdAndDateBetweenOrderByDateAscPlannedStartAsc(
                TenantContext.companyId(), from, to).stream().map(t -> TourDto.of(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public TourDto get(Long id) {
        return TourDto.of(load(id), true);
    }

    /** Tournées du jour d'un chauffeur (vue mobile), avec leurs arrêts. */
    @Transactional(readOnly = true)
    public List<TourDto> forDriver(Long driverId, LocalDate date) {
        List<Tour> tours = tourRepository.findByCompanyIdAndDriverIdAndDateOrderByPlannedStartAsc(
                        TenantContext.companyId(), driverId, date).stream()
                .filter(t -> t.getStatus() != Tour.TourStatus.ANNULEE)
                .toList();
        // Suggestion du signataire habituel de chaque site (évite la saisie au chauffeur)
        Set<Long> siteIds = new HashSet<>();
        tours.forEach(t -> t.getStops().forEach(s -> siteIds.add(s.getSite().getId())));
        Map<Long, String> signers = new java.util.HashMap<>();
        if (!siteIds.isEmpty()) {
            for (Object[] row : stopRepository.recentSigners(TenantContext.companyId(), siteIds)) {
                signers.putIfAbsent((Long) row[0], (String) row[1]);
            }
        }
        return tours.stream().map(t -> TourDto.of(t, true, signers)).toList();
    }

    /** Vérifie qu'une tournée est bien affectée à ce chauffeur (accès mobile). */
    @Transactional(readOnly = true)
    public void requireAssignedTo(Long tourId, Long driverId) {
        Tour tour = load(tourId);
        if (driverId == null || tour.getDriver() == null || !tour.getDriver().getId().equals(driverId)) {
            throw new ResourceNotFoundException("Tournée introuvable");
        }
    }

    /** Export de traçabilité : un passage par ligne (séparateur « ; », BOM UTF-8 pour Excel). */
    @Transactional(readOnly = true)
    public String traceabilityCsv(LocalDate from, LocalDate to) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("date;tournee;chauffeur;vehicule;ordre;site;type_site;reference_site;type_arret;creneau_debut;creneau_fin;")
                .append("arrivee;cloture;statut;dans_creneau;signataire;colis;echantillons;codes_scannes;")
                .append("temperature_c;plage_min_c;plage_max_c;rupture_froid;motif_echec\n");
        List<Tour> tours = tourRepository.findByCompanyIdAndDateBetweenOrderByDateAscPlannedStartAsc(
                TenantContext.companyId(), from, to);
        for (Tour tour : tours) {
            String driver = tour.getDriver() != null ? tour.getDriver().getFirstName() + " " + tour.getDriver().getLastName() : "";
            String truck = tour.getTruck() != null ? tour.getTruck().getRegistration() : "";
            for (TourStop s : tour.getStops()) {
                Boolean onTime = s.deliveredOnTime();
                String[] row = {
                        String.valueOf(tour.getDate()), cell(tour.getName()), cell(driver), cell(truck),
                        String.valueOf(s.getSequence()), cell(s.getSite().getName()), s.getSite().getKind().name(),
                        cell(s.getSite().getReference()), s.getType().name(), opt(s.getWindowStart()), opt(s.getWindowEnd()),
                        s.getArrivedAt() != null ? s.getArrivedAt().format(CSV_TS) : "",
                        s.getCompletedAt() != null ? s.getCompletedAt().format(CSV_TS) : "",
                        s.getStatus().name(), onTime == null ? "" : onTime ? "oui" : "non",
                        cell(s.getSignedBy()), opt(s.getParcelCount()), opt(s.getSampleCount()), cell(s.getScannedCodes()),
                        opt(s.getTemperatureCelsius()), opt(s.getTemperatureMinCelsius()), opt(s.getTemperatureMaxCelsius()),
                        s.isTemperatureExcursion() ? "oui" : "non", cell(s.getFailureReason())
                };
                csv.append(String.join(";", row)).append('\n');
            }
        }
        return csv.toString();
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

    // ------------------------------------------------------------ composition

    @Transactional
    public TourDto create(TourRequest req) {
        Tour tour = new Tour();
        tour.setCompany(TenantContext.require());
        apply(tour, req);
        return TourDto.of(tourRepository.save(tour), true);
    }

    @Transactional
    public TourDto update(Long id, TourRequest req) {
        Tour tour = load(id);
        requireEditable(tour);
        apply(tour, req);
        replan(tour);
        return TourDto.of(tourRepository.save(tour), true);
    }

    @Transactional
    public void delete(Long id) {
        Tour tour = load(id);
        if (tour.getStatus() == Tour.TourStatus.EN_COURS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tournée en cours : terminez-la ou annulez-la d'abord");
        }
        orderRepository.releaseByTour(tour); // les commandes redeviennent « à planifier »
        tourRepository.delete(tour);
    }

    /** Duplique une tournée (mêmes arrêts, statut remis à zéro) à une autre date : tournées récurrentes. */
    @Transactional
    public TourDto duplicate(Long id, LocalDate date) {
        Tour source = load(id);
        Tour copy = new Tour();
        copy.setCompany(source.getCompany());
        copy.setName(source.getName());
        copy.setDate(date != null ? date : source.getDate().plusDays(1));
        copy.setDriver(source.getDriver());
        copy.setTruck(source.getTruck());
        copy.setDepot(source.getDepot());
        copy.setPlannedStart(source.getPlannedStart());
        copy.setNotes(source.getNotes());
        for (TourStop s : source.getStops()) {
            TourStop c = new TourStop();
            c.setCompany(s.getCompany());
            c.setTour(copy);
            c.setSite(s.getSite());
            c.setSequence(s.getSequence());
            c.setType(s.getType());
            c.setWindowStart(s.getWindowStart());
            c.setWindowEnd(s.getWindowEnd());
            c.setServiceMinutes(s.getServiceMinutes());
            c.setTemperatureMinCelsius(s.getTemperatureMinCelsius());
            c.setTemperatureMaxCelsius(s.getTemperatureMaxCelsius());
            c.setExpectedQuantity(s.getExpectedQuantity());
            c.setNotes(s.getNotes());
            copy.getStops().add(c);
        }
        replan(copy);
        return TourDto.of(tourRepository.save(copy), true);
    }

    @Transactional
    public TourDto addStop(Long tourId, TourStopRequest req) {
        Tour tour = load(tourId);
        requireEditable(tour);
        Site site = loadSite(req.siteId());
        TourStop stop = new TourStop();
        stop.setCompany(tour.getCompany());
        stop.setTour(tour);
        stop.setSite(site);
        stop.setSequence(tour.getStops().size() + 1);
        applyStop(stop, req, site);
        tour.getStops().add(stop);
        replan(tour);
        return TourDto.of(tourRepository.save(tour), true);
    }

    @Transactional
    public TourDto updateStop(Long tourId, Long stopId, TourStopRequest req) {
        Tour tour = load(tourId);
        requireEditable(tour);
        TourStop stop = findStop(tour, stopId);
        Site site = loadSite(req.siteId());
        stop.setSite(site);
        applyStop(stop, req, site);
        replan(tour);
        return TourDto.of(tourRepository.save(tour), true);
    }

    @Transactional
    public TourDto removeStop(Long tourId, Long stopId) {
        Tour tour = load(tourId);
        requireEditable(tour);
        TourStop stop = findStop(tour, stopId);
        if (stop.getStatus() != TourStop.StopStatus.A_FAIRE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un arrêt déjà clôturé ne peut pas être retiré");
        }
        orderRepository.releaseByStop(stop);
        tour.getStops().remove(stop);
        renumber(tour.getStops());
        replan(tour);
        return TourDto.of(tourRepository.save(tour), true);
    }

    /** Ordre manuel imposé par l'exploitant. */
    @Transactional
    public TourDto reorder(Long tourId, ReorderRequest req) {
        Tour tour = load(tourId);
        requireEditable(tour);
        Map<Long, TourStop> byId = tour.getStops().stream()
                .collect(Collectors.toMap(TourStop::getId, Function.identity()));
        if (req.stopIds().size() != byId.size() || !byId.keySet().equals(new HashSet<>(req.stopIds()))) {
            throw new IllegalArgumentException("La liste doit contenir exactement tous les arrêts de la tournée");
        }
        List<TourStop> ordered = req.stopIds().stream().map(byId::get).toList();
        applyOrder(tour, ordered);
        replan(tour);
        return TourDto.of(tourRepository.save(tour), true);
    }

    /**
     * Optimise l'ordre des arrêts restant à faire (les arrêts déjà clôturés
     * gardent leur place en tête), puis recalcule les heures d'arrivée.
     */
    @Transactional
    public TourDto optimize(Long tourId) {
        Tour tour = load(tourId);
        requireEditable(tour);
        List<TourStop> closed = tour.getStops().stream()
                .filter(s -> s.getStatus() != TourStop.StopStatus.A_FAIRE).toList();
        List<TourStop> open = tour.getStops().stream()
                .filter(s -> s.getStatus() == TourStop.StopStatus.A_FAIRE).toList();

        RouteOptimizer.Depot origin = depotOf(tour);
        if (!closed.isEmpty()) {
            // On repart du dernier arrêt clôturé localisé
            Site last = closed.get(closed.size() - 1).getSite();
            if (last.hasCoordinates()) {
                origin = new RouteOptimizer.Depot(last.getLatitude(), last.getLongitude());
            }
        }
        LocalTime start = tour.getStatus() == Tour.TourStatus.EN_COURS && tour.getDate().equals(LocalDate.now())
                ? LocalTime.now().withSecond(0).withNano(0)
                : tour.getPlannedStart();
        RouteOptimizer.Plan plan = optimizer.optimize(origin, nodes(open), start);

        List<TourStop> ordered = new ArrayList<>(closed);
        plan.order().forEach(i -> ordered.add(open.get(i)));
        applyOrder(tour, ordered);
        replan(tour);
        tour.setOptimizedAt(LocalDateTime.now());
        return TourDto.of(tourRepository.save(tour), true);
    }

    // -------------------------------------------------------------- exécution

    @Transactional
    public TourDto start(Long tourId) {
        Tour tour = load(tourId);
        if (tour.getStatus() != Tour.TourStatus.PLANIFIEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une tournée planifiée peut démarrer");
        }
        if (tour.getStops().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La tournée ne contient aucun arrêt");
        }
        tour.setStatus(Tour.TourStatus.EN_COURS);
        tour.setStartedAt(LocalDateTime.now());
        return TourDto.of(tourRepository.save(tour), true);
    }

    @Transactional
    public TourDto complete(Long tourId) {
        Tour tour = load(tourId);
        if (tour.getStatus() != Tour.TourStatus.EN_COURS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une tournée en cours peut être terminée");
        }
        tour.setStatus(Tour.TourStatus.TERMINEE);
        tour.setCompletedAt(LocalDateTime.now());
        return TourDto.of(tourRepository.save(tour), true);
    }

    @Transactional
    public TourDto cancel(Long tourId) {
        Tour tour = load(tourId);
        if (tour.getStatus() == Tour.TourStatus.TERMINEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une tournée terminée ne peut pas être annulée");
        }
        orderRepository.releaseByTour(tour); // commandes à replanifier
        tour.setStatus(Tour.TourStatus.ANNULEE);
        return TourDto.of(tourRepository.save(tour), true);
    }

    /** Signale l'arrivée sur site (horodatage réel). */
    @Transactional
    public TourDto arrive(Long tourId, Long stopId) {
        return arrive(tourId, stopId, null);
    }

    /** Arrivée sur site, éventuellement horodatée par le téléphone (action faite hors connexion). */
    @Transactional
    public TourDto arrive(Long tourId, Long stopId, LocalDateTime occurredAt) {
        Tour tour = load(tourId);
        if (tour.getStatus() == Tour.TourStatus.PLANIFIEE) {
            // Arriver au premier arrêt démarre la tournée : une action de moins pour le chauffeur
            tour.setStatus(Tour.TourStatus.EN_COURS);
            tour.setStartedAt(effectiveTime(occurredAt));
        }
        requireRunning(tour);
        TourStop stop = findStop(tour, stopId);
        if (stop.getArrivedAt() == null) {
            stop.setArrivedAt(effectiveTime(occurredAt));
        }
        return TourDto.of(tourRepository.save(tour), true);
    }

    /** Clôture un arrêt avec sa preuve de passage. La tournée se termine quand tous les arrêts sont clôturés. */
    @Transactional
    public TourDto completeStop(Long tourId, Long stopId, StopCompletionRequest req) {
        Tour tour = load(tourId);
        if (tour.getStatus() == Tour.TourStatus.PLANIFIEE) {
            tour.setStatus(Tour.TourStatus.EN_COURS);
            tour.setStartedAt(LocalDateTime.now());
        }
        requireRunning(tour);
        TourStop stop = findStop(tour, stopId);
        TourStop.StopStatus status = TourStop.StopStatus.valueOf(req.status());
        if (status == TourStop.StopStatus.ECHEC && (req.failureReason() == null || req.failureReason().isBlank())) {
            throw new IllegalArgumentException("Le motif d'échec est obligatoire");
        }
        LocalDateTime now = effectiveTime(req.occurredAt());
        if (stop.getArrivedAt() == null || stop.getArrivedAt().isAfter(now)) {
            stop.setArrivedAt(now);
        }
        stop.setCompletedAt(now);
        stop.setStatus(status);
        stop.setSignedBy(req.signedBy());
        stop.setParcelCount(req.parcelCount());
        stop.setSampleCount(req.sampleCount());
        stop.setTemperatureCelsius(req.temperatureCelsius());
        stop.setScannedCodes(req.scannedCodes());
        stop.setFailureReason(status == TourStop.StopStatus.ECHEC ? req.failureReason() : null);
        if (req.notes() != null && !req.notes().isBlank()) {
            // On conserve les consignes de l'exploitant : la remarque du chauffeur s'ajoute
            String driverNote = "Chauffeur : " + req.notes().trim();
            String merged = stop.getNotes() == null || stop.getNotes().isBlank() ? driverNote : stop.getNotes() + " · " + driverNote;
            stop.setNotes(merged.length() > 1000 ? merged.substring(0, 1000) : merged);
        }
        boolean allClosed = tour.getStops().stream().allMatch(s -> s.getStatus() != TourStop.StopStatus.A_FAIRE);
        if (allClosed) {
            tour.setStatus(Tour.TourStatus.TERMINEE);
            tour.setCompletedAt(now);
        }
        return TourDto.of(tourRepository.save(tour), true);
    }

    // ------------------------------------------------------------- indicateurs

    @Transactional(readOnly = true)
    public TourStatsDto stats(LocalDate from, LocalDate to) {
        Long companyId = TenantContext.companyId();
        List<Tour> tours = tourRepository.findByCompanyIdAndDateBetweenOrderByDateAscPlannedStartAsc(companyId, from, to)
                .stream().filter(t -> t.getStatus() != Tour.TourStatus.ANNULEE).toList();
        List<TourStop> stops = tours.stream().flatMap(t -> t.getStops().stream()).toList();

        int done = count(stops, s -> s.getStatus() == TourStop.StopStatus.FAIT);
        int failed = count(stops, s -> s.getStatus() == TourStop.StopStatus.ECHEC);
        List<Boolean> punctuality = stops.stream().map(TourStop::deliveredOnTime).filter(Objects::nonNull).toList();
        int onTime = (int) punctuality.stream().filter(Boolean::booleanValue).count();
        double plannedKm = tours.stream().mapToDouble(t -> t.getPlannedDistanceKm() != null ? t.getPlannedDistanceKm() : 0).sum();

        Map<String, Long> reasons = stops.stream()
                .filter(s -> s.getStatus() == TourStop.StopStatus.ECHEC && s.getFailureReason() != null)
                .collect(Collectors.groupingBy(TourStop::getFailureReason, LinkedHashMap::new, Collectors.counting()));

        return new TourStatsDto(from, to,
                tours.size(),
                count(tours, t -> t.getStatus() == Tour.TourStatus.TERMINEE),
                stops.size(), done, failed,
                pct(done, done + failed),
                pct(onTime, punctuality.size()),
                tours.isEmpty() ? 0 : round((double) stops.size() / tours.size()),
                round(plannedKm),
                stops.isEmpty() ? 0 : round(plannedKm / stops.size()),
                stops.stream().mapToInt(s -> s.getSampleCount() != null ? s.getSampleCount() : 0).sum(),
                stops.stream().mapToInt(s -> s.getParcelCount() != null ? s.getParcelCount() : 0).sum(),
                count(stops, TourStop::isTemperatureExcursion),
                reasons);
    }

    // ------------------------------------------------------------------ outils

    private void apply(Tour tour, TourRequest req) {
        Long companyId = TenantContext.companyId();
        tour.setName(req.name().trim());
        tour.setDate(req.date());
        tour.setPlannedStart(req.plannedStart() != null ? req.plannedStart() : DEFAULT_START);
        tour.setNotes(req.notes());
        tour.setDriver(req.driverId() == null ? null : driverRepository.findByIdAndCompanyId(req.driverId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable")));
        tour.setTruck(req.truckId() == null ? null : truckRepository.findByIdAndCompanyId(req.truckId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Véhicule introuvable")));
        tour.setDepot(req.depotId() == null ? null : loadSite(req.depotId()));
    }

    private void applyStop(TourStop stop, TourStopRequest req, Site site) {
        stop.setType(req.type() != null ? TourStop.StopType.valueOf(req.type()) : TourStop.StopType.LIVRAISON);
        stop.setWindowStart(req.windowStart() != null ? req.windowStart() : site.getOpeningFrom());
        stop.setWindowEnd(req.windowEnd() != null ? req.windowEnd() : site.getOpeningTo());
        if (stop.getWindowStart() != null && stop.getWindowEnd() != null && stop.getWindowEnd().isBefore(stop.getWindowStart())) {
            throw new IllegalArgumentException("La fin du créneau doit suivre son début");
        }
        stop.setServiceMinutes(req.serviceMinutes() != null ? req.serviceMinutes()
                : site.getServiceMinutes() != null ? site.getServiceMinutes() : DEFAULT_SERVICE_MINUTES);
        stop.setTemperatureMinCelsius(req.temperatureMinCelsius());
        stop.setTemperatureMaxCelsius(req.temperatureMaxCelsius());
        stop.setExpectedQuantity(req.expectedQuantity());
        stop.setNotes(req.notes());
    }

    /** Recalcule heures d'arrivée, retards, distance et durée pour l'ordre courant. */
    /** Enregistre une tournée composée ailleurs (répartition automatique) après calcul de son planning. */
    @Transactional
    public Tour saveWithPlan(Tour tour) {
        renumber(tour.getStops());
        replan(tour);
        return tourRepository.save(tour);
    }

    private void replan(Tour tour) {
        List<TourStop> stops = tour.getStops();
        if (stops.isEmpty()) {
            tour.setPlannedDistanceKm(0.0);
            tour.setPlannedDurationMinutes(0);
            return;
        }
        List<Integer> identity = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) identity.add(i);
        RouteOptimizer.Plan plan = optimizer.schedule(depotOf(tour), nodes(stops), identity, tour.getPlannedStart());
        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).setPlannedArrival(plan.arrivals().get(i));
            stops.get(i).setPlannedLatenessMinutes(plan.latenessMinutes().get(i));
        }
        tour.setPlannedDistanceKm(plan.distanceKm());
        tour.setPlannedDurationMinutes(plan.durationMinutes());
    }

    private void applyOrder(Tour tour, List<TourStop> ordered) {
        // Réordonne la collection gérée sans orphelins : on vide puis on réinsère
        tour.getStops().clear();
        tour.getStops().addAll(ordered);
        renumber(tour.getStops());
    }

    private static void renumber(List<TourStop> stops) {
        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).setSequence(i + 1);
        }
    }

    private static List<RouteOptimizer.Node> nodes(List<TourStop> stops) {
        return stops.stream().map(s -> new RouteOptimizer.Node(
                s.getSite().getLatitude(), s.getSite().getLongitude(),
                s.getWindowStart(), s.getWindowEnd(), s.getServiceMinutes())).toList();
    }

    private static RouteOptimizer.Depot depotOf(Tour tour) {
        Site d = tour.getDepot();
        return d != null && d.hasCoordinates() ? new RouteOptimizer.Depot(d.getLatitude(), d.getLongitude()) : null;
    }

    private Tour load(Long id) {
        return tourRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Tournée introuvable"));
    }

    private Site loadSite(Long id) {
        return siteRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Site introuvable"));
    }

    private static TourStop findStop(Tour tour, Long stopId) {
        return tour.getStops().stream().filter(s -> s.getId().equals(stopId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Arrêt introuvable"));
    }

    private static void requireEditable(Tour tour) {
        if (!tour.isEditable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tournée " + tour.getStatus().name().toLowerCase() + " : modification impossible");
        }
    }

    /**
     * Heure retenue pour une action terrain : celle du téléphone si l'action a été
     * faite hors connexion, bornée à [maintenant − 24 h ; maintenant] pour éviter
     * qu'une horloge déréglée ne fausse la traçabilité.
     */
    static LocalDateTime effectiveTime(LocalDateTime occurredAt) {
        LocalDateTime now = LocalDateTime.now();
        if (occurredAt == null || occurredAt.isAfter(now.plusMinutes(5)) || occurredAt.isBefore(now.minusHours(24))) {
            return now;
        }
        return occurredAt.isAfter(now) ? now : occurredAt;
    }

    private static void requireRunning(Tour tour) {
        if (tour.getStatus() != Tour.TourStatus.EN_COURS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La tournée n'est pas en cours");
        }
    }

    private static <T> int count(List<T> items, java.util.function.Predicate<T> p) {
        return (int) items.stream().filter(p).count();
    }

    private static double pct(int part, int total) {
        return total == 0 ? 0 : round(part * 100.0 / total);
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
