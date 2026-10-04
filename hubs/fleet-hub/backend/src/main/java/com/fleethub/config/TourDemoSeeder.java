package com.fleethub.config;

import com.fleethub.model.Company;
import com.fleethub.model.Driver;
import com.fleethub.model.Site;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;
import com.fleethub.model.Truck;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.SiteRepository;
import com.fleethub.repository.TourRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.service.tour.RouteOptimizer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Données de démonstration du module Tournées (collecte pharmacies ↔
 * laboratoire autour de Lyon) : dépôt, sites, deux camionnettes, et trois
 * semaines de tournées dont celle du jour, prête à être exécutée.
 */
final class TourDemoSeeder {

    private static final String[] FAILURES = {"Site fermé", "Destinataire absent", "Colis / échantillon non prêt"};

    private TourDemoSeeder() {
    }

    static void seed(Company demo, SiteRepository siteRepository, TourRepository tourRepository,
                     DriverRepository driverRepository, TruckRepository truckRepository,
                     RouteOptimizer optimizer, Random rnd) {
        Site depot = site(demo, "Dépôt Lyon Gerland", Site.SiteKind.DEPOT, "DEP-01",
                "60 avenue Tony Garnier", "69007", "Lyon", 45.7330, 4.8270, null, null, 0);
        Site lab = site(demo, "Laboratoire BioLyon Analyses", Site.SiteKind.LABORATOIRE, "LAB-01",
                "12 rue Garibaldi", "69006", "Lyon", 45.7680, 4.8530, LocalTime.of(7, 0), LocalTime.of(19, 0), 10);
        List<Site> pharmacies = List.of(
                site(demo, "Pharmacie des Brotteaux", Site.SiteKind.PHARMACIE, "FINESS 690012345",
                        "45 boulevard des Brotteaux", "69006", "Lyon", 45.7700, 4.8580, LocalTime.of(8, 30), LocalTime.of(12, 0), 6),
                site(demo, "Pharmacie de la Croix-Rousse", Site.SiteKind.PHARMACIE, "FINESS 690023456",
                        "3 place de la Croix-Rousse", "69004", "Lyon", 45.7740, 4.8320, LocalTime.of(8, 30), LocalTime.of(12, 30), 6),
                site(demo, "Pharmacie Saxe-Gambetta", Site.SiteKind.PHARMACIE, "FINESS 690034567",
                        "110 cours Gambetta", "69007", "Lyon", 45.7530, 4.8520, LocalTime.of(9, 0), LocalTime.of(12, 0), 5),
                site(demo, "Pharmacie de Monplaisir", Site.SiteKind.PHARMACIE, "FINESS 690045678",
                        "100 avenue des Frères Lumière", "69008", "Lyon", 45.7450, 4.8720, LocalTime.of(8, 30), LocalTime.of(12, 0), 5),
                site(demo, "Pharmacie de Villeurbanne Gratte-Ciel", Site.SiteKind.PHARMACIE, "FINESS 690056789",
                        "20 avenue Henri Barbusse", "69100", "Villeurbanne", 45.7700, 4.8800, LocalTime.of(9, 0), LocalTime.of(12, 30), 6),
                site(demo, "Pharmacie de Vaise", Site.SiteKind.PHARMACIE, "FINESS 690067890",
                        "15 rue de Bourgogne", "69009", "Lyon", 45.7740, 4.8050, LocalTime.of(8, 30), LocalTime.of(12, 0), 5),
                site(demo, "Pharmacie Bron Centre", Site.SiteKind.PHARMACIE, "FINESS 690078901",
                        "5 avenue Franklin Roosevelt", "69500", "Bron", 45.7390, 4.9120, LocalTime.of(9, 0), LocalTime.of(12, 0), 6),
                site(demo, "Clinique du Parc", Site.SiteKind.ETABLISSEMENT_SANTE, "FINESS 690089012",
                        "155 boulevard de Stalingrad", "69006", "Lyon", 45.7770, 4.8610, LocalTime.of(8, 0), LocalTime.of(11, 30), 10));
        siteRepository.save(depot);
        siteRepository.save(lab);
        pharmacies.forEach(siteRepository::save);

        Truck van1 = van(demo, "GV-201-LY", "Renault", "Kangoo E-Tech", Truck.FuelType.ELECTRIC, 45.7330, 4.8270);
        Truck van2 = van(demo, "GV-202-LY", "Citroën", "ë-Berlingo", Truck.FuelType.ELECTRIC, 45.7330, 4.8270);
        truckRepository.save(van1);
        truckRepository.save(van2);
        Driver sophie = new Driver(null, demo, "Sophie", "Lambert", "FR-201-456-789", "06 22 33 44 01",
                "sophie.lambert@fleet.fr", LocalDate.now().minusYears(2), true);
        Driver yanis = new Driver(null, demo, "Yanis", "Morel", "FR-201-567-890", "06 22 33 44 02",
                "yanis.morel@fleet.fr", LocalDate.now().minusYears(1), true);
        driverRepository.save(sophie);
        driverRepository.save(yanis);

        LocalDate today = LocalDate.now();
        for (int d = 21; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            // Pas de tournée le week-end, sauf celle du jour (la démo doit toujours en montrer une)
            if (day.getDayOfWeek().getValue() >= 6 && d > 0) continue;
            boolean past = d > 0;
            tourRepository.save(tour(demo, "Collecte matin – Est", day, sophie, van1, depot,
                    pharmacies.subList(0, 4), lab, optimizer, rnd, past));
            tourRepository.save(tour(demo, "Collecte matin – Ouest", day, yanis, van2, depot,
                    pharmacies.subList(4, 8), lab, optimizer, rnd, past));
        }
    }

    private static Tour tour(Company company, String name, LocalDate day, Driver driver, Truck truck, Site depot,
                             List<Site> pickups, Site lab, RouteOptimizer optimizer, Random rnd, boolean executed) {
        Tour tour = new Tour();
        tour.setCompany(company);
        tour.setName(name);
        tour.setDate(day);
        tour.setDriver(driver);
        tour.setTruck(truck);
        tour.setDepot(depot);
        tour.setPlannedStart(LocalTime.of(8, 15));

        List<TourStop> stops = new ArrayList<>();
        for (Site s : pickups) {
            stops.add(stop(company, tour, s, TourStop.StopType.COLLECTE, s.getOpeningFrom(), s.getOpeningTo(),
                    s.getServiceMinutes(), 2.0, 8.0));
        }
        // Remise au laboratoire en fin de tournée, avant midi
        TourStop delivery = stop(company, tour, lab, TourStop.StopType.LIVRAISON, LocalTime.of(10, 0),
                LocalTime.of(12, 30), lab.getServiceMinutes(), 2.0, 8.0);

        RouteOptimizer.Depot origin = new RouteOptimizer.Depot(depot.getLatitude(), depot.getLongitude());
        RouteOptimizer.Plan plan = optimizer.optimize(origin, stops.stream().map(TourDemoSeeder::node).toList(),
                tour.getPlannedStart());
        List<TourStop> ordered = new ArrayList<>();
        plan.order().forEach(i -> ordered.add(stops.get(i)));
        ordered.add(delivery);
        List<Integer> identity = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) identity.add(i);
        RouteOptimizer.Plan schedule = optimizer.schedule(origin, ordered.stream().map(TourDemoSeeder::node).toList(),
                identity, tour.getPlannedStart());
        for (int i = 0; i < ordered.size(); i++) {
            TourStop s = ordered.get(i);
            s.setSequence(i + 1);
            s.setPlannedArrival(schedule.arrivals().get(i));
            s.setPlannedLatenessMinutes(schedule.latenessMinutes().get(i));
        }
        tour.getStops().addAll(ordered);
        tour.setPlannedDistanceKm(schedule.distanceKm());
        tour.setPlannedDurationMinutes(schedule.durationMinutes());
        tour.setOptimizedAt(day.atTime(7, 30));

        if (executed) {
            execute(tour, day, rnd);
        }
        return tour;
    }

    /** Simule l'exécution : horodatages, preuves, quelques échecs et retards, rares ruptures de froid. */
    private static void execute(Tour tour, LocalDate day, Random rnd) {
        tour.setStatus(Tour.TourStatus.TERMINEE);
        tour.setStartedAt(day.atTime(tour.getPlannedStart()));
        int collected = 0;
        LocalDateTime last = tour.getStartedAt();
        for (TourStop s : tour.getStops()) {
            int drift = rnd.nextInt(25) - 8;
            LocalDateTime arrived = day.atTime(s.getPlannedArrival()).plusMinutes(drift);
            s.setArrivedAt(arrived);
            s.setCompletedAt(arrived.plusMinutes(s.getServiceMinutes()));
            last = s.getCompletedAt();
            if (s.getType() == TourStop.StopType.COLLECTE && rnd.nextDouble() < 0.06) {
                s.setStatus(TourStop.StopStatus.ECHEC);
                s.setFailureReason(FAILURES[rnd.nextInt(FAILURES.length)]);
                continue;
            }
            s.setStatus(TourStop.StopStatus.FAIT);
            s.setSignedBy(s.getType() == TourStop.StopType.LIVRAISON ? "Accueil laboratoire" : "Pharmacien");
            s.setTemperatureCelsius(Math.round((4 + rnd.nextDouble() * 3 + (rnd.nextDouble() < 0.03 ? 5 : 0)) * 10) / 10.0);
            if (s.getType() == TourStop.StopType.COLLECTE) {
                int samples = 1 + rnd.nextInt(6);
                collected += samples;
                s.setSampleCount(samples);
                StringBuilder codes = new StringBuilder();
                for (int k = 0; k < samples; k++) {
                    if (k > 0) codes.append(',');
                    codes.append("SC-").append(day.toString().replace("-", "")).append('-')
                            .append(s.getSite().getId()).append('-').append(k + 1);
                }
                s.setScannedCodes(codes.toString());
            } else {
                s.setSampleCount(collected);
            }
        }
        tour.setCompletedAt(last.plusMinutes(15));
    }

    private static RouteOptimizer.Node node(TourStop s) {
        return new RouteOptimizer.Node(s.getSite().getLatitude(), s.getSite().getLongitude(),
                s.getWindowStart(), s.getWindowEnd(), s.getServiceMinutes());
    }

    private static TourStop stop(Company company, Tour tour, Site site, TourStop.StopType type, LocalTime from,
                                 LocalTime to, Integer service, Double tMin, Double tMax) {
        TourStop s = new TourStop();
        s.setCompany(company);
        s.setTour(tour);
        s.setSite(site);
        s.setType(type);
        s.setWindowStart(from);
        s.setWindowEnd(to);
        s.setServiceMinutes(service != null ? service : 5);
        s.setTemperatureMinCelsius(tMin);
        s.setTemperatureMaxCelsius(tMax);
        return s;
    }

    private static Site site(Company company, String name, Site.SiteKind kind, String ref, String address,
                             String postalCode, String city, double lat, double lon,
                             LocalTime from, LocalTime to, int serviceMinutes) {
        Site s = new Site();
        s.setCompany(company);
        s.setName(name);
        s.setKind(kind);
        s.setReference(ref);
        s.setAddress(address);
        s.setPostalCode(postalCode);
        s.setCity(city);
        s.setLatitude(lat);
        s.setLongitude(lon);
        s.setOpeningFrom(from);
        s.setOpeningTo(to);
        s.setServiceMinutes(serviceMinutes);
        return s;
    }

    private static Truck van(Company company, String reg, String brand, String model, Truck.FuelType fuel,
                             double lat, double lon) {
        Truck t = new Truck();
        t.setCompany(company);
        t.setRegistration(reg);
        t.setBrand(brand);
        t.setModel(model);
        t.setModelYear(2024);
        t.setTruckType(Truck.TruckType.VUL);
        t.setFuelType(fuel);
        t.setCapacityTons(0.6);
        t.setAcquisitionDate(LocalDate.now().minusYears(1));
        t.setPurchasePrice(32000.0);
        t.setExpectedConsumptionL100Km(Truck.TruckType.VUL.getDefaultConsumptionL100Km());
        t.setCurrentLatitude(lat);
        t.setCurrentLongitude(lon);
        t.setCurrentSpeedKph(0.0);
        t.setCurrentStatus(Truck.VehicleStatus.ARRET);
        t.setLastGpsUpdate(LocalDateTime.now().minusMinutes(5));
        t.setActive(true);
        return t;
    }
}
