package com.incokalk.service.ml;

import com.incokalk.model.Company;
import com.incokalk.model.EtaModelCoefficient;
import com.incokalk.model.EtaPrediction;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EtaModelCoefficientRepository;
import com.incokalk.repository.EtaPredictionRepository;
import com.incokalk.scheduling.DistributedJobLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("EtaTrainingService — Tests unitaires")
class EtaTrainingServiceTest {

    @Mock EtaPredictionRepository etaPredictionRepo;
    @Mock EtaModelCoefficientRepository coefficientRepo;
    @Mock CompanyRepository companyRepo;

    EtaTrainingService service;
    UUID companyId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        // Optional.empty() => DistributedJobLock is a transparent no-op (no Redis wired).
        service = new EtaTrainingService(etaPredictionRepo, coefficientRepo, companyRepo,
                new DistributedJobLock(Optional.empty()));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private EtaPrediction.EtaPredictionBuilder base() {
        return EtaPrediction.builder()
                .id(UUID.randomUUID())
                .origin("FRPAR")
                .destination("FRLYS")
                .mode("SEA");
    }

    /** Records with all optional fields populated and identical actualDays (guarantees R²=0 → not trained). */
    private List<EtaPrediction> notTrainedFixture_allFieldsPresent() {
        List<EtaPrediction> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(base()
                    .carrierName("DHL")
                    .origin("FRPAR")
                    .destination("USNYC")
                    .createdAt(LocalDateTime.of(2024, 3, 15, 10, 0))
                    .actualDays(10)
                    .predictionAccuracy(BigDecimal.valueOf(90))
                    .build());
        }
        return list;
    }

    /** Records with all optional fields absent/null and identical actualDays (guarantees R²=0 → not trained). */
    private List<EtaPrediction> notTrainedFixture_allFieldsAbsent() {
        List<EtaPrediction> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(base()
                    .carrierName(null)
                    .origin(null)
                    .destination(null)
                    .createdAt(null)
                    .actualDays(10)
                    .build());
        }
        return list;
    }

    /**
     * Records with unique "mode" values and varying actualDays, no other optional feature set.
     * Given EtaRegressionModel's coefficient math, a single feature key unique to each sample
     * yields a perfect fit (R²=1), which drives trainModel into the "success" branch.
     */
    private List<EtaPrediction> trainedFixture() {
        List<EtaPrediction> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(base()
                    .mode("MODE" + i)
                    .carrierName(null)
                    .origin(null)
                    .destination(null)
                    .createdAt(null)
                    .actualDays(5 + i)
                    .build());
        }
        return list;
    }

    private void stubHistorical(List<EtaPrediction> historical) {
        when(etaPredictionRepo.findByCompanyIdAndActualDaysNotNullAndPredictionAccuracyNotNull(companyId))
                .thenReturn(historical);
    }

    // ── trainModel — insufficient data branch ───────────────────────────

    @Test
    @DisplayName("trainModel → moins de 10 échantillons historiques → modèle vide non entraîné")
    void trainModel_insufficientData() {
        List<EtaPrediction> historical = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            historical.add(base().actualDays(10).build());
        }
        stubHistorical(historical);

        EtaRegressionModel result = service.trainModel(companyId);

        assertThat(result.isTrained()).isFalse();
        assertThat(result.getTotalSamples()).isZero();
        verifyNoInteractions(coefficientRepo);
    }

    @Test
    @DisplayName("trainModel → exactement 0 échantillon historique → modèle vide non entraîné")
    void trainModel_noData() {
        stubHistorical(Collections.emptyList());

        EtaRegressionModel result = service.trainModel(companyId);

        assertThat(result.isTrained()).isFalse();
        verifyNoInteractions(coefficientRepo);
    }

    // ── trainModel — model-not-trained branch (toTrainingSample: true branches) ──

    @Test
    @DisplayName("trainModel → >=10 échantillons mais R² non positif (jours identiques) → modèle non entraîné, features toutes présentes")
    void trainModel_notTrained_allFeaturesPresent() {
        stubHistorical(notTrainedFixture_allFieldsPresent());

        EtaRegressionModel result = service.trainModel(companyId);

        assertThat(result.isTrained()).isFalse();
        verify(coefficientRepo, never()).save(any());
        verify(coefficientRepo, never()).saveAll(any());
    }

    // ── trainModel — model-not-trained branch (toTrainingSample: false/null branches) ──

    @Test
    @DisplayName("trainModel → >=10 échantillons mais R² non positif (jours identiques) → modèle non entraîné, features toutes absentes")
    void trainModel_notTrained_allFeaturesAbsent() {
        stubHistorical(notTrainedFixture_allFieldsAbsent());

        EtaRegressionModel result = service.trainModel(companyId);

        assertThat(result.isTrained()).isFalse();
        verify(coefficientRepo, never()).save(any());
    }

    @Test
    @DisplayName("trainModel → codes origine/destination trop courts (<2) → branche longueur non déclenchée")
    void trainModel_notTrained_shortOriginDestinationCodes() {
        List<EtaPrediction> historical = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            historical.add(base()
                    .carrierName("DHL")
                    .origin("A")
                    .destination("B")
                    .createdAt(LocalDateTime.of(2024, 3, 15, 10, 0))
                    .actualDays(10)
                    .build());
        }
        stubHistorical(historical);

        EtaRegressionModel result = service.trainModel(companyId);

        assertThat(result.isTrained()).isFalse();
        verify(coefficientRepo, never()).save(any());
    }

    // ── trainModel — success branch ─────────────────────────────────────

    @Test
    @DisplayName("trainModel → modèle entraîné avec succès → coefficients sauvegardés, anciens désactivés")
    void trainModel_success() {
        stubHistorical(trainedFixture());

        EtaModelCoefficient existingActive = EtaModelCoefficient.builder()
                .company(Company.builder().id(companyId).build())
                .featureName("mode")
                .featureValue("OLD")
                .coefficient(BigDecimal.ONE)
                .isActive(true)
                .build();
        when(coefficientRepo.findByCompanyIdAndIsActiveTrue(companyId))
                .thenReturn(new ArrayList<>(List.of(existingActive)));
        when(coefficientRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(coefficientRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EtaRegressionModel result = service.trainModel(companyId);

        assertThat(result.isTrained()).isTrue();
        assertThat(result.getTotalSamples()).isEqualTo(10);
        assertThat(result.getRSquared()).isGreaterThan(0);

        // Existing active coefficients get deactivated then saved.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EtaModelCoefficient>> captor = ArgumentCaptor.forClass(List.class);
        verify(coefficientRepo).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(c -> assertThat(c.isActive()).isFalse());

        // One new coefficient saved per distinct feature key (10 unique "mode:MODEx" keys).
        verify(coefficientRepo, times(10)).save(any(EtaModelCoefficient.class));
    }

    // ── loadModel — empty coefficients branch ───────────────────────────

    @Test
    @DisplayName("loadModel → aucun coefficient actif → modèle vide non entraîné")
    void loadModel_empty() {
        when(coefficientRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());

        EtaRegressionModel result = service.loadModel(companyId);

        assertThat(result.isTrained()).isFalse();
        assertThat(result.getTotalSamples()).isZero();
        assertThat(result.getCoefficients()).isEmpty();
    }

    // ── loadModel — populated branch (covers both rSquared null/non-null sub-branches) ──

    @Test
    @DisplayName("loadModel → coefficients actifs présents → modèle reconstruit (dernier enregistrement gagne intercept/R²/échantillons)")
    void loadModel_populated() {
        EtaModelCoefficient c1 = EtaModelCoefficient.builder()
                .featureName("mode")
                .featureValue("SEA")
                .coefficient(BigDecimal.valueOf(2.5))
                .intercept(BigDecimal.valueOf(5.0))
                .rSquared(BigDecimal.valueOf(0.8))
                .samplesCount(15)
                .isActive(true)
                .build();
        EtaModelCoefficient c2 = EtaModelCoefficient.builder()
                .featureName("carrier")
                .featureValue("DHL")
                .coefficient(BigDecimal.valueOf(1.2))
                .intercept(BigDecimal.valueOf(5.5))
                .rSquared(null) // exercises the "rSquared == null" branch → defaults to 0
                .samplesCount(20)
                .isActive(true)
                .build();
        when(coefficientRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(Arrays.asList(c1, c2));

        EtaRegressionModel result = service.loadModel(companyId);

        assertThat(result.getCoefficients())
                .containsEntry("mode:SEA", 2.5)
                .containsEntry("carrier:DHL", 1.2);
        // Loop overwrites scalar fields with the last processed coefficient's values.
        assertThat(result.getIntercept()).isEqualTo(5.5);
        assertThat(result.getRSquared()).isEqualTo(0.0);
        assertThat(result.getTotalSamples()).isEqualTo(20);
    }
}
