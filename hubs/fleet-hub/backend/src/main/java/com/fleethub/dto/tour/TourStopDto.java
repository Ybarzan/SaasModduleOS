package com.fleethub.dto.tour;

import com.fleethub.model.MedicalProduct;
import com.fleethub.model.Site;
import com.fleethub.model.TourStop;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record TourStopDto(
        Long id,
        int sequence,
        Long siteId,
        String siteName,
        String siteKind,
        String address,
        String city,
        Double latitude,
        Double longitude,
        String contactPhone,
        String type,
        LocalTime windowStart,
        LocalTime windowEnd,
        int serviceMinutes,
        LocalTime plannedArrival,
        Integer plannedLatenessMinutes,
        String status,
        LocalDateTime arrivedAt,
        LocalDateTime completedAt,
        String signedBy,
        Integer parcelCount,
        Integer sampleCount,
        Double temperatureCelsius,
        Double temperatureMinCelsius,
        Double temperatureMaxCelsius,
        boolean temperatureExcursion,
        String scannedCodes,
        String failureReason,
        String notes,
        Boolean onTime,
        Integer expectedQuantity,
        /** Dernier signataire connu sur ce site : suggestion pour éviter la saisie (application chauffeur). */
        String suggestedSigner,
        /** Activité transport médical : saisie des quantités par catégorie de produit. */
        boolean medical,
        /** Quantités saisies par catégorie (SANG, PCR, URINE, AUTRE) ; null hors transport médical. */
        Map<String, Integer> products,
        /** Remise au laboratoire : produits pris en charge plus tôt dans la tournée et pas encore remis. */
        Map<String, Integer> expectedProducts
) {
    public static TourStopDto of(TourStop s) {
        return of(s, null);
    }

    public static TourStopDto of(TourStop s, String suggestedSigner) {
        return of(s, suggestedSigner, null);
    }

    public static TourStopDto of(TourStop s, String suggestedSigner, Map<MedicalProduct, Integer> expectedProducts) {
        Site site = s.getSite();
        boolean medical = s.getCompany() != null && s.getCompany().isMedical();
        return new TourStopDto(s.getId(), s.getSequence(), site.getId(), site.getName(), site.getKind().name(),
                site.getAddress(), site.getCity(), site.getLatitude(), site.getLongitude(), site.getContactPhone(),
                s.getType().name(), s.getWindowStart(), s.getWindowEnd(), s.getServiceMinutes(),
                s.getPlannedArrival(), s.getPlannedLatenessMinutes(), s.getStatus().name(),
                s.getArrivedAt(), s.getCompletedAt(), s.getSignedBy(), s.getParcelCount(), s.getSampleCount(),
                s.getTemperatureCelsius(), s.getTemperatureMinCelsius(), s.getTemperatureMaxCelsius(),
                s.isTemperatureExcursion(), s.getScannedCodes(), s.getFailureReason(), s.getNotes(),
                s.deliveredOnTime(), s.getExpectedQuantity(), suggestedSigner,
                medical, medical ? names(s.getProducts()) : null,
                medical && expectedProducts != null ? names(expectedProducts) : null);
    }

    /** Catégories dans l'ordre de l'énumération (affichage stable). */
    static Map<String, Integer> names(Map<MedicalProduct, Integer> counts) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (MedicalProduct p : MedicalProduct.values()) {
            Integer v = counts.get(p);
            if (v != null && v > 0) {
                out.put(p.name(), v);
            }
        }
        return out;
    }
}
