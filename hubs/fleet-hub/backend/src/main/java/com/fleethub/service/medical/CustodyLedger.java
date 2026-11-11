package com.fleethub.service.medical;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.model.AppUser;
import com.fleethub.model.CustodyEvent;
import com.fleethub.model.MedicalProduct;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.CustodyEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Journal de garde du transport médical : ajout seul, chaîné par empreinte
 * SHA-256. Chaque société a sa propre chaîne ; l'ajout est sérialisé par un
 * verrou d'écriture sur la société pour que deux passages simultanés ne se
 * disputent pas le même maillon.
 */
@Service
@RequiredArgsConstructor
public class CustodyLedger {

    static final String GENESIS = "0".repeat(64);

    private final CustodyEventRepository repository;
    private final CompanyRepository companyRepository;
    private final AppUserRepository userRepository;

    /** Contenu d'un événement à enregistrer (les champs facultatifs peuvent être nuls). */
    public record Entry(CustodyEvent.Type type, Tour tour, TourStop stop, LocalDateTime occurredAt,
                        Map<MedicalProduct, Integer> products, Double temperatureCelsius, String signedBy,
                        Double latitude, Double longitude, String details) {

        public static Entry of(CustodyEvent.Type type, Tour tour, String details) {
            return new Entry(type, tour, null, null, null, null, null, null, null, details);
        }
    }

    /** Ajoute un événement au journal de la société de la tournée. */
    @Transactional(propagation = Propagation.MANDATORY)
    public CustodyEvent append(Long companyId, Entry entry) {
        companyRepository.lockById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Société introuvable"));
        CustodyEvent last = repository.findTopByCompanyIdOrderBySequenceNoDesc(companyId).orElse(null);

        CustodyEvent e = new CustodyEvent();
        e.setCompanyId(companyId);
        e.setType(entry.type());
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        e.setRecordedAt(now);
        e.setOccurredAt(entry.occurredAt() != null ? entry.occurredAt().truncatedTo(ChronoUnit.SECONDS) : now);
        e.setActor(truncate(currentActor(), 255));
        if (entry.tour() != null) {
            e.setTourId(entry.tour().getId());
            e.setTourName(truncate(entry.tour().getName(), 255));
        }
        if (entry.stop() != null) {
            e.setTourStopId(entry.stop().getId());
            e.setSiteName(truncate(entry.stop().getSite().getName(), 255));
            e.setSiteKind(entry.stop().getSite().getKind().name());
        }
        if (entry.products() != null) {
            e.setProducts(MedicalProduct.format(entry.products()));
            e.setTotalQuantity(MedicalProduct.total(entry.products()));
        }
        e.setTemperatureCelsius(entry.temperatureCelsius());
        e.setSignedBy(truncate(entry.signedBy(), 255));
        e.setLatitude(entry.latitude());
        e.setLongitude(entry.longitude());
        e.setDetails(truncate(entry.details(), 1000));
        seal(e, last);
        return repository.save(e);
    }

    /**
     * Rattache un événement entièrement renseigné à la chaîne : numéro d'ordre,
     * empreinte du maillon précédent, puis sa propre empreinte. Les horodatages
     * doivent être à la seconde (stockage sans perte).
     */
    public static void seal(CustodyEvent e, CustodyEvent previous) {
        e.setSequenceNo(previous == null ? 1 : previous.getSequenceNo() + 1);
        e.setPreviousHash(previous == null ? GENESIS : previous.getHash());
        e.setHash(hash(e));
    }

    /** Résultat de la vérification d'intégrité de la chaîne. */
    public record Verification(boolean valid, long eventsChecked, Long firstInvalidSequence, String lastHash) {}

    /** Recalcule toute la chaîne : la moindre modification ou suppression est détectée. */
    @Transactional(readOnly = true)
    public Verification verify(Long companyId) {
        List<CustodyEvent> events = repository.findByCompanyIdOrderBySequenceNoAsc(companyId);
        String previous = GENESIS;
        long expectedSeq = 1;
        for (CustodyEvent e : events) {
            if (e.getSequenceNo() != expectedSeq || !previous.equals(e.getPreviousHash()) || !hash(e).equals(e.getHash())) {
                return new Verification(false, expectedSeq - 1, expectedSeq, previous);
            }
            previous = e.getHash();
            expectedSeq++;
        }
        return new Verification(true, events.size(), null, previous);
    }

    /** Empreinte SHA-256 du contenu de l'événement et du maillon précédent. */
    static String hash(CustodyEvent e) {
        String canonical = String.join("|",
                String.valueOf(e.getCompanyId()), String.valueOf(e.getSequenceNo()), e.getType().name(),
                str(e.getTourId()), str(e.getTourStopId()), str(e.getTourName()), str(e.getSiteName()), str(e.getSiteKind()),
                str(e.getOccurredAt()), str(e.getRecordedAt()), str(e.getActor()), str(e.getProducts()),
                str(e.getTotalQuantity()), str(e.getTemperatureCelsius()), str(e.getSignedBy()),
                str(e.getLatitude()), str(e.getLongitude()), str(e.getDetails()), e.getPreviousHash());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponible", ex);
        }
    }

    /** Utilisateur à l'origine de l'action : « Nom affiché (identifiant) », ou « système ». */
    public String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return "système";
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .map(AppUser::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name + " (" + username + ")")
                .orElse(username);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
