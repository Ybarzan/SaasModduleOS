package com.fleethub.service;

import com.fleethub.config.EtaMlConfig;
import com.fleethub.model.*;
import com.fleethub.repository.*;
import com.fleethub.security.TenantContext;
import com.fleethub.service.ml.EtaMlClient;
import com.fleethub.service.ml.EtaRegressionModel;
import com.fleethub.service.ml.EtaTrainingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Couvre surtout applyReliabilityWeighting() (Phase 2 J5, voir
 * docs/03-plan-migration.md) : la confiance heuristique existante ne doit
 * bouger QUE quand la prédiction est rattachée à une expédition réelle ET
 * qu'il y a assez d'observations réelles mesurées -- jamais sur une
 * supposition.
 */
@DisplayName("EtaPredictionService — pondération par fiabilité réelle")
class EtaPredictionServiceTest {

    EtaPredictionRepository etaPredictionRepo;
    CompanyRepository companyRepo;
    EtaTrainingService etaTrainingService;
    EtaMlClient etaMlClient;
    EtaMlConfig etaMlConfig;
    ShipmentOrderRepository shipmentRepo;
    EtaSourceReliabilityRepository reliabilityRepo;
    EtaPredictionService service;

    Company company;
    Carrier carrier;
    ShipmentOrder shipment;

    @BeforeEach
    void setUp() throws Exception {
        etaPredictionRepo = mock(EtaPredictionRepository.class);
        companyRepo = mock(CompanyRepository.class);
        etaTrainingService = mock(EtaTrainingService.class);
        etaMlClient = mock(EtaMlClient.class);
        etaMlConfig = mock(EtaMlConfig.class);
        shipmentRepo = mock(ShipmentOrderRepository.class);
        reliabilityRepo = mock(EtaSourceReliabilityRepository.class);

        service = new EtaPredictionService(
                etaPredictionRepo, companyRepo, etaTrainingService, etaMlClient, etaMlConfig,
                shipmentRepo, reliabilityRepo);

        company = new Company();
        company.setId(7L);
        when(companyRepo.findById(7L)).thenReturn(Optional.of(company));
        when(etaPredictionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Désactive les deux voies ML (service Python + modèle Java) pour isoler l'heuristique.
        when(etaMlClient.predict(any(), any(), any(), any())).thenThrow(new RuntimeException("indisponible"));
        EtaRegressionModel untrainedModel = mock(EtaRegressionModel.class);
        when(untrainedModel.isTrained()).thenReturn(false);
        when(etaTrainingService.loadModel(any())).thenReturn(untrainedModel);

        carrier = Carrier.builder().id(UUID.randomUUID()).name("DHL").code("DHL").build();
        shipment = ShipmentOrder.builder()
                .id(UUID.randomUUID()).carrier(carrier)
                .shipperCountry("CHN").consigneeCountry("DEU")
                .build();

        TenantContext.set(company);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private EtaPrediction baselinePrediction() {
        return EtaPrediction.builder().origin("CNSHA").destination("DEHAM").mode("SEA").carrierName("DHL").build();
    }

    @Test
    @DisplayName("prédiction ad hoc (sans expédition réelle) → confiance heuristique inchangée, jamais de requête de fiabilité")
    void predict_withoutShipment_neverQueriesReliability() {
        EtaPrediction result = service.predict(baselinePrediction());

        verifyNoInteractions(reliabilityRepo);
        assertThat(result.isReliabilityWeighted()).isFalse();
        assertThat(result.getReliabilitySampleCount()).isNull();
    }

    /** Confiance heuristique "pure" pour la lane/mode/date du test courant,
     *  recalculée à chaque fois plutôt que figée en dur : seasonalFactor
     *  dépend du mois réel d'exécution (calculateSeasonalFactor), une valeur
     *  codée en dur casserait selon la date à laquelle le test tourne. */
    private BigDecimal heuristicBaseline() {
        return service.predict(baselinePrediction()).getConfidencePercent();
    }

    @Test
    @DisplayName("prédiction rattachée à une expédition, aucune fiabilité mesurée pour ce groupe → confiance heuristique inchangée")
    void predict_withShipmentButNoReliabilityRow_usesHeuristicOnly() {
        BigDecimal baseline = heuristicBaseline();
        when(shipmentRepo.findByIdAndCompanyId(shipment.getId(), 7L)).thenReturn(Optional.of(shipment));
        when(reliabilityRepo.findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        EtaPrediction result = service.predict(baselinePrediction(), shipment.getId());

        assertThat(result.getConfidencePercent()).isEqualByComparingTo(baseline);
        assertThat(result.isReliabilityWeighted()).isFalse();
        assertThat(result.getReliabilitySampleCount()).isNull();
    }

    @Test
    @DisplayName("fiabilité mesurée mais sous le seuil minimal d'échantillons → confiance heuristique inchangée")
    void predict_reliabilityBelowMinSamples_usesHeuristicOnly() {
        BigDecimal baseline = heuristicBaseline();
        when(shipmentRepo.findByIdAndCompanyId(shipment.getId(), 7L)).thenReturn(Optional.of(shipment));
        EtaSourceReliability lowSample = EtaSourceReliability.builder()
                .sampleCount(5).meanAbsoluteDeviationDays(BigDecimal.ZERO).build();
        when(reliabilityRepo.findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
                any(), any(), any(), any(), any())).thenReturn(Optional.of(lowSample));

        EtaPrediction result = service.predict(baselinePrediction(), shipment.getId());

        assertThat(result.getConfidencePercent()).isEqualByComparingTo(baseline);
        assertThat(result.isReliabilityWeighted()).isFalse();
        assertThat(result.getReliabilitySampleCount()).isNull();
    }

    @Test
    @DisplayName("fiabilité mesurée au-dessus du seuil, écart faible → confiance tirée vers le haut par la mesure réelle")
    void predict_reliabilityAboveThresholdLowDeviation_pullsConfidenceUp() {
        when(shipmentRepo.findByIdAndCompanyId(shipment.getId(), 7L)).thenReturn(Optional.of(shipment));
        // 100 échantillons (plein poids), écart absolu moyen de 1 jour → mesure = 95 - 4 = 91, au-dessus de l'heuristique (85).
        EtaSourceReliability highSampleLowMad = EtaSourceReliability.builder()
                .sampleCount(100).meanAbsoluteDeviationDays(BigDecimal.ONE).build();
        when(reliabilityRepo.findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
                7L, carrier.getId(), "CHN", "DEU", null)).thenReturn(Optional.of(highSampleLowMad));

        EtaPrediction result = service.predict(baselinePrediction(), shipment.getId());

        assertThat(result.getConfidencePercent()).isGreaterThan(BigDecimal.valueOf(85));
        assertThat(result.isReliabilityWeighted()).isTrue();
        assertThat(result.getReliabilitySampleCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("fiabilité mesurée au-dessus du seuil, écart important → confiance tirée vers le bas par la mesure réelle")
    void predict_reliabilityAboveThresholdHighDeviation_pullsConfidenceDown() {
        when(shipmentRepo.findByIdAndCompanyId(shipment.getId(), 7L)).thenReturn(Optional.of(shipment));
        // écart absolu moyen de 15 jours → mesure = 95 - 60 = 35, bien sous l'heuristique (85).
        EtaSourceReliability highSampleHighMad = EtaSourceReliability.builder()
                .sampleCount(100).meanAbsoluteDeviationDays(BigDecimal.valueOf(15)).build();
        when(reliabilityRepo.findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
                7L, carrier.getId(), "CHN", "DEU", null)).thenReturn(Optional.of(highSampleHighMad));

        EtaPrediction result = service.predict(baselinePrediction(), shipment.getId());

        assertThat(result.getConfidencePercent()).isLessThan(BigDecimal.valueOf(85));
        assertThat(result.isReliabilityWeighted()).isTrue();
        assertThat(result.getReliabilitySampleCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("confiance toujours bornée [20,98] même avec une mesure de fiabilité extrême")
    void predict_reliabilityExtreme_confidenceStaysBounded() {
        when(shipmentRepo.findByIdAndCompanyId(shipment.getId(), 7L)).thenReturn(Optional.of(shipment));
        EtaSourceReliability extreme = EtaSourceReliability.builder()
                .sampleCount(1000).meanAbsoluteDeviationDays(BigDecimal.valueOf(100)).build();
        when(reliabilityRepo.findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
                7L, carrier.getId(), "CHN", "DEU", null)).thenReturn(Optional.of(extreme));

        EtaPrediction result = service.predict(baselinePrediction(), shipment.getId());

        assertThat(result.getConfidencePercent()).isGreaterThanOrEqualTo(BigDecimal.valueOf(20));
        assertThat(result.getConfidencePercent()).isLessThanOrEqualTo(BigDecimal.valueOf(98));
    }
}
