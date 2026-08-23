package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.DebDeclarationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("DebAutoGenerationService — Tests unitaires")
class DebAutoGenerationServiceTest {

    DebDeclarationRepository debRepo;
    CompanyRepository companyRepo;
    DebAutoGenerationService service;
    UUID companyId;
    Company company;

    @BeforeEach
    void setUp() {
        debRepo = mock(DebDeclarationRepository.class);
        companyRepo = mock(CompanyRepository.class);
        service = new DebAutoGenerationService(debRepo, companyRepo);
        companyId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
    }

    @Test
    @DisplayName("generateDebFromShipment → crée DEB IMPORT")
    void generateDebFromShipment_import() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        var data = new DebAutoGenerationService.ShipmentData(
                companyId, "IMPORT", "CN", "FR", "CN",
                "84713000", "Machines", BigDecimal.valueOf(500),
                BigDecimal.valueOf(10000), "PCE", "SEA", "4100", "FR12345678901", 202607);
        var result = service.generateDebFromShipment(data, companyId);
        assertThat(result.status()).isEqualTo(com.incokalk.model.DebDeclaration.DebStatus.DRAFT);
        assertThat(result.referenceNumber()).startsWith("DEB-IMP-");
        assertThat(result.message()).contains("auto-generated");
    }

    @Test
    @DisplayName("generateDebFromShipment → ignore le companyId du corps, utilise celui de l'appelant")
    void generateDebFromShipment_ignoresBodyCompanyId() {
        UUID attackerTargetCompanyId = UUID.randomUUID();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        var data = new DebAutoGenerationService.ShipmentData(
                attackerTargetCompanyId, "IMPORT", "CN", "FR", "CN",
                "84713000", "Machines", BigDecimal.valueOf(500),
                BigDecimal.valueOf(10000), "PCE", "SEA", "4100", "FR12345678901", 202607);

        service.generateDebFromShipment(data, companyId);

        // La DEB doit être rattachée à companyId (appelant), jamais à attackerTargetCompanyId (corps).
        verify(companyRepo).findById(companyId);
        verify(companyRepo, never()).findById(attackerTargetCompanyId);
    }

    @Test
    @DisplayName("generateDebFromShipment → société non trouvée")
    void generateDebFromShipment_companyNotFound() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
        var data = new DebAutoGenerationService.ShipmentData(
                companyId, "EXPORT", "FR", "US", "US",
                "84713000", "Machines", BigDecimal.valueOf(200),
                BigDecimal.valueOf(5000), "PCE", "AIR", "7100", "FR12345678901", 202607);
        assertThatThrownBy(() -> service.generateDebFromShipment(data, companyId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateBulkDeb → crée uniquement les ship du bon mois")
    void generateBulkDeb() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        var s1 = new DebAutoGenerationService.ShipmentData(
                companyId, "IMPORT", "CN", "FR", "CN",
                "84713000", "M1", BigDecimal.ONE, BigDecimal.TEN,
                "PCE", "SEA", "4100", "FR1", 202607);
        var s2 = new DebAutoGenerationService.ShipmentData(
                companyId, "IMPORT", "US", "FR", "US",
                "84713000", "M2", BigDecimal.ONE, BigDecimal.TEN,
                "PCE", "AIR", "4100", "FR2", 202606);

        var result = service.generateBulkDeb(companyId, List.of(s1, s2), 202607);
        assertThat(result.message()).contains("1 DEB");
        verify(debRepo, times(1)).save(any());
    }

    @Test
    @DisplayName("calculateStatisticalValue → calcul")
    void calculateStatisticalValue() {
        BigDecimal val = service.calculateStatisticalValue(BigDecimal.TEN, BigDecimal.valueOf(150), 5);
        assertThat(val).isEqualByComparingTo(new BigDecimal("750.00"));
    }

    @Test
    @DisplayName("calculateStatisticalValue → quantité 0 → 0")
    void calculateStatisticalValue_zero() {
        assertThat(service.calculateStatisticalValue(BigDecimal.TEN, BigDecimal.valueOf(150), 0))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("isCompulsoryDeb → GB seuil 1500")
    void isCompulsoryDeb_gb() {
        assertThat(service.isCompulsoryDeb(new BigDecimal("2000"), "GB")).isTrue();
        assertThat(service.isCompulsoryDeb(new BigDecimal("1000"), "GB")).isFalse();
    }

    @Test
    @DisplayName("isCompulsoryDeb → autre pays seuil 25000")
    void isCompulsoryDeb_other() {
        assertThat(service.isCompulsoryDeb(new BigDecimal("30000"), "CN")).isTrue();
        assertThat(service.isCompulsoryDeb(new BigDecimal("20000"), "CN")).isFalse();
    }
}
