package com.fleethub.service;

import com.fleethub.model.Carrier;
import com.fleethub.model.Company;
import com.fleethub.model.EtaReportedSource;
import com.fleethub.model.EtaSourceReliability;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.EtaReportedSourceRepository;
import com.fleethub.repository.EtaSourceReliabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recalcule, chaque semaine, la fiabilité réelle (transporteur x lane x mode)
 * de l'ETA annoncée -- voir docs/03-plan-migration.md Phase 2 J3-J4. Repart de
 * zéro à chaque run (recalcul complet, pas d'incrément) : plus simple à
 * vérifier et insensible à un double comptage, le volume de données reste
 * modeste pour ce calcul.
 * <p>
 * Mélange les observations CARRIER_BOOKING (ETA annoncée au booking) et
 * AIS_BROADCAST (ETA diffusée par le navire suivi) dans une seule mesure par
 * groupe -- pas de ventilation par source (voir
 * EtaReportedSourceRepository.findScorable()).
 * <p>
 * L'écart mesuré est {@code actualDeliveryDate - reportedArrival} en jours :
 * positif = livré en retard par rapport à l'ETA annoncée, négatif = en
 * avance. Seules les observations avec une arrivée réelle ET une ETA annoncée
 * connue entrent dans le calcul (voir EtaReportedSourceRepository.findScorable).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EtaSourceReliabilityService {

    private final EtaReportedSourceRepository reportedSourceRepo;
    private final EtaSourceReliabilityRepository reliabilityRepo;

    @Scheduled(cron = "0 0 3 * * MON")
    public void recomputeWeekly() {
        int groups = recomputeAll();
        log.info("[EtaSourceReliability] Recalcul hebdomadaire terminé : {} groupe(s) transporteur×lane×mode", groups);
    }

    @Transactional
    public int recomputeAll() {
        List<EtaReportedSource> scorable = reportedSourceRepo.findScorable();

        Map<GroupKey, List<Long>> deviationsByGroup = new LinkedHashMap<>();
        Map<GroupKey, Company> companyByGroup = new LinkedHashMap<>();
        Map<GroupKey, Carrier> carrierByGroup = new LinkedHashMap<>();

        for (EtaReportedSource observation : scorable) {
            ShipmentOrder shipment = observation.getShipment();
            long deviationDays = ChronoUnit.DAYS.between(
                    observation.getReportedArrival(), shipment.getActualDeliveryDate());

            GroupKey key = new GroupKey(
                    observation.getCompany().getId(),
                    observation.getCarrier() != null ? observation.getCarrier().getId() : null,
                    observation.getOriginCountry(),
                    observation.getDestinationCountry(),
                    observation.getTransportMode());

            deviationsByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(deviationDays);
            companyByGroup.putIfAbsent(key, observation.getCompany());
            carrierByGroup.putIfAbsent(key, observation.getCarrier());
        }

        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<GroupKey, List<Long>> entry : deviationsByGroup.entrySet()) {
            GroupKey key = entry.getKey();
            List<Long> deviations = entry.getValue();

            EtaSourceReliability reliability = reliabilityRepo
                    .findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
                            key.companyId(), key.carrierId(), key.originCountry(), key.destinationCountry(), key.transportMode())
                    .orElseGet(() -> EtaSourceReliability.builder()
                            .company(companyByGroup.get(key))
                            .carrier(carrierByGroup.get(key))
                            .originCountry(key.originCountry())
                            .destinationCountry(key.destinationCountry())
                            .transportMode(key.transportMode())
                            .build());

            reliability.setSampleCount(deviations.size());
            reliability.setMeanDeviationDays(average(deviations));
            reliability.setMeanAbsoluteDeviationDays(averageAbsolute(deviations));
            reliability.setComputedAt(now);
            reliabilityRepo.save(reliability);
        }

        return deviationsByGroup.size();
    }

    private BigDecimal average(List<Long> values) {
        double avg = values.stream().mapToLong(Long::longValue).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageAbsolute(List<Long> values) {
        double avg = values.stream().mapToLong(v -> Math.abs(v)).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private record GroupKey(Long companyId, UUID carrierId, String originCountry, String destinationCountry, String transportMode) {}
}
