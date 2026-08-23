package com.fleethub.config;

import com.fleethub.model.*;
import com.fleethub.repository.*;
import com.fleethub.service.TachographService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Jeu de données de démonstration (dev). Désactivé en prod par défaut
 * ({@code app.seed.enabled=false}) : le premier SAAS_ADMIN est alors créé par
 * {@link SaaSAdminBootstrap}.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

    private final PasswordEncoder passwordEncoder;
    private final TachographService tachographService;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Value("${app.security.gest-password}")
    private String gestPassword;

    /**
     * S'exécute AVANT {@link SaaSAdminBootstrap} (ordre 1 &lt; 2) pour que le
     * tenant de démonstration soit créé en premier : sinon le saasadmin créé
     * par le bootstrap ferait sauter le garde-fou {@code count() > 0}.
     */
    @Bean
    @Order(1)
    CommandLineRunner seed(AppUserRepository userRepository,
                           CompanyRepository companyRepository,
                           DriverRepository driverRepository,
                           TruckRepository truckRepository,
                           AssignmentRepository assignmentRepository,
                           TripRepository tripRepository,
                           DrivingEventRepository eventRepository,
                           TachographDayRepository tachoRepository,
                           MaintenanceRepository maintenanceRepository,
                           FuelRecordRepository fuelRepository,
                           CostRecordRepository costRepository) {
        return args -> {
            if (userRepository.count() > 0) return;
            Random rnd = new Random(42);
            LocalDate today = LocalDate.now();

            // ---- Société de démonstration (tenant) ----
            Company demo = new Company();
            demo.setName("Fleet Hub Démo");
            demo.setPlan(Company.SubscriptionPlan.PRO);
            demo.setStatus(Company.CompanyStatus.ACTIVE);
            demo.setCountry("FR");
            demo.setContactEmail("demo@fleethub.fr");
            demo.setCreatedAt(LocalDateTime.now());
            companyRepository.save(demo);

            // ---- Utilisateurs ----
            // Opérateur plateforme (back-office SaaS), sans tenant.
            AppUser saasAdmin = new AppUser();
            saasAdmin.setUsername("saasadmin");
            saasAdmin.setPassword(passwordEncoder.encode(adminPassword));
            saasAdmin.setRole("SAAS_ADMIN");
            saasAdmin.setDisplayName("Opérateur Plateforme");
            saasAdmin.setEmail("ops@fleethub.fr");
            saasAdmin.setEnabled(true);
            saasAdmin.setCreatedAt(LocalDateTime.now());
            userRepository.save(saasAdmin);
            // Comptes du tenant de démonstration.
            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            admin.setDisplayName("Gestionnaire Flotte");
            admin.setEmail("admin@fleethub.fr");
            admin.setEnabled(true);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setCompany(demo);
            userRepository.save(admin);
            AppUser gest = new AppUser();
            gest.setUsername("gestionnaire");
            gest.setPassword(passwordEncoder.encode(gestPassword));
            gest.setRole("GESTIONNAIRE");
            gest.setDisplayName("Responsable Transport");
            gest.setEmail("gest@fleethub.fr");
            gest.setEnabled(true);
            gest.setCreatedAt(LocalDateTime.now());
            gest.setCompany(demo);
            userRepository.save(gest);

            // ---- Camions ----
            Truck[] trucks = {
                truck(demo, null, "GT-123-AB", "Renault Trucks", "T High 520", 2021, Truck.TruckType.TRACTEUR, Truck.FuelType.DIESEL, 26.0, 33.0, 46.2044, 5.7815, Truck.VehicleStatus.ROULAGE, 84, "Lyon"),
                truck(demo, null, "GT-456-CD", "Volvo", "FH 460", 2022, Truck.TruckType.TRACTEUR, Truck.FuelType.DIESEL, 26.0, 31.0, 48.8566, 2.3522, Truck.VehicleStatus.ARRET, 0, "Paris"),
                truck(demo, null, "GT-789-EF", "Scania", "R 450", 2020, Truck.TruckType.TRACTEUR, Truck.FuelType.DIESEL, 26.0, 30.5, 43.2965, 5.3698, Truck.VehicleStatus.ROULAGE, 76, "Marseille"),
                truck(demo, null, "GT-321-GH", "MAN", "TGX 510", 2023, Truck.TruckType.TRACTEUR, Truck.FuelType.DIESEL, 26.0, 32.0, 44.8378, -0.5792, Truck.VehicleStatus.REPOS, 0, "Bordeaux"),
                truck(demo, null, "GT-654-IJ", "Mercedes-Benz", "Actros 450", 2021, Truck.TruckType.TRACTEUR, Truck.FuelType.DIESEL, 26.0, 30.0, 47.2184, -1.5536, Truck.VehicleStatus.ALERTE, 0, "Nantes"),
                truck(demo, null, "GT-987-KL", "Iveco", "S-Way 460", 2022, Truck.TruckType.TRACTEUR, Truck.FuelType.DIESEL, 26.0, 34.0, 43.6047, 1.4442, Truck.VehicleStatus.IMMOBILISE, 0, "Toulouse")
            };
            for (Truck t : trucks) truckRepository.save(t);

            // ---- Chauffeurs ----
            Driver[] drivers = {
                driver(demo, null, "Jean", "Martin", "FR-104-852-371", "06 12 34 56 01", "jean.martin@fleet.fr", today.minusYears(6)),
                driver(demo, null, "Ahmed", "Benali", "FR-104-741-258", "06 12 34 56 02", "ahmed.benali@fleet.fr", today.minusYears(4)),
                driver(demo, null, "Pierre", "Dubois", "FR-104-963-147", "06 12 34 56 03", "pierre.dubois@fleet.fr", today.minusYears(2)),
                driver(demo, null, "Lucas", "Moreau", "FR-104-258-963", "06 12 34 56 04", "lucas.moreau@fleet.fr", today.minusYears(1)),
                driver(demo, null, "Karim", "Haddad", "FR-104-147-852", "06 12 34 56 05", "karim.haddad@fleet.fr", today.minusYears(5)),
                driver(demo, null, "Thomas", "Petit", "FR-104-369-258", "06 12 34 56 06", "thomas.petit@fleet.fr", today.minusYears(3))
            };
            for (Driver d : drivers) driverRepository.save(d);

            double[] riskFactors = {0.8, 1.4, 1.0, 2.0, 1.2, 1.7};
            double[] ecoFactors = {1.05, 1.12, 1.0, 1.25, 1.08, 1.18};

            // ---- Affectations ----
            DriverTruckAssignment[] assignments = new DriverTruckAssignment[6];
            for (int i = 0; i < 6; i++) {
                assignments[i] = new DriverTruckAssignment(null, demo, drivers[i], trucks[i],
                        today.minusYears(1), null, true);
                assignmentRepository.save(assignments[i]);
            }

            // ---- Trajets, événements, tachygraphe, carburant sur 35 jours ----
            LocalDate start = today.minusDays(35);
            LocalDate day = start;
            double[] kmPerTruck = new double[6];
            double[] litersPerTruck = new double[6];

            List<TachographDay>[] tachoByDriver = new List[6];
            for (int i = 0; i < 6; i++) tachoByDriver[i] = new ArrayList<>();

            while (!day.isAfter(today)) {
                for (int i = 0; i < 6; i++) {
                    double dayKm = 0;
                    double dayLiters = 0;
                    int nbTrips = (day.getDayOfWeek().getValue() >= 6) ? (rnd.nextBoolean() ? 1 : 0) : (1 + rnd.nextInt(2));
                    for (int k = 0; k < nbTrips; k++) {
                        double dist = 150 + rnd.nextDouble() * 650;
                        double hours = dist / (76 + rnd.nextDouble() * 14);
                        LocalDateTime st = day.atTime(5 + rnd.nextInt(4), rnd.nextInt(60));
                        LocalDateTime et = st.plusMinutes(Math.max(60, Math.round(hours * 60)));
                        boolean loaded = rnd.nextDouble() < 0.8;
                        boolean onTime = rnd.nextDouble() < 0.92;
                        Trip t = new Trip(null, demo, drivers[i], trucks[i], st, et,
                                null, null, dist, rnd.nextDouble() * 8 + 8, loaded,
                                Trip.TripStatus.TERMINE, onTime);
                        tripRepository.save(t);
                        double liters = dist / 100.0 * trucks[i].getExpectedConsumptionL100Km() * ecoFactors[i] * (0.92 + rnd.nextDouble() * 0.16);
                        dayKm += dist;
                        dayLiters += liters;
                        kmPerTruck[i] += dist;
                        litersPerTruck[i] += liters;
                    }

                    // Événements de conduite
                    int evCount = (int) (riskFactors[i] * (rnd.nextInt(3)));
                    for (int k = 0; k < evCount; k++) {
                        DrivingEvent.EventType[] types = {
                            DrivingEvent.EventType.FREINAGE_BRUSQUE,
                            DrivingEvent.EventType.ACCELERATION_FORTE,
                            DrivingEvent.EventType.EXCES_VITESSE,
                            DrivingEvent.EventType.RALENTI
                        };
                        DrivingEvent.EventType type = types[rnd.nextInt(types.length)];
                        LocalDateTime ts = day.atTime(6 + rnd.nextInt(12), rnd.nextInt(60));
                        double lat = trucks[i].getCurrentLatitude() + (rnd.nextDouble() - 0.5) * 4;
                        double lon = trucks[i].getCurrentLongitude() + (rnd.nextDouble() - 0.5) * 4;
                        DrivingEvent e = new DrivingEvent(null, demo, drivers[i], trucks[i], ts, type,
                                1 + rnd.nextInt(10),
                                type == DrivingEvent.EventType.EXCES_VITESSE ? 92 + rnd.nextDouble() * 18 : 60 + rnd.nextDouble() * 40,
                                type == DrivingEvent.EventType.RALENTI ? 300 + rnd.nextDouble() * 1800 : 2 + rnd.nextDouble() * 8,
                                lat, lon);
                        eventRepository.save(e);
                    }

                    // Tachygraphe (conduite + repos) : le verdict de conformité est
                    // calculé après la série complète par le moteur 561/2006.
                    double drivingHours;
                    double workHours;
                    double rest;
                    if (rnd.nextDouble() < 0.22) {
                        // Jour de repos hebdomadaire : pas de conduite.
                        drivingHours = 0;
                        workHours = 0.5 + rnd.nextDouble() * 1.5;
                        rest = 600 + rnd.nextDouble() * 180;
                    } else {
                        drivingHours = 6.5 + rnd.nextDouble() * 2.0;
                        workHours = drivingHours + rnd.nextDouble() * 3;
                        rest = 45 + rnd.nextDouble() * 45;
                        // Profil risqué : quelques dépassements de conduite.
                        if (riskFactors[i] > 1.6 && rnd.nextDouble() < 0.35) {
                            drivingHours = 10.5 + rnd.nextDouble();
                            workHours = drivingHours + 1.5 + rnd.nextDouble();
                            rest = rnd.nextDouble() * 25;
                        }
                    }
                    TachographDay tacho = new TachographDay(null, demo, drivers[i], day,
                            round(drivingHours, 1), round(workHours, 1),
                            round(rest, 0), false);
                    tachoByDriver[i].add(tacho);

                    // Carburant aligné sur le kilométrage du jour
                    if (dayKm > 0) {
                        double liters = dayLiters * (0.95 + rnd.nextDouble() * 0.1);
                        FuelRecord f = new FuelRecord(null, demo, trucks[i], day, round(liters, 1),
                                round(liters * (1.55 + rnd.nextDouble() * 0.2), 2), 0);
                        fuelRepository.save(f);
                    }
                }
                day = day.plusDays(1);
            }

            // ---- Conformité tachygraphe : évaluée par le moteur 561/2006 ----
            for (int i = 0; i < 6; i++) {
                for (TachographDay d : tachoByDriver[i]) {
                    d.setCompliant(tachographService.assess(d, tachoByDriver[i]).compliant());
                    tachoRepository.save(d);
                }
            }

            // ---- Maintenance ----
            for (int i = 0; i < 6; i++) {
                LocalDate mDay = start.minusDays(5);
                while (!mDay.isAfter(today.plusDays(30))) {
                    boolean planned = rnd.nextDouble() < 0.8;
                    boolean isRepair = !planned || rnd.nextDouble() < 0.25;
                    MaintenanceRecord.MaintenanceType type;
                    if (isRepair) type = MaintenanceRecord.MaintenanceType.REPARATION;
                    else {
                        MaintenanceRecord.MaintenanceType[] types = {
                            MaintenanceRecord.MaintenanceType.VIDANGE,
                            MaintenanceRecord.MaintenanceType.FREINS,
                            MaintenanceRecord.MaintenanceType.PNEUS,
                            MaintenanceRecord.MaintenanceType.REVISION,
                            MaintenanceRecord.MaintenanceType.CONTROLE_TECHNIQUE
                        };
                        type = types[rnd.nextInt(types.length)];
                    }
                    boolean overdue = i == 5 && rnd.nextDouble() < 0.5;
                    boolean done = !mDay.isAfter(today.minusDays(2)) && !(overdue && rnd.nextDouble() < 0.6);
                    MaintenanceRecord.MaintenanceStatus status = done
                            ? (rnd.nextDouble() < 0.9 ? MaintenanceRecord.MaintenanceStatus.REALISE : MaintenanceRecord.MaintenanceStatus.RETARDE)
                            : MaintenanceRecord.MaintenanceStatus.PLANIFIE;
                    LocalDate doneDate = done ? mDay.plusDays(status == MaintenanceRecord.MaintenanceStatus.RETARDE ? rnd.nextInt(8) + 3 : rnd.nextInt(3)) : null;
                    boolean doneOnTime = doneDate == null || !doneDate.isAfter(mDay.plusDays(2));
                    MaintenanceRecord m = new MaintenanceRecord(null, demo, trucks[i], mDay, doneDate,
                            type, planned, 150 + rnd.nextDouble() * 1200, doneOnTime, status);
                    maintenanceRepository.save(m);
                    mDay = mDay.plusDays(12 + rnd.nextInt(18));
                }
            }

            // ---- Coûts mensuels par couple ----
            for (int i = 0; i < 6; i++) {
                for (int m = 0; m < 3; m++) {
                    YearMonth ym = YearMonth.now().minusMonths(m);
                    double salary = 2700 + rnd.nextDouble() * 500;
                    double fuel = litersPerTruck[i] * 1.60 / 3.0;
                    double maint = 200 + rnd.nextDouble() * 400;
                    double assur = 400 + rnd.nextDouble() * 120;
                    double amort = 1450 + rnd.nextDouble() * 300;
                    double peages = 300 + rnd.nextDouble() * 500;
                    saveCost(costRepository, demo, trucks[i], drivers[i], ym, CostRecord.CostCategory.SALAIRE, salary);
                    saveCost(costRepository, demo, trucks[i], drivers[i], ym, CostRecord.CostCategory.CARBURANT, fuel);
                    saveCost(costRepository, demo, trucks[i], drivers[i], ym, CostRecord.CostCategory.MAINTENANCE, maint);
                    saveCost(costRepository, demo, trucks[i], drivers[i], ym, CostRecord.CostCategory.ASSURANCE, assur);
                    saveCost(costRepository, demo, trucks[i], drivers[i], ym, CostRecord.CostCategory.AMORTISSEMENT, amort);
                    saveCost(costRepository, demo, trucks[i], drivers[i], ym, CostRecord.CostCategory.PEAGES, peages);
                }
            }
        };
    }

    private Truck truck(Company company, Long id, String reg, String brand, String model, Integer year,
                        Truck.TruckType type, Truck.FuelType fuel, double capacity, double expected,
                        double lat, double lon, Truck.VehicleStatus status, double speed, String city) {
        return new Truck(id, company, reg, brand, model, year, type, fuel, capacity,
                LocalDate.now().minusYears(year > 2020 ? 3 : 4), 95000.0 + year * 0, expected,
                lat, lon, speed, status, LocalDateTime.now().minusMinutes(15), true);
    }

    private Driver driver(Company company, Long id, String first, String last, String license, String phone,
                          String email, LocalDate hire) {
        return new Driver(id, company, first, last, license, phone, email, hire, true);
    }

    private void saveCost(CostRecordRepository repo, Company company, Truck t, Driver d, YearMonth ym,
                          CostRecord.CostCategory cat, double amount) {
        repo.save(new CostRecord(null, company, t, d, ym, cat, round(amount, 2)));
    }

    private double round(double v, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(v * scale) / scale;
    }
}
