package com.fleethub.service.medical;

import com.fleethub.model.CustodyEvent;
import com.fleethub.model.MedicalProduct;
import com.fleethub.model.NonConformity;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;
import com.fleethub.repository.NonConformityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Traçabilité des tournées de transport médical, branchée sur le cycle de vie
 * des tournées : chaque action terrain alimente le journal de garde, et les
 * écarts (froid, quantités, délai, collecte manquée) ouvrent automatiquement
 * une non-conformité. Sans effet pour les autres activités.
 */
@Service
@RequiredArgsConstructor
public class MedicalTraceability {

    private final CustodyLedger ledger;
    private final NonConformityRepository ncRepository;

    /** Données terrain accompagnant une clôture de passage. */
    public record FieldData(Double latitude, Double longitude, String driverNote, String correctionReason) {
        public static final FieldData NONE = new FieldData(null, null, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onTourStarted(Tour tour, LocalDateTime at) {
        if (!medical(tour)) return;
        ledger.append(companyId(tour), new CustodyLedger.Entry(CustodyEvent.Type.TOURNEE_DEMARREE, tour, null, at,
                null, null, null, null, null, vehicleAndDriver(tour)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onArrived(Tour tour, TourStop stop, LocalDateTime at, Double latitude, Double longitude) {
        if (!medical(tour)) return;
        ledger.append(companyId(tour), new CustodyLedger.Entry(CustodyEvent.Type.ARRIVEE, tour, stop, at,
                null, null, null, latitude, longitude, null));
    }

    /** Clôture (ou correction) d'un passage : événement de garde + non-conformités du passage. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onStopCompleted(Tour tour, TourStop stop, boolean correction, FieldData field) {
        if (!medical(tour)) return;
        boolean failed = stop.getStatus() == TourStop.StopStatus.ECHEC;
        CustodyEvent.Type type = correction ? CustodyEvent.Type.CORRECTION
                : failed ? CustodyEvent.Type.ECHEC
                : stop.getType().isPickup() ? CustodyEvent.Type.PRISE_EN_CHARGE : CustodyEvent.Type.REMISE;
        StringBuilder details = new StringBuilder();
        if (correction) {
            details.append("Motif de correction : ").append(field.correctionReason().trim())
                    .append(" — nouveau résultat : ").append(failed ? "échec" : "réalisé");
        }
        if (failed && stop.getFailureReason() != null) {
            append(details, "Motif d'échec : " + stop.getFailureReason());
        }
        if (stop.isTemperatureExcursion()) {
            append(details, "Hors plage " + range(stop));
        }
        if (field.driverNote() != null && !field.driverNote().isBlank()) {
            append(details, "Commentaire : " + field.driverNote().trim());
        }
        ledger.append(companyId(tour), new CustodyLedger.Entry(type, tour, stop, stop.getCompletedAt(),
                failed ? null : stop.productCounts(), stop.getTemperatureCelsius(), stop.getSignedBy(),
                field.latitude(), field.longitude(), details.isEmpty() ? null : details.toString()));

        if (stop.isTemperatureExcursion()) {
            openOnce(tour, stop, NonConformity.Type.ECART_TEMPERATURE, NonConformity.Severity.MAJEURE,
                    "Température relevée " + stop.getTemperatureCelsius() + " °C à " + stop.getSite().getName()
                            + ", hors de la plage exigée " + range(stop) + " : rupture de la chaîne du froid.");
        }
        if (failed && stop.getType().isPickup()) {
            openOnce(tour, stop, NonConformity.Type.COLLECTE_NON_REALISEE, NonConformity.Severity.MINEURE,
                    "Collecte non réalisée à " + stop.getSite().getName() + " (" + stop.getFailureReason()
                            + ") : les produits restent sur site, prévenir le laboratoire.");
        }
    }

    /** Fin de tournée (terminée ou annulée) : clôture du journal et rapprochement collecté / remis. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onTourEnded(Tour tour, boolean cancelled) {
        if (!medical(tour)) return;
        Reconciliation r = Reconciliation.of(tour);
        String summary = "Collecté : " + r.collectedTotal() + " (" + MedicalProduct.describe(r.collected()) + ")"
                + " · Remis : " + r.deliveredTotal() + " (" + MedicalProduct.describe(r.delivered()) + ")";
        ledger.append(companyId(tour), new CustodyLedger.Entry(
                cancelled ? CustodyEvent.Type.TOURNEE_ANNULEE : CustodyEvent.Type.TOURNEE_TERMINEE, tour, null,
                cancelled ? LocalDateTime.now() : tour.getCompletedAt(), r.delivered(), null, null, null, null, summary));

        if (!r.balanced()) {
            openOnce(tour, null, NonConformity.Type.ECART_QUANTITE, NonConformity.Severity.CRITIQUE,
                    "Écart entre produits collectés et remis" + (cancelled ? " (tournée annulée)" : "") + " — "
                            + r.describeGap() + ". Retrouver les produits et informer le laboratoire.");
        }
        List<Reconciliation.Transit> late = r.transits().stream().filter(Reconciliation.Transit::exceeded).toList();
        if (!late.isEmpty()) {
            openOnce(tour, null, NonConformity.Type.DELAI_ACHEMINEMENT, NonConformity.Severity.MAJEURE,
                    "Délai d'acheminement supérieur à " + r.transitLimitMinutes() + " min : "
                            + late.stream().map(t -> t.siteName() + " (" + t.minutes() + " min)")
                            .collect(Collectors.joining(", ")) + ".");
        }
    }

    /** Ouvre une non-conformité et la consigne au journal de garde. */
    @Transactional(propagation = Propagation.MANDATORY)
    public NonConformity open(Long companyId, Tour tour, TourStop stop, NonConformity.Type type,
                              NonConformity.Severity severity, String description, boolean automatic) {
        NonConformity nc = new NonConformity();
        nc.setCompanyId(companyId);
        if (tour != null) {
            nc.setTourId(tour.getId());
            nc.setTourName(tour.getName());
            nc.setTourDate(tour.getDate());
        }
        if (stop != null) {
            nc.setTourStopId(stop.getId());
            nc.setSiteName(stop.getSite().getName());
        }
        nc.setType(type);
        nc.setSeverity(severity);
        nc.setStatus(NonConformity.Status.OUVERTE);
        nc.setAutomatic(automatic);
        nc.setDescription(description.length() > 1000 ? description.substring(0, 1000) : description);
        nc.setDetectedAt(LocalDateTime.now());
        nc.setDetectedBy(automatic ? "système" : ledger.currentActor());
        NonConformity saved = ncRepository.save(nc);
        ledger.append(companyId, new CustodyLedger.Entry(CustodyEvent.Type.NC_OUVERTE, tour, stop, null,
                null, null, null, null, null,
                "NC #" + saved.getId() + " " + type.name() + " (" + severity.name().toLowerCase() + ") : " + saved.getDescription()));
        return saved;
    }

    private void openOnce(Tour tour, TourStop stop, NonConformity.Type type, NonConformity.Severity severity,
                          String description) {
        Long companyId = companyId(tour);
        boolean exists = stop != null
                ? ncRepository.existsByCompanyIdAndTourIdAndTourStopIdAndType(companyId, tour.getId(), stop.getId(), type)
                : ncRepository.existsByCompanyIdAndTourIdAndType(companyId, tour.getId(), type);
        if (!exists) {
            open(companyId, tour, stop, type, severity, description, true);
        }
    }

    private static boolean medical(Tour tour) {
        return tour.getCompany() != null && tour.getCompany().isMedical();
    }

    private static Long companyId(Tour tour) {
        return tour.getCompany().getId();
    }

    private static String vehicleAndDriver(Tour tour) {
        String driver = tour.getDriver() != null ? tour.getDriver().getFirstName() + " " + tour.getDriver().getLastName() : "non affecté";
        String truck = tour.getTruck() != null ? tour.getTruck().getRegistration() : "non affecté";
        return "Chauffeur : " + driver + " · Véhicule : " + truck;
    }

    private static String range(TourStop s) {
        return (s.getTemperatureMinCelsius() != null ? s.getTemperatureMinCelsius() : "…") + " à "
                + (s.getTemperatureMaxCelsius() != null ? s.getTemperatureMaxCelsius() : "…") + " °C";
    }

    private static void append(StringBuilder sb, String part) {
        if (!sb.isEmpty()) sb.append(" · ");
        sb.append(part);
    }
}
