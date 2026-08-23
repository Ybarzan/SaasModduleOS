package com.incokalk.service.taric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.taric.TaricMeasureDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaricApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.taric.api.base-url:https://ec.europa.eu/taxation_customs/dds2/taric/api}")
    private String baseUrl;

    @Value("${incokalk.taric.api.key:}")
    private String apiKey;

    @Value("${incokalk.taric.cache.ttl-hours:24}")
    private int cacheTtlHours;

    @Value("${incokalk.taric.simulation-mode:true}")
    private boolean simulationMode;

    @Cacheable(value = "taric-rates", key = "#hsCode + ':' + #origin + ':' + #dest")
    public List<TaricMeasureDto> fetchRates(String hsCode, String origin, String dest) {
        if (simulationMode) {
            log.info("[TARIC] Mode simulation pour {} ({}) -> {}", hsCode, origin, dest);
            return simulateRates(hsCode, origin, dest);
        }
        return fetchFromApi(hsCode, origin, dest);
    }

    private List<TaricMeasureDto> fetchFromApi(String hsCode, String origin, String dest) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (apiKey != null && !apiKey.isBlank()) {
                headers.setBearerAuth(apiKey);
            }

            String url = String.format("%s/measures?code=%s&origin=%s&dest=%s",
                    baseUrl, hsCode, origin, dest);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseApiResponse(response.getBody(), hsCode, origin, dest);
            }
            log.warn("[TARIC] API returned {} for {} ({}->{})",
                    response.getStatusCode(), hsCode, origin, dest);
        } catch (Exception e) {
            log.error("[TARIC] API call failed pour {} ({}->{}): {}",
                    hsCode, origin, dest, e.getMessage());
        }
        return List.of();
    }

    private List<TaricMeasureDto> parseApiResponse(String json, String hsCode, String origin, String dest) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<TaricMeasureDto> rates = new ArrayList<>();

            JsonNode measures = root.isArray() ? root : root.path("measures");
            if (measures == null || !measures.isArray()) {
                return extractFromWcoResponse(root, hsCode, origin, dest);
            }

            for (JsonNode m : measures) {
                TaricMeasureDto dto = new TaricMeasureDto();
                dto.setHsCode(hsCode);
                dto.setOriginCountry(origin);
                dto.setDestinationCountry(dest);
                dto.setDescription(m.path("description").asText(""));
                dto.setDutyRate(m.path("dutyRate").asDouble(0));
                dto.setDutyType(m.path("dutyType").asText("AD"));
                dto.setPrefential(m.path("isPrefential").asBoolean(false));
                dto.setTradeAgreementCode(m.path("agreementCode").asText(null));
                dto.setAntiDumping(m.path("isAntiDumping").asBoolean(false));
                if (m.has("validFrom")) {
                    dto.setValidFrom(LocalDate.parse(m.path("validFrom").asText()));
                }
                if (m.has("validTo")) {
                    dto.setValidTo(LocalDate.parse(m.path("validTo").asText()));
                }
                rates.add(dto);
            }
            return rates;
        } catch (Exception e) {
            log.error("[TARIC] Erreur parsing API response: {}", e.getMessage());
            return List.of();
        }
    }

    private List<TaricMeasureDto> extractFromWcoResponse(JsonNode root, String hsCode, String origin, String dest) {
        List<TaricMeasureDto> rates = new ArrayList<>();
        try {
            if (root.has("dutyRate")) {
                TaricMeasureDto dto = new TaricMeasureDto();
                dto.setHsCode(hsCode);
                dto.setOriginCountry(origin);
                dto.setDestinationCountry(dest);
                dto.setDescription(root.path("description").asText(""));
                dto.setDutyRate(root.path("dutyRate").asDouble(0));
                dto.setDutyType(root.path("rateType").asText("AD"));
                if (root.has("preferential")) {
                    for (JsonNode pref : root.path("preferential")) {
                        TaricMeasureDto prefDto = new TaricMeasureDto();
                        prefDto.setHsCode(hsCode);
                        prefDto.setOriginCountry(origin);
                        prefDto.setDestinationCountry(dest);
                        prefDto.setPrefential(true);
                        prefDto.setTradeAgreementCode(pref.path("agreementCode").asText());
                        prefDto.setDutyRate(pref.path("rateNumeric").asDouble(0));
                        rates.add(prefDto);
                    }
                }
                rates.add(dto);
            }
        } catch (Exception e) {
            log.error("[TARIC] Erreur parsing WCO response: {}", e.getMessage());
        }
        return rates;
    }

    private List<TaricMeasureDto> simulateRates(String hsCode, String origin, String dest) {
        List<TaricMeasureDto> rates = new ArrayList<>();

        Map<String, Double> mfnRates = Map.ofEntries(
            Map.entry("01", 8.3), Map.entry("02", 12.8), Map.entry("03", 7.5),
            Map.entry("04", 10.9), Map.entry("05", 6.5), Map.entry("06", 8.0),
            Map.entry("07", 10.4), Map.entry("08", 8.5), Map.entry("09", 6.0),
            Map.entry("10", 12.0), Map.entry("11", 9.2), Map.entry("12", 5.7),
            Map.entry("13", 4.0), Map.entry("14", 3.5), Map.entry("15", 10.5),
            Map.entry("16", 13.5), Map.entry("17", 15.0), Map.entry("18", 7.0),
            Map.entry("19", 9.5), Map.entry("20", 11.0), Map.entry("21", 8.5),
            Map.entry("22", 6.5), Map.entry("23", 5.0), Map.entry("24", 57.0),
            Map.entry("25", 3.0), Map.entry("26", 0.0), Map.entry("27", 2.5),
            Map.entry("28", 5.5), Map.entry("29", 5.5), Map.entry("30", 0.0),
            Map.entry("31", 4.0), Map.entry("32", 6.5), Map.entry("33", 0.0),
            Map.entry("34", 4.5), Map.entry("35", 7.0), Map.entry("36", 6.5),
            Map.entry("37", 0.0), Map.entry("38", 6.0), Map.entry("39", 6.5),
            Map.entry("40", 3.5), Map.entry("41", 3.0), Map.entry("42", 8.0),
            Map.entry("43", 4.5), Map.entry("44", 3.0), Map.entry("45", 5.5),
            Map.entry("46", 4.0), Map.entry("47", 0.0), Map.entry("48", 1.5),
            Map.entry("49", 2.0), Map.entry("50", 5.0), Map.entry("51", 4.0),
            Map.entry("52", 7.0), Map.entry("53", 3.5), Map.entry("54", 8.0),
            Map.entry("55", 8.0), Map.entry("56", 5.0), Map.entry("57", 6.5),
            Map.entry("58", 6.5), Map.entry("59", 6.0), Map.entry("60", 9.0),
            Map.entry("61", 12.0), Map.entry("62", 12.0), Map.entry("63", 10.0),
            Map.entry("64", 17.0), Map.entry("65", 3.0), Map.entry("66", 3.5),
            Map.entry("67", 3.5), Map.entry("68", 2.5), Map.entry("69", 5.0),
            Map.entry("70", 5.0), Map.entry("71", 2.5), Map.entry("72", 2.0),
            Map.entry("73", 2.5), Map.entry("74", 3.5), Map.entry("75", 2.0),
            Map.entry("76", 5.0), Map.entry("78", 3.5), Map.entry("79", 3.5),
            Map.entry("80", 2.0), Map.entry("81", 2.5), Map.entry("82", 2.5),
            Map.entry("83", 2.5), Map.entry("84", 1.8), Map.entry("85", 1.4),
            Map.entry("86", 1.5), Map.entry("87", 6.5), Map.entry("88", 2.5),
            Map.entry("89", 2.5), Map.entry("90", 2.5), Map.entry("91", 4.0),
            Map.entry("92", 3.0), Map.entry("93", 0.0), Map.entry("94", 3.5),
            Map.entry("95", 4.5), Map.entry("96", 5.0), Map.entry("97", 0.0)
        );

        String chapter = hsCode.length() >= 2 ? hsCode.substring(0, 2) : "00";
        double mfnRate = mfnRates.getOrDefault(chapter, 3.5);

        TaricMeasureDto mfn = new TaricMeasureDto();
        mfn.setHsCode(hsCode);
        mfn.setDescription(String.format("Marchandises classées sous %s (taux MFN simulé)", hsCode));
        mfn.setOriginCountry(origin);
        mfn.setDestinationCountry(dest);
        mfn.setDutyRate(mfnRate);
        mfn.setDutyType("AD");
        mfn.setValidFrom(LocalDate.now().minusMonths(1));
        mfn.setValidTo(LocalDate.now().plusYears(1));
        rates.add(mfn);

        Map<String, Map<String, Double>> prefRates = Map.ofEntries(
            Map.entry("VN", Map.of("61", 2.5, "62", 2.5, "64", 7.0, "84", 0.0, "85", 0.0)),
            Map.entry("KR", Map.of("61", 0.0, "62", 0.0, "84", 0.0, "85", 0.0, "87", 0.0)),
            Map.entry("SG", Map.of("84", 0.0, "85", 0.0, "90", 0.0)),
            Map.entry("JP", Map.of("84", 0.0, "85", 0.0, "87", 0.0, "90", 0.0)),
            Map.entry("CA", Map.of("84", 0.0, "85", 0.0, "90", 0.0, "87", 0.0)),
            Map.entry("CL", Map.of("84", 0.0, "85", 0.0, "87", 0.0)),
            Map.entry("MX", Map.of("84", 0.0, "85", 0.0, "87", 0.0)),
            Map.entry("ZA", Map.of("61", 5.0, "84", 0.0, "85", 0.0)),
            Map.entry("TN", Map.of("61", 0.0, "62", 0.0, "84", 0.0, "85", 0.0))
        );

        Map<String, String> aggrCodes = Map.of(
            "VN", "EVFTA", "KR", "FTA", "SG", "EUSFTA",
            "JP", "EUJEPA", "CA", "CETA", "CL", "EUCLEA",
            "MX", "EUMXA", "ZA", "EUSADC", "TN", "EURO-MED"
        );

        if (prefRates.containsKey(origin)) {
            Map<String, Double> chapterPrefs = prefRates.get(origin);
            if (chapterPrefs.containsKey(chapter)) {
                TaricMeasureDto pref = new TaricMeasureDto();
                pref.setHsCode(hsCode);
                pref.setDescription(String.format("Taux préférentiel %s", aggrCodes.get(origin)));
                pref.setOriginCountry(origin);
                pref.setDestinationCountry(dest);
                pref.setDutyRate(chapterPrefs.get(chapter));
                pref.setDutyType("AD");
                pref.setPrefential(true);
                pref.setTradeAgreementCode(aggrCodes.get(origin));
                pref.setPrefentialOriginCriteria("CTH");
                pref.setValidFrom(LocalDate.now().minusMonths(1));
                pref.setValidTo(LocalDate.now().plusYears(1));
                rates.add(pref);
            }
        }

        return rates;
    }

    @Cacheable(value = "taric-hs-descriptions", key = "#hsCode")
    public String getDescription(String hsCode) {
        if (simulationMode || hsCode == null) return "";
        try {
            String url = String.format("%s/description?code=%s", baseUrl, hsCode);
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                JsonNode node = objectMapper.readTree(resp.getBody());
                return node.path("description").asText("");
            }
        } catch (Exception e) {
            log.debug("[TARIC] Description non trouvée pour {}", hsCode);
        }
        return "";
    }
}
