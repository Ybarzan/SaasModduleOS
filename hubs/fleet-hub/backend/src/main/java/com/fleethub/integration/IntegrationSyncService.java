package com.fleethub.integration;

import com.fleethub.integration.dto.FuelTransactionDto;
import com.fleethub.integration.dto.GpsPositionDto;
import com.fleethub.integration.dto.TachographDayDto;
import com.fleethub.model.Driver;
import com.fleethub.model.FuelRecord;
import com.fleethub.model.TachographDay;
import com.fleethub.model.Truck;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.FuelRecordRepository;
import com.fleethub.repository.TachographDayRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.service.TachographService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IntegrationSyncService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationSyncService.class);

    private final IntegrationProperties props;
    private final ObjectProvider<TachographSource> tachographSource;
    private final ObjectProvider<GpsSource> gpsSource;
    private final ObjectProvider<CostSource> costSource;

    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;
    private final TachographDayRepository tachographRepository;
    private final FuelRecordRepository fuelRecordRepository;
    private final TachographService tachographService;

    // ---- Tâches planifiées (polling) ----

    @Scheduled(cron = "0 15 2 * * *")
    public void syncTachograph() {
        if (!props.getTacho().isEnabled()) return;
        TachographSource source = tachographSource.getIfAvailable();
        if (source == null) {
            log.warn("Aucun fournisseur tachygraphe trouvé pour provider '{}'", props.getTacho().getProvider());
            return;
        }
        LocalDate since = LocalDate.now().minusDays(props.getTacho().getSyncDaysBack());
        ingestTachographDays(source.fetchDrivingDays(since));
    }

    @Scheduled(fixedDelay = 60_000)
    public void syncGpsPositions() {
        if (!props.getGps().isEnabled()) return;
        GpsSource source = gpsSource.getIfAvailable();
        if (source == null) {
            log.warn("Aucun fournisseur GPS trouvé pour provider '{}'", props.getGps().getProvider());
            return;
        }
        ingestGpsPositions(source.fetchPositions());
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void syncFuelTransactions() {
        if (!props.getCost().isEnabled()) return;
        CostSource source = costSource.getIfAvailable();
        if (source == null) {
            log.warn("Aucun fournisseur carburant trouvé pour provider '{}'", props.getCost().getProvider());
            return;
        }
        LocalDate since = LocalDate.now().minusDays(props.getCost().getSyncDaysBack());
        ingestFuelTransactions(source.fetchTransactions(since));
    }

    // ---- Ingestion (push webhook ou polling) ----

    /** Enregistre les jours tachygraphe par clé métier {@code licenseNumber}. */
    @Transactional
    public int ingestTachographDays(List<TachographDayDto> data) {
        return ingestTachographDays(data, null);
    }

    /**
     * Variante tenant-scopée (webhook par société) : la jointure est limitée à
     * la société {@code companyId} pour éviter tout mélange inter-sociétés.
     */
    @Transactional
    public int ingestTachographDays(List<TachographDayDto> data, Long companyId) {
        int saved = 0;
        int skipped = 0;
        for (TachographDayDto dto : data) {
            Optional<Driver> driver = companyId != null
                    ? driverRepository.findByLicenseNumberAndCompanyId(dto.licenseNumber(), companyId)
                    : driverRepository.findByLicenseNumber(dto.licenseNumber());
            if (driver.isEmpty()) {
                skipped++;
                continue;
            }
            Optional<TachographDay> existing = tachographRepository.findByDriverIdAndDate(driver.get().getId(), dto.date());
            TachographDay day = existing.orElseGet(TachographDay::new);
            day.setCompany(driver.get().getCompany());
            day.setDriver(driver.get());
            day.setDate(dto.date());
            day.setDrivingHours(dto.drivingHours());
            day.setWorkHours(dto.workHours());
            day.setRestMinutes(dto.restMinutes());
            day.setCompliant(assess(driver.get(), day).compliant());
            tachographRepository.save(day);
            saved++;
        }
        log.info("Tachograph: {} enregistrement(s) synchronisés, {} ignoré(s)", saved, skipped);
        return saved;
    }

    /** Recalcule la conformité 561/2006 en tenant compte de l'historique du chauffeur. */
    private TachographService.Assessment assess(Driver driver, TachographDay day) {
        List<TachographDay> history = new ArrayList<>(tachographRepository
                .findByDriverAndDateBetween(driver, day.getDate().minusDays(13), day.getDate()));
        boolean present = history.stream()
                .anyMatch(x -> x.getId() != null && x.getId().equals(day.getId()));
        if (!present) history.add(day);
        return tachographService.assess(day, history);
    }

    /** Met à jour les positions GPS par clé métier {@code registration} (idempotent, update en place). */
    @Transactional
    public int ingestGpsPositions(List<GpsPositionDto> positions) {
        return ingestGpsPositions(positions, null);
    }

    /** Variante tenant-scopée (voir {@link #ingestTachographDays(List, Long)}). */
    @Transactional
    public int ingestGpsPositions(List<GpsPositionDto> positions, Long companyId) {
        int updated = 0;
        for (GpsPositionDto pos : positions) {
            Optional<Truck> truck = companyId != null
                    ? truckRepository.findByRegistrationAndCompanyId(pos.registration(), companyId)
                    : truckRepository.findByRegistration(pos.registration());
            if (truck.isEmpty()) continue;
            Truck t = truck.get();
            t.setCurrentLatitude(pos.latitude());
            t.setCurrentLongitude(pos.longitude());
            t.setCurrentSpeedKph(pos.speedKph());
            t.setCurrentStatus(pos.speedKph() > 0 ? Truck.VehicleStatus.ROULAGE : Truck.VehicleStatus.ARRET);
            t.setLastGpsUpdate(pos.timestamp());
            truckRepository.save(t);
            updated++;
        }
        log.info("GPS: {} véhicule(s) mis à jour", updated);
        return updated;
    }

    /** Enregistre les transactions carburant par clé métier {@code registration} (anti-doublon). */
    @Transactional
    public int ingestFuelTransactions(List<FuelTransactionDto> transactions) {
        return ingestFuelTransactions(transactions, null);
    }

    /** Variante tenant-scopée (voir {@link #ingestTachographDays(List, Long)}). */
    @Transactional
    public int ingestFuelTransactions(List<FuelTransactionDto> transactions, Long companyId) {
        int saved = 0;
        for (FuelTransactionDto tx : transactions) {
            Optional<Truck> truck = companyId != null
                    ? truckRepository.findByRegistrationAndCompanyId(tx.registration(), companyId)
                    : truckRepository.findByRegistration(tx.registration());
            if (truck.isEmpty()) continue;
            boolean exists = fuelRecordRepository.findByTruckIdAndDate(truck.get().getId(), tx.date()).stream()
                    .anyMatch(r -> Math.abs(r.getLiters() - tx.liters()) < 0.001
                            && Math.abs(r.getAmount() - tx.amount()) < 0.01);
            if (exists) continue;
            FuelRecord record = new FuelRecord();
            record.setCompany(truck.get().getCompany());
            record.setTruck(truck.get());
            record.setDate(tx.date());
            record.setLiters(tx.liters());
            record.setAmount(tx.amount());
            record.setOdometerKm(tx.odometerKm());
            fuelRecordRepository.save(record);
            saved++;
        }
        log.info("Carburant: {} transaction(s) synchronisée(s)", saved);
        return saved;
    }
}
