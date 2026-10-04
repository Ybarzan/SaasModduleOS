package com.fleethub.service.tour;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.tour.DispatchRequest;
import com.fleethub.dto.tour.DispatchResultDto;
import com.fleethub.dto.tour.OrderDto;
import com.fleethub.dto.tour.OrderRequest;
import com.fleethub.dto.tour.TourDto;
import com.fleethub.model.Company;
import com.fleethub.model.DeliveryOrder;
import com.fleethub.model.Driver;
import com.fleethub.model.Site;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;
import com.fleethub.model.Truck;
import com.fleethub.repository.DeliveryOrderRepository;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.SiteRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Commandes à planifier et répartition automatique en tournées
 * (VROOM si configuré, solveur intégré sinon).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final LocalTime DEFAULT_START = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(18, 0);
    private static final int MAX_ORDERS_PER_DISPATCH = 1000;

    private final DeliveryOrderRepository orderRepository;
    private final SiteRepository siteRepository;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;
    private final TourService tourService;
    private final RouteOptimizer optimizer;
    private final InsertionDispatchSolver insertionSolver;
    private final VroomDispatchSolver vroomSolver;

    @Value("${app.tours.average-speed-kmh:35}")
    private double averageSpeedKmh = 35;

    // ------------------------------------------------------------------ CRUD

    @Transactional(readOnly = true)
    public List<OrderDto> list(LocalDate date) {
        return orderRepository.findByCompanyIdAndDateOrderByIdAsc(TenantContext.companyId(), date).stream()
                .map(OrderDto::of).toList();
    }

    @Transactional
    public OrderDto create(OrderRequest req) {
        Site site = siteRepository.findByIdAndCompanyId(req.siteId(), TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Site introuvable"));
        DeliveryOrder o = new DeliveryOrder();
        o.setCompany(TenantContext.require());
        o.setDate(req.date());
        o.setSite(site);
        o.setReference(req.reference());
        o.setType(req.type() != null ? TourStop.StopType.valueOf(req.type()) : TourStop.StopType.LIVRAISON);
        o.setWindowStart(req.windowStart() != null ? req.windowStart() : site.getOpeningFrom());
        o.setWindowEnd(req.windowEnd() != null ? req.windowEnd() : site.getOpeningTo());
        if (o.getWindowStart() != null && o.getWindowEnd() != null && o.getWindowEnd().isBefore(o.getWindowStart())) {
            throw new IllegalArgumentException("La fin du créneau doit suivre son début");
        }
        o.setServiceMinutes(req.serviceMinutes() != null ? req.serviceMinutes()
                : site.getServiceMinutes() != null ? site.getServiceMinutes() : 5);
        o.setQuantity(req.quantity() != null ? req.quantity() : 1);
        o.setTemperatureMinCelsius(req.temperatureMinCelsius());
        o.setTemperatureMaxCelsius(req.temperatureMaxCelsius());
        o.setNotes(req.notes());
        return OrderDto.of(orderRepository.save(o));
    }

    @Transactional
    public void delete(Long id) {
        DeliveryOrder o = orderRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable"));
        if (o.getStatus() == DeliveryOrder.OrderStatus.PLANIFIEE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Commande déjà planifiée : retirez d'abord l'arrêt de sa tournée");
        }
        orderRepository.delete(o);
    }

    // ------------------------------------------------------------- répartition

    @Transactional
    public DispatchResultDto dispatch(DispatchRequest req) {
        Long companyId = TenantContext.companyId();
        Company company = TenantContext.require();
        Site depot = siteRepository.findByIdAndCompanyId(req.depotId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Dépôt introuvable"));
        if (!depot.hasCoordinates()) {
            throw new IllegalArgumentException("Le dépôt doit être localisé (coordonnées GPS) pour calculer les tournées");
        }
        LocalTime start = req.start() != null ? req.start() : DEFAULT_START;
        LocalTime end = req.end() != null ? req.end() : DEFAULT_END;
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("L'heure de retour doit suivre l'heure de départ");
        }

        // Véhicules : camion / chauffeur de la société, sans doublon
        List<Truck> trucks = new ArrayList<>();
        List<Driver> drivers = new ArrayList<>();
        Set<Long> usedTrucks = new HashSet<>();
        Set<Long> usedDrivers = new HashSet<>();
        List<DispatchSolver.Vehicle> vehicles = new ArrayList<>();
        for (DispatchRequest.VehicleSlot slot : req.vehicles()) {
            if (slot.truckId() == null && slot.driverId() == null) {
                throw new IllegalArgumentException("Chaque véhicule doit avoir un camion ou un chauffeur");
            }
            if (slot.truckId() != null && !usedTrucks.add(slot.truckId())) {
                throw new IllegalArgumentException("Un même véhicule est sélectionné deux fois");
            }
            if (slot.driverId() != null && !usedDrivers.add(slot.driverId())) {
                throw new IllegalArgumentException("Un même chauffeur est sélectionné deux fois");
            }
            trucks.add(slot.truckId() == null ? null : truckRepository.findByIdAndCompanyId(slot.truckId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Véhicule introuvable")));
            drivers.add(slot.driverId() == null ? null : driverRepository.findByIdAndCompanyId(slot.driverId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable")));
            vehicles.add(new DispatchSolver.Vehicle(slot.capacity(), start.toSecondOfDay(), end.toSecondOfDay()));
        }

        // Commandes à planifier (toutes, ou le sous-ensemble demandé)
        List<DeliveryOrder> pending = orderRepository.findByCompanyIdAndDateAndStatusOrderByIdAsc(
                companyId, req.date(), DeliveryOrder.OrderStatus.A_PLANIFIER);
        if (req.orderIds() != null && !req.orderIds().isEmpty()) {
            Set<Long> wanted = new HashSet<>(req.orderIds());
            pending = pending.stream().filter(o -> wanted.contains(o.getId())).toList();
        }
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("Aucune commande à planifier pour cette date");
        }
        if (pending.size() > MAX_ORDERS_PER_DISPATCH) {
            throw new IllegalArgumentException("Trop de commandes pour une seule répartition (max " + MAX_ORDERS_PER_DISPATCH + ")");
        }

        List<DispatchResultDto.Unassigned> unassigned = new ArrayList<>();
        List<DeliveryOrder> located = new ArrayList<>();
        for (DeliveryOrder o : pending) {
            if (o.getSite().hasCoordinates()) {
                located.add(o);
            } else {
                unassigned.add(unassigned(o, "Site non localisé : renseignez son adresse ou ses coordonnées"));
            }
        }

        DispatchSolver.Result result = located.isEmpty()
                ? new DispatchSolver.Result(List.of(), List.of(), "—")
                : solve(depot, located, vehicles);
        for (int j : result.unassigned()) {
            unassigned.add(unassigned(located.get(j),
                    "Non placée : capacité, créneau ou amplitude horaire incompatibles avec les véhicules choisis"));
        }

        // Création d'une tournée par véhicule utilisé
        String prefix = req.namePrefix() != null && !req.namePrefix().isBlank() ? req.namePrefix().trim() : "Tournée";
        List<TourDto> created = new ArrayList<>();
        double totalKm = 0;
        int number = 0;
        for (int v = 0; v < result.routes().size(); v++) {
            List<Integer> route = result.routes().get(v);
            if (route.isEmpty()) continue;
            number++;
            Tour tour = new Tour();
            tour.setCompany(company);
            tour.setDate(req.date());
            tour.setDepot(depot);
            tour.setPlannedStart(start);
            tour.setTruck(trucks.get(v));
            tour.setDriver(drivers.get(v));
            tour.setName(prefix + " " + number + label(trucks.get(v), drivers.get(v)));
            for (int j : route) {
                DeliveryOrder o = located.get(j);
                TourStop stop = new TourStop();
                stop.setCompany(company);
                stop.setTour(tour);
                stop.setSite(o.getSite());
                stop.setType(o.getType());
                stop.setWindowStart(o.getWindowStart());
                stop.setWindowEnd(o.getWindowEnd());
                stop.setServiceMinutes(o.getServiceMinutes());
                stop.setTemperatureMinCelsius(o.getTemperatureMinCelsius());
                stop.setTemperatureMaxCelsius(o.getTemperatureMaxCelsius());
                stop.setExpectedQuantity(o.getQuantity());
                stop.setNotes(joinNotes(o));
                tour.getStops().add(stop);
            }
            Tour saved = tourService.saveWithPlan(tour);
            for (int k = 0; k < route.size(); k++) {
                DeliveryOrder o = located.get(route.get(k));
                o.setTourStop(saved.getStops().get(k));
                o.setStatus(DeliveryOrder.OrderStatus.PLANIFIEE);
            }
            totalKm += saved.getPlannedDistanceKm() != null ? saved.getPlannedDistanceKm() : 0;
            created.add(TourDto.of(saved, true));
        }
        return new DispatchResultDto(result.solver(), created, unassigned, Math.round(totalKm * 10) / 10.0);
    }

    private DispatchSolver.Result solve(Site depot, List<DeliveryOrder> orders, List<DispatchSolver.Vehicle> vehicles) {
        int n = orders.size() + 1;
        double[][] coords = new double[n][2];
        coords[0] = new double[]{depot.getLatitude(), depot.getLongitude()};
        for (int i = 0; i < orders.size(); i++) {
            Site s = orders.get(i).getSite();
            coords[i + 1] = new double[]{s.getLatitude(), s.getLongitude()};
        }
        long[][] distances = new long[n][n];
        long[][] durations = new long[n][n];
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                if (a == b) continue;
                double km = optimizer.distanceKm(coords[a][0], coords[a][1], coords[b][0], coords[b][1]);
                distances[a][b] = Math.round(km * 1000);
                durations[a][b] = Math.round(km / averageSpeedKmh * 3600);
            }
        }
        List<DispatchSolver.Job> jobs = orders.stream().map(o -> new DispatchSolver.Job(
                o.getQuantity(),
                o.getWindowStart() != null ? o.getWindowStart().toSecondOfDay() : null,
                o.getWindowEnd() != null ? o.getWindowEnd().toSecondOfDay() : null,
                o.getServiceMinutes() * 60)).toList();

        if (vroomSolver.isEnabled()) {
            DispatchSolver.Result r = vroomSolver.solve(durations, distances, jobs, vehicles);
            if (r != null) return r;
        }
        return insertionSolver.solve(durations, distances, jobs, vehicles);
    }

    private static String label(Truck truck, Driver driver) {
        if (driver != null) {
            String last = driver.getLastName();
            return " – " + driver.getFirstName() + (last != null && !last.isBlank() ? " " + last.charAt(0) + "." : "");
        }
        if (truck != null) return " – " + truck.getRegistration();
        return "";
    }

    private static String joinNotes(DeliveryOrder o) {
        String ref = o.getReference() != null ? "Réf. " + o.getReference() : null;
        String qty = o.getQuantity() > 1 ? o.getQuantity() + " unité(s)" : null;
        return java.util.stream.Stream.of(ref, qty, o.getNotes())
                .filter(x -> x != null && !x.isBlank())
                .collect(Collectors.joining(" · "));
    }

    private static DispatchResultDto.Unassigned unassigned(DeliveryOrder o, String reason) {
        return new DispatchResultDto.Unassigned(o.getId(), o.getReference(), o.getSite().getName(), reason);
    }

    /** Index des sites de la société par clé de rapprochement (référence, puis nom + code postal). */
    static Map<String, Site> indexSites(List<Site> sites) {
        Map<String, Site> index = new java.util.HashMap<>();
        for (Site s : sites) {
            if (s.getReference() != null && !s.getReference().isBlank()) {
                index.putIfAbsent("ref:" + normalize(s.getReference()), s);
            }
            index.putIfAbsent("name:" + normalize(s.getName()) + "|" + normalize(s.getPostalCode()), s);
        }
        return index;
    }

    static String normalize(String v) {
        if (v == null) return "";
        return java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
