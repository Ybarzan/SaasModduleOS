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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        service = new CustomsDutyService(taricRepo, agreementRepo, taricApiClient, taricSyncService);
    }

    @Test
    @DisplayName("Intra EU → droits 0")
    void calculate_intraEU() {
        var r = service.calculateDetailed("84713000", "FR", "DE", 1000, 100, 50);
        assertThat(r.dutyAmount()).isZero();
        assertThat(r.dutyType()).isEqualTo("NONE");
        assertThat(r.isPrefential()).isFalse();
    }

    @Test
    @DisplayName("CN→FR MFN → droits MFN")
    void calculate_mfn() {
        TaricRate mfn = new TaricRate();
        mfn.setDutyRate(3.5);
        when(taricRepo.findMFNRates(eq("84713000"), eq("CN"), eq("FR"), any(LocalDate.class)))
                .thenReturn(List.of(mfn));
        when(taricRepo.findPrefentialRates(eq("84713000"), eq("CN"), eq("FR"), any(LocalDate.class)))
                .thenReturn(List.of());

        var r = service.calculateDetailed("84713000", "CN", "FR", 1000, 100, 50);
        assertThat(r.dutyRate()).isEqualTo(3.5);
        assertThat(r.isPrefential()).isFalse();
        assertThat(r.dutyAmount()).isPositive();
    }

    @Test
    @DisplayName("MA→FR préférentiel → accord trouvé")
    void calculate_prefential() {
        TaricRate mfn = new TaricRate();
        mfn.setDutyRate(5.0);
        TaricRate pref = new TaricRate();
        pref.setDutyRate(0.0);
        pref.setTradeAgreementCode("DCFMA");
        pref.setPrefentialOriginCriteria("100%");

        when(taricRepo.findMFNRates(anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(List.of(mfn));
        when(taricRepo.findPrefentialRates(anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(List.of(pref));
        when(agreementRepo.findByCode("DCFMA")).thenReturn(Optional.of(
                TradeAgreement.builder().code("DCFMA").name("DCF Maroc").build()));

        var r = service.calculateDetailed("84713000", "MA", "FR", 1000, 100, 50);
        assertThat(r.isPrefential()).isTrue();
        assertThat(r.agreementCode()).isEqualTo("DCFMA");
        assertThat(r.dutyRate()).isZero();
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

    @Test
    @DisplayName("fallback rate pour HS 84")
    void calculate_fallback() {
        when(taricRepo.findMFNRates(anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(List.of());
        when(taricRepo.findPrefentialRates(anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(List.of());
        var r = service.calculateDetailed("84713000", "CN", "FR", 1000, 0, 0);
        assertThat(r.dutyRate()).isEqualTo(1.8);
    }
}
