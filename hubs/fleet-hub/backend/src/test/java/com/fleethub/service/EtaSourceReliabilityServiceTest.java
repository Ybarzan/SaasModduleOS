package com.fleethub.service;

import com.fleethub.model.*;
import com.fleethub.repository.EtaReportedSourceRepository;
import com.fleethub.repository.EtaSourceReliabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EtaSourceReliabilityService — recomputeAll")
class EtaSourceReliabilityServiceTest {

    EtaReportedSourceRepository reportedSourceRepo;
    EtaSourceReliabilityRepository reliabilityRepo;
    EtaSourceReliabilityService service;

    Company company;
    Carrier carrier;

    @BeforeEach
    void setUp() {
        reportedSourceRepo = mock(EtaReportedSourceRepository.class);
        reliabilityRepo = mock(EtaSourceReliabilityRepository.class);
        service = new EtaSourceReliabilityService(reportedSourceRepo, reliabilityRepo);

        company = new Company();
        company.setId(7L);
        carrier = Carrier.builder().id(UUID.randomUUID()).name("DHL").code("DHL").build();

        when(reliabilityRepo.findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(reliabilityRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private EtaReportedSource observation(String originCountry, String destCountry, String mode,
                                            LocalDateTime reportedArrival, LocalDateTime actualDeliveryDate) {
        ShipmentOrder shipment = ShipmentOrder.builder().id(UUID.randomUUID()).actualDeliveryDate(actualDeliveryDate).build();
        return EtaReportedSource.builder()
                .id(UUID.randomUUID()).company(company).carrier(carrier).shipment(shipment)
                .source(EtaReportedSource.Source.CARRIER_BOOKING)
                .originCountry(originCountry).destinationCountry(destCountry).transportMode(mode)
                .reportedArrival(reportedArrival)
                .build();
    }

    @Test
    @DisplayName("livraisons en retard et à l'heure dans le même groupe → biais et amplitude moyens corrects")
    void recomputeAll_mixedDeviations_computesCorrectMeans() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<EtaReportedSource> observations = List.of(
                observation("CHN", "DEU", "SEA", base, base.plusDays(2)),  // +2j (retard)
                observation("CHN", "DEU", "SEA", base, base.minusDays(1)), // -1j (avance)
                observation("CHN", "DEU", "SEA", base, base)               // 0j (pile à l'heure)
        );
        when(reportedSourceRepo.findScorable(EtaReportedSource.Source.CARRIER_BOOKING)).thenReturn(observations);

        int groups = service.recomputeAll();

        assertThat(groups).isEqualTo(1);
        ArgumentCaptor<EtaSourceReliability> captor = ArgumentCaptor.forClass(EtaSourceReliability.class);
        verify(reliabilityRepo).save(captor.capture());
        EtaSourceReliability saved = captor.getValue();

        assertThat(saved.getSampleCount()).isEqualTo(3);
        // moyenne signée (2 - 1 + 0) / 3 = 0.33
        assertThat(saved.getMeanDeviationDays().doubleValue()).isCloseTo(0.33, org.assertj.core.data.Offset.offset(0.01));
        // moyenne absolue (2 + 1 + 0) / 3 = 1.00
        assertThat(saved.getMeanAbsoluteDeviationDays().doubleValue()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(saved.getOriginCountry()).isEqualTo("CHN");
        assertThat(saved.getDestinationCountry()).isEqualTo("DEU");
        assertThat(saved.getTransportMode()).isEqualTo("SEA");
    }

    @Test
    @DisplayName("deux groupes distincts (transporteurs différents) → deux lignes recalculées séparément")
    void recomputeAll_distinctCarriers_computesSeparateGroups() {
        Carrier otherCarrier = Carrier.builder().id(UUID.randomUUID()).name("Maersk").code("MAERSK").build();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);

        EtaReportedSource dhlObs = observation("CHN", "DEU", "SEA", base, base.plusDays(5));
        EtaReportedSource maerskObs = EtaReportedSource.builder()
                .id(UUID.randomUUID()).company(company).carrier(otherCarrier)
                .shipment(ShipmentOrder.builder().id(UUID.randomUUID()).actualDeliveryDate(base).build())
                .source(EtaReportedSource.Source.CARRIER_BOOKING)
                .originCountry("CHN").destinationCountry("DEU").transportMode("SEA")
                .reportedArrival(base)
                .build();

        when(reportedSourceRepo.findScorable(EtaReportedSource.Source.CARRIER_BOOKING))
                .thenReturn(List.of(dhlObs, maerskObs));

        int groups = service.recomputeAll();

        assertThat(groups).isEqualTo(2);
        verify(reliabilityRepo, times(2)).save(any());
    }

    @Test
    @DisplayName("ligne de fiabilité déjà existante pour le groupe → mise à jour en place, pas de doublon")
    void recomputeAll_existingReliabilityRow_updatesInPlace() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(reportedSourceRepo.findScorable(EtaReportedSource.Source.CARRIER_BOOKING))
                .thenReturn(List.of(observation("CHN", "DEU", "SEA", base, base.plusDays(3))));

        EtaSourceReliability existing = EtaSourceReliability.builder()
                .id(UUID.randomUUID()).company(company).carrier(carrier)
                .originCountry("CHN").destinationCountry("DEU").transportMode("SEA")
                .sampleCount(10).build();
        when(reliabilityRepo.findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
                eq(7L), eq(carrier.getId()), eq("CHN"), eq("DEU"), eq("SEA")))
                .thenReturn(Optional.of(existing));

        service.recomputeAll();

        ArgumentCaptor<EtaSourceReliability> captor = ArgumentCaptor.forClass(EtaSourceReliability.class);
        verify(reliabilityRepo).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getSampleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("aucune observation comparable → aucun recalcul")
    void recomputeAll_noScorableObservations_doesNothing() {
        when(reportedSourceRepo.findScorable(EtaReportedSource.Source.CARRIER_BOOKING)).thenReturn(List.of());

        int groups = service.recomputeAll();

        assertThat(groups).isZero();
        verifyNoInteractions(reliabilityRepo);
    }
}
