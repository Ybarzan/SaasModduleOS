package com.incokalk.service;

import com.incokalk.model.TaricRate;
import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.TaricRateRepository;
import com.incokalk.repository.TradeAgreementRepository;
import com.incokalk.service.taric.TaricApiClient;
import com.incokalk.service.taric.TaricSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CustomsDutyService — Tests unitaires")
class CustomsDutyServiceTest {

    TaricRateRepository taricRepo;
    TradeAgreementRepository agreementRepo;
    TaricApiClient taricApiClient;
    TaricSyncService taricSyncService;
    CustomsDutyService service;

    @BeforeEach
    void setUp() {
        taricRepo = mock(TaricRateRepository.class);
        agreementRepo = mock(TradeAgreementRepository.class);
        taricApiClient = mock(TaricApiClient.class);
        taricSyncService = mock(TaricSyncService.class);
        when(taricApiClient.isSimulationMode()).thenReturn(true);
        when(taricRepo.findRateCandidates(anyList(), anyList(), anyCollection(), anyBoolean(), any(LocalDate.class)))
                .thenReturn(List.of());
        service = new CustomsDutyService(taricRepo, agreementRepo, taricApiClient, taricSyncService);
    }

    private static TaricRate rate(String hs, String origin, double pct, String type) {
        TaricRate r = new TaricRate();
        r.setHsCode(hs);
        r.setOriginCountry(origin);
        r.setDestinationCountry("FR");
        r.setDutyRate(pct);
        r.setDutyType(type);
        return r;
    }

    private void mfnRows(TaricRate... rows) {
        when(taricRepo.findRateCandidates(anyList(), anyList(), anyCollection(), eq(false), any(LocalDate.class)))
                .thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("Intra UE → droits 0")
    void calculate_intraEU() {
        var r = service.calculateDetailed("84713000", "FR", "DE", 1000, 100, 50);
        assertThat(r.dutyAmount()).isZero();
        assertThat(r.dutyType()).isEqualTo("NONE");
        assertThat(r.rateAvailable()).isTrue();
    }

    @Test
    @DisplayName("CN→FR : taux TARIC trouvé → montant = CIF × taux %")
    void calculate_mfn() {
        mfnRows(rate("847130", "CN", 3.5, "AD"));
        var r = service.calculateDetailed("84713000", "CN", "FR", 1000, 100, 50);
        assertThat(r.rateAvailable()).isTrue();
        assertThat(r.dutyRate()).isEqualTo(3.5);
        assertThat(r.dutyAmount()).isEqualTo(40.25); // 1150 × 3,5 %
        assertThat(r.basisType()).isEqualTo(CustomsDutyService.BASIS_CIF_EU);
        assertThat(r.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("MA→FR : taux préférentiel de l'accord appliqué")
    void calculate_prefential() {
        mfnRows(rate("847130", "XX", 5.0, "AD"));
        TaricRate pref = rate("847130", "MA", 0.0, "AD");
        pref.setPrefential(true);
        pref.setTradeAgreementCode("DCFMA");
        pref.setPrefentialOriginCriteria("100%");
        when(taricRepo.findRateCandidates(anyList(), anyList(), anyCollection(), eq(true), any(LocalDate.class)))
                .thenReturn(List.of(pref));
        when(agreementRepo.findByCode("DCFMA")).thenReturn(Optional.of(
                TradeAgreement.builder().code("DCFMA").name("DCF Maroc").build()));

        var r = service.calculateDetailed("84713000", "MA", "FR", 1000, 100, 50);
        assertThat(r.isPrefential()).isTrue();
        assertThat(r.agreementCode()).isEqualTo("DCFMA");
        assertThat(r.dutyRate()).isZero();
        assertThat(r.savings()).isEqualTo(57.5);
    }

    // ── P0-1 : plus aucun taux inventé, sur aucun chapitre ─────────────────────

    static Stream<String> allChapters() {
        return IntStream.rangeClosed(1, 97).mapToObj(c -> String.format("%02d", c) + "0100");
    }

    @ParameterizedTest(name = "chapitre {0} sans donnée TARIC → indisponible, jamais un taux moyen")
    @MethodSource("allChapters")
    void noTaricData_anyChapter_isUnavailable(String hs) {
        var r = service.calculateDetailed(hs, "CN", "FR", 10_000, 0, 0);
        assertThat(r.rateAvailable()).isFalse();
        assertThat(r.dutyAmount()).isZero();
        assertThat(r.dutyRate()).isZero();
        assertThat(r.basisType()).isNull();
        assertThat(r.notes()).contains("non disponibles");
    }

    @ParameterizedTest(name = "échelle : taux stocké {0} → {0} % de la valeur CIF")
    @ValueSource(doubles = {0.0, 1.8, 3.5, 12.0, 57.0, 100.0})
    void storedRateIsPercentagePoints(double pct) {
        mfnRows(rate("847130", "XX", pct, "AD"));
        var r = service.calculateDetailed("847130", "CN", "FR", 10_000, 0, 0);
        assertThat(r.rateAvailable()).isTrue();
        // 1.8 doit donner 180 € sur 10 000 € (1,8 %), jamais 18 000 € (180 %).
        assertThat(r.dutyAmount()).isCloseTo(10_000 * pct / 100.0, within(0.01));
    }

    // ── P0-2 / B3-B4 : l'axe réel est utilisé, pas ("CN","FR") codé en dur ───────

    @Test
    @DisplayName("Destinations hors UE (FR→US, FR→CN) : pas de tarif TARIC appliqué")
    void nonEuDestination_isUnavailable() {
        mfnRows(rate("847130", "XX", 1.8, "AD")); // ne doit PAS être utilisé
        for (String dest : List.of("US", "CN", "GB", "NO", "CH")) {
            var r = service.calculateDetailed("847130", "FR", dest, 10_000, 0, 0);
            assertThat(r.rateAvailable()).as(dest).isFalse();
            assertThat(r.notes()).as(dest).contains(dest);
        }
        verify(taricRepo, never()).findRateCandidates(anyList(), anyList(), anyCollection(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Origines différentes → recherche faite avec l'origine réelle (+ erga omnes)")
    void realOriginIsPropagated() {
        when(taricRepo.findRateCandidates(anyList(), eq(List.of("CN", "XX")), anyCollection(), eq(false), any()))
                .thenReturn(List.of(rate("847130", "CN", 6.5, "AD")));
        when(taricRepo.findRateCandidates(anyList(), eq(List.of("US", "XX")), anyCollection(), eq(false), any()))
                .thenReturn(List.of(rate("847130", "XX", 0.0, "AD")));

        var fromCn = service.calculateDetailed("847130", "CN", "FR", 10_000, 0, 0);
        var fromUs = service.calculateDetailed("847130", "US", "FR", 10_000, 0, 0);

        assertThat(fromCn.dutyAmount()).isEqualTo(650.0);
        assertThat(fromUs.dutyAmount()).isZero();
        assertThat(fromUs.rateAvailable()).isTrue();
    }

    @Test
    @DisplayName("Recherche hiérarchique : le code le plus précis l'emporte, origine spécifique avant erga omnes")
    void longestPrefixThenSpecificOrigin() {
        mfnRows(rate("8471", "CN", 9.9, "AD"),
                rate("847130", "XX", 0.0, "AD"),
                rate("847130", "CN", 2.0, "AD"));
        var r = service.calculateDetailed("8471.30.00", "CN", "FR", 1000, 0, 0);
        assertThat(r.dutyRate()).isEqualTo(2.0);
        verify(taricRepo).findRateCandidates(eq(List.of("84713000", "8471300", "847130", "84713", "8471")),
                eq(List.of("CN", "XX")), anyCollection(), eq(false), any());
    }

    @Test
    @DisplayName("Droit spécifique (SD) ou taux > 100 % : refusé, jamais appliqué comme un pourcentage")
    void specificOrImplausibleDuty_isUnavailable() {
        mfnRows(rate("1701", "BR", 339.0, "SD"));
        assertThat(service.calculateDetailed("1701", "BR", "FR", 10_000, 0, 0).rateAvailable()).isFalse();

        mfnRows(rate("100630", "XX", 175.0, "AD"));
        var rice = service.calculateDetailed("100630", "IN", "FR", 10_000, 0, 0);
        assertThat(rice.rateAvailable()).isFalse();
        assertThat(rice.dutyAmount()).isZero();
    }

    @Test
    @DisplayName("Code HS absent ou trop court → indisponible (plus de 3,5 % par défaut)")
    void missingHsCode_isUnavailable() {
        assertThat(service.calculateDetailed(null, "CN", "FR", 1000, 0, 0).rateAvailable()).isFalse();
        assertThat(service.calculateDetailed("84", "CN", "FR", 1000, 0, 0).rateAvailable()).isFalse();
    }

    @Test
    @DisplayName("Mode simulation : aucun appel à la source simulée, rien n'est persisté")
    void simulationMode_neverFetchesNorPersists() {
        service.calculateDetailed("847130", "CN", "FR", 1000, 0, 0);
        verify(taricApiClient, never()).fetchRates(anyString(), anyString(), anyString());
        verify(taricSyncService, never()).saveRates(anyList());
    }

    @Test
    @DisplayName("findRate : NaN si indisponible (jamais un taux inventé)")
    void findRate_unavailable_isNaN() {
        assertThat(service.findRate("847130", "CN", "FR")).isNaN();
        mfnRows(rate("847130", "XX", 0.0, "AD"));
        assertThat(service.findRate("847130", "CN", "FR")).isZero();
    }

    @Test
    @DisplayName("getEUAgreement → trouvé")
    void getEUAgreement() {
        when(agreementRepo.findByPartnerCountryAndIsActiveTrue("MA"))
                .thenReturn(List.of(TradeAgreement.builder().name("DCF Maroc").build()));
        assertThat(service.getEUAgreement("MA")).isEqualTo("DCF Maroc");
    }

    @Test
    @DisplayName("getEUAgreement → pas trouvé")
    void getEUAgreement_notFound() {
        when(agreementRepo.findByPartnerCountryAndIsActiveTrue("XX"))
                .thenReturn(List.of());
        assertThat(service.getEUAgreement("XX")).isNull();
    }

    @Test
    @DisplayName("isEU → true/false")
    void isEU() {
        assertThat(service.isEU("FR")).isTrue();
        assertThat(service.isEU("CN")).isFalse();
    }

    @Test
    @DisplayName("isIntraEU → true/false")
    void isIntraEU() {
        assertThat(service.isIntraEU("FR", "DE")).isTrue();
        assertThat(service.isIntraEU("CN", "FR")).isFalse();
    }

    @Test
    @DisplayName("getEUCountries → contient FR")
    void getEUCountries() {
        assertThat(service.getEUCountries()).contains("FR");
    }
}
