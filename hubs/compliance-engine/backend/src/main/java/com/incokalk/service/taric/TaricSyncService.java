package com.incokalk.service.taric;

import com.incokalk.dto.taric.TaricMeasureDto;
import com.incokalk.model.TaricRate;
import com.incokalk.repository.TaricRateRepository;
import com.incokalk.scheduling.DistributedJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaricSyncService {

    private final TaricApiClient taricApiClient;
    private final TaricRateRepository taricRepo;
    private final DistributedJobLock jobLock;

    @Value("${incokalk.taric.sync.enabled:false}")
    private boolean syncEnabled;

    @Value("${incokalk.taric.sync.batch-size:50}")
    private int batchSize;

    private static final List<String> PRIORITY_HS = List.of(
        "84713000", "84714100", "84714900", "84715000",
        "85235110", "85235191", "85235199",
        "61091000", "61099020", "61099030",
        "62034211", "62034231", "62046211",
        "64039911", "64039931", "64039950",
        "87032311", "87032319", "87033211",
        "90181100", "90181200", "90181300",
        "39269097", "39261000", "39269092",
        "20091911", "20091919", "20091991",
        "22042110", "22042111", "22042112",
        "30049000", "30021500", "30021600"
    );

    private static final List<String> PRIORITY_ORIGINS = List.of(
        "CN", "US", "JP", "KR", "VN", "IN", "SG", "MY", "TH", "TW",
        "CH", "NO", "GB", "CA", "MX", "BR", "ZA", "TN", "MA", "TR"
    );

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void syncDaily() {
        if (!syncEnabled) {
            log.info("[TARIC] Sync désactivé (incokalk.taric.sync.enabled=false)");
            return;
        }
        jobLock.runExclusively("taric-sync-daily", Duration.ofHours(1), () -> {
            log.info("[TARIC] Début synchronisation quotidienne");
            int total = 0;

            for (String hsCode : PRIORITY_HS) {
                for (String origin : PRIORITY_ORIGINS) {
                    try {
                        List<TaricMeasureDto> rates = taricApiClient.fetchRates(hsCode, origin, "FR");
                        for (TaricMeasureDto dto : rates) {
                            upsertRate(dto);
                            total++;
                        }
                    } catch (Exception e) {
                        log.warn("[TARIC] Erreur sync {} ({}->FR): {}", hsCode, origin, e.getMessage());
                    }
                }
            }

            log.info("[TARIC] Synchronisation terminée: {} taux mis à jour", total);
        });
    }

    @Transactional
    public List<TaricMeasureDto> refreshHsCode(String hsCode, String origin, String dest) {
        log.info("[TARIC] Rafraîchissement {} ({}->{})", hsCode, origin, dest);
        List<TaricMeasureDto> rates = taricApiClient.fetchRates(hsCode, origin, dest);
        saveRates(rates);
        return rates;
    }

    @Transactional
    public void saveRates(List<TaricMeasureDto> rates) {
        for (TaricMeasureDto dto : rates) {
            upsertRate(dto);
        }
    }

    private void upsertRate(TaricMeasureDto dto) {
        Optional<TaricRate> existing = taricRepo
            .findFirstByHsCodeAndOriginCountryAndDestinationCountryAndIsPrefentialFalse(
                dto.getHsCode(), dto.getOriginCountry(), dto.getDestinationCountry());

        TaricRate rate;
        if (existing.isPresent() && !dto.isPrefential()) {
            rate = existing.get();
        } else if (dto.isPrefential()) {
            rate = new TaricRate();
            rate.setPrefential(true);
            rate.setTradeAgreementCode(dto.getTradeAgreementCode());
        } else {
            rate = new TaricRate();
        }

        rate.setHsCode(dto.getHsCode());
        rate.setDescription(dto.getDescription());
        rate.setOriginCountry(dto.getOriginCountry());
        rate.setDestinationCountry(dto.getDestinationCountry());
        rate.setDutyRate(dto.getDutyRate());
        rate.setDutyType(dto.getDutyType() != null ? dto.getDutyType() : "AD");
        rate.setSpecificAmount(dto.getSpecificAmount());
        rate.setSpecificUnit(dto.getSpecificUnit());
        rate.setPrefentialOriginCriteria(dto.getPrefentialOriginCriteria());
        rate.setAntiDumping(dto.isAntiDumping());
        rate.setAntiDumpingDuty(dto.getAntiDumpingDuty());
        rate.setValidFrom(dto.getValidFrom() != null ? dto.getValidFrom() : LocalDate.now().minusMonths(1));
        rate.setValidTo(dto.getValidTo() != null ? dto.getValidTo() : LocalDate.now().plusYears(1));
        rate.setNotes(dto.getNotes());

        if (rate.getSearchText() == null || rate.getSearchText().isBlank()) {
            String normalized = normalizeText(dto.getDescription());
            rate.setSearchText(normalized);
        }

        taricRepo.save(rate);
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        String normalized = text.toLowerCase().trim();
        StringBuilder sb = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            switch (c) {
                case 'à', 'â', 'ä' -> sb.append('a');
                case 'é', 'è', 'ê', 'ë' -> sb.append('e');
                case 'î', 'ï' -> sb.append('i');
                case 'ô', 'ö' -> sb.append('o');
                case 'ù', 'û', 'ü' -> sb.append('u');
                case 'ç' -> sb.append('c');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public long getTotalRates() {
        return taricRepo.count();
    }

    @Transactional(readOnly = true)
    public long getDistinctHsCount() {
        return taricRepo.findDistinctHsCodesWithDescriptions().size();
    }
}
