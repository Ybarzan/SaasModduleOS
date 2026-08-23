package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.EtaPrediction;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EtaPredictionRepository;
import com.incokalk.service.ml.EtaMlClient;
import com.incokalk.service.ml.EtaRegressionModel;
import com.incokalk.service.ml.EtaTrainingService;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EtaPredictionService — Tests unitaires")
class EtaPredictionServiceTest {

    @Mock EtaPredictionRepository etaPredictionRepo;
    @Mock CompanyRepository companyRepo;
    @Mock EtaTrainingService etaTrainingService;
    @Mock EtaMlClient etaMlClient;
    @Mock com.incokalk.config.EtaMlConfig etaMlConfig;
    @InjectMocks EtaPredictionService service;

    UUID companyId;
    UUID predictionId;
    Company company;

    // Fixed instants built BEFORE any MockedStatic<LocalDateTime> is opened. Building a
    // LocalDateTime.of(...) *inside* a try-with-resources that mocks LocalDateTime statics
    // would itself be intercepted by Mockito (since all static methods of the mocked class
    // are captured, not just now()), corrupting the in-progress stubbing.
    private static final LocalDateTime NEUTRAL_MARCH = LocalDateTime.of(2024, 3, 15, 10, 0);
    private static final LocalDateTime HIGH_SEASON_NOV = LocalDateTime.of(2024, 11, 10, 8, 0);
    private static final LocalDateTime EXISTING_ARRIVAL_2030 = LocalDateTime.of(2030, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        predictionId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
        // Service ML Python indisponible dans ces tests : on reste sur le repli Java/heuristique.
        lenient().when(etaMlClient.predict(any(), any(), any(), any())).thenReturn(null);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private EtaPrediction.EtaPredictionBuilder base() {
        return EtaPrediction.builder()
                .id(predictionId)
                .origin("FRPAR")
                .destination("FRLYS")
                .mode("SEA")
                .weatherDelayDays(0);
    }

    private void stubCommon() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(etaPredictionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── getAll / getById / getByLane ──────────────────────────────────────

    @Test
    @DisplayName("getAll → liste triée par date de création")
    void getAll() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            EtaPrediction p = base().build();
            when(etaPredictionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(p));
            assertThat(service.getAll()).containsExactly(p);
        }
    }

    @Test
    @DisplayName("getById → trouvé")
    void getById_found() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            EtaPrediction p = base().build();
            when(etaPredictionRepo.findByCompanyIdAndId(companyId, predictionId)).thenReturn(Optional.of(p));
            assertThat(service.getById(predictionId)).isEqualTo(p);
        }
    }

    @Test
    @DisplayName("getById → pas trouvé lève ResourceNotFoundException")
    void getById_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(etaPredictionRepo.findByCompanyIdAndId(companyId, predictionId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(predictionId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("getByLane → filtre origine/destination")
    void getByLane() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            EtaPrediction p = base().build();
            when(etaPredictionRepo.findByCompanyIdAndOriginAndDestination(companyId, "FRPAR", "FRLYS"))
                    .thenReturn(List.of(p));
            assertThat(service.getByLane("FRPAR", "FRLYS")).containsExactly(p);
        }
    }

    // ── predict — company introuvable ─────────────────────────────────────

    @Test
    @DisplayName("predict → entreprise introuvable lève ResourceNotFoundException")
    void predict_companyNotFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
            EtaPrediction p = base().build();
            assertThatThrownBy(() -> service.predict(p)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── predict — mode / baseDays branches (neutral month = mars, lane FR→FR = OTHER, fallback 30) ────────

    @Test
    @DisplayName("predict → mode SEA, baseDays=30 (lane FR→FR non classifiée → repli SEA_DEFAULT)")
    void predict_mode_sea() {
        assertPredictedDaysForMode("SEA", 31);
    }

    @Test
    @DisplayName("predict → mode AIR, baseDays=5")
    void predict_mode_air() {
        assertPredictedDaysForMode("AIR", 6);
    }

    @Test
    @DisplayName("predict → mode ROAD, baseDays=3")
    void predict_mode_road() {
        assertPredictedDaysForMode("ROAD", 4);
    }

    @Test
    @DisplayName("predict → mode RAIL, baseDays=15")
    void predict_mode_rail() {
        assertPredictedDaysForMode("RAIL", 16);
    }

    @Test
    @DisplayName("predict → mode null → défaut SEA (baseDays=30)")
    void predict_mode_null_defaultsToSea() {
        assertPredictedDaysForMode(null, 31);
    }

    @Test
    @DisplayName("predict → mode inconnu → baseDays par défaut 30")
    void predict_mode_unknown_defaultsTo30() {
        assertPredictedDaysForMode("TRAIN", 31);
    }

    private void assertPredictedDaysForMode(String mode, int expectedDays) {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());

            EtaPrediction p = base().mode(mode).build();
            EtaPrediction result = service.predict(p);

            assertThat(result.getPredictedDays()).isEqualTo(expectedDays);
            assertThat(result.getBaselineDays()).isNotNull();
        }
    }

    // ── predict — calculateSeasonalFactor branches ─────────────────────────

    @Test
    @DisplayName("predict → haute saison (oct/nov/déc) → facteur saisonnier 1.15")
    void predict_seasonalFactor_highSeason() {
        BigDecimal factor = predictWithMonth(LocalDateTime.of(2024, 11, 10, 8, 0)).getSeasonalFactor();
        assertThat(factor).isEqualByComparingTo(BigDecimal.valueOf(1.15));
    }

    @Test
    @DisplayName("predict → saison moyenne (juin/juil/août) → facteur saisonnier 1.05")
    void predict_seasonalFactor_midSeason() {
        BigDecimal factor = predictWithMonth(LocalDateTime.of(2024, 7, 10, 8, 0)).getSeasonalFactor();
        assertThat(factor).isEqualByComparingTo(BigDecimal.valueOf(1.05));
    }

    @Test
    @DisplayName("predict → saison normale (mars) → facteur saisonnier 1.0")
    void predict_seasonalFactor_defaultSeason() {
        BigDecimal factor = predictWithMonth(LocalDateTime.of(2024, 3, 10, 8, 0)).getSeasonalFactor();
        assertThat(factor).isEqualByComparingTo(BigDecimal.ONE);
    }

    private EtaPrediction predictWithMonth(LocalDateTime now) {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(now);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());
            EtaPrediction p = base().build();
            return service.predict(p);
        }
    }

    // ── predict — calculateCongestionFactor branches ────────────────────────

    @Test
    @DisplayName("predict → congestion origine et destination → 1.15")
    void predict_congestion_both() {
        EtaPrediction r = predictWithLane("CNSHA", "NLRTM");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.valueOf(1.15));
    }

    @Test
    @DisplayName("predict → congestion origine seule (préfixe CN) → 1.08")
    void predict_congestion_originOnly() {
        EtaPrediction r = predictWithLane("CNXXXX", "USNYC");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.valueOf(1.08));
    }

    @Test
    @DisplayName("predict → congestion destination seule (préfixe NL) → 1.08")
    void predict_congestion_destOnly_nlPrefix() {
        EtaPrediction r = predictWithLane("USNYC", "NLXYZ");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.valueOf(1.08));
    }

    @Test
    @DisplayName("predict → congestion destination seule (préfixe DE) → 1.08")
    void predict_congestion_destOnly_dePrefix() {
        EtaPrediction r = predictWithLane("USNYC", "DEXYZ");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.valueOf(1.08));
    }

    @Test
    @DisplayName("predict → congestion destination seule (préfixe BE) → 1.08")
    void predict_congestion_destOnly_bePrefix() {
        EtaPrediction r = predictWithLane("USNYC", "BEXYZ");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.valueOf(1.08));
    }

    @Test
    @DisplayName("predict → congestion destination seule (préfixe EU) → 1.08")
    void predict_congestion_destOnly_euPrefix() {
        EtaPrediction r = predictWithLane("USNYC", "EUXYZ");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.valueOf(1.08));
    }

    @Test
    @DisplayName("predict → congestion destination via appartenance au set (AEJEA) → origine seule via set")
    void predict_congestion_originOnly_viaSetMembership() {
        EtaPrediction r = predictWithLane("AEJEA", "USNYC");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.valueOf(1.08));
    }

    @Test
    @DisplayName("predict → aucune congestion → 1.0")
    void predict_congestion_none() {
        EtaPrediction r = predictWithLane("USNYC", "USLAX");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("predict → origine nulle → congestion neutre 1.0")
    void predict_congestion_nullOrigin() {
        EtaPrediction r = predictWithLane(null, "FRPAR");
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("predict → destination nulle → congestion neutre 1.0")
    void predict_congestion_nullDestination() {
        EtaPrediction r = predictWithLane("FRPAR", null);
        assertThat(r.getCongestionFactor()).isEqualByComparingTo(BigDecimal.ONE);
    }

    private EtaPrediction predictWithLane(String origin, String destination) {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());
            EtaPrediction p = base().origin(origin).destination(destination).build();
            return service.predict(p);
        }
    }

    // ── predict — estimateCustomsDelay branches ────────────────────────────

    @Test
    @DisplayName("predict → même pays → délai douanier 1")
    void predict_customsDelay_sameCountry() {
        EtaPrediction r = predictWithLane("FRPAR", "FRLYS");
        assertThat(r.getCustomsDelayDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("predict → pays différents → délai douanier 2")
    void predict_customsDelay_differentCountry() {
        EtaPrediction r = predictWithLane("CNSHA", "FRPAR");
        assertThat(r.getCustomsDelayDays()).isEqualTo(2);
    }

    @Test
    @DisplayName("predict → origine nulle → délai douanier 1")
    void predict_customsDelay_nullOrigin() {
        EtaPrediction r = predictWithLane(null, "FRPAR");
        assertThat(r.getCustomsDelayDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("predict → destination nulle → délai douanier 1")
    void predict_customsDelay_nullDestination() {
        EtaPrediction r = predictWithLane("FRPAR", null);
        assertThat(r.getCustomsDelayDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("predict → codes origine/destination trop courts (<2) → pays vides égaux → délai 1")
    void predict_customsDelay_shortCodesBothEmpty() {
        EtaPrediction r = predictWithLane("A", "B");
        assertThat(r.getCustomsDelayDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("predict → code origine trop court, destination normale → pays différents → délai 2")
    void predict_customsDelay_shortOriginOnly() {
        EtaPrediction r = predictWithLane("A", "FRPAR");
        assertThat(r.getCustomsDelayDays()).isEqualTo(2);
    }

    // ── predict — computeMlPrediction branches (ML) ─────────────────────────

    @Test
    @DisplayName("predict → modèle non entraîné → repli heuristique")
    void predict_ml_modelNotTrained() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());

            EtaPrediction result = service.predict(base().build());

            assertThat(result.getPredictedDays()).isEqualTo(31); // heuristique pure
        }
    }

    @Test
    @DisplayName("predict → exception lors du chargement du modèle ML → repli heuristique")
    void predict_ml_loadModelThrows() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenThrow(new RuntimeException("boom"));

            EtaPrediction result = service.predict(base().build());

            assertThat(result.getPredictedDays()).isEqualTo(31); // heuristique pure malgré exception
        }
    }

    @Test
    @DisplayName("predict → modèle entraîné, peu d'échantillons (<50) → moyenne ML/heuristique")
    void predict_ml_trained_lowSamples_blended() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            EtaRegressionModel model = new EtaRegressionModel(10.0, Collections.emptyMap(), 0.5, 20);
            when(etaTrainingService.loadModel(companyId)).thenReturn(model);

            EtaPrediction result = service.predict(base().build());

            // heuristique = 31, ml = round(10.0) = 10 → blended = max(1,(10+31)/2) = 20
            assertThat(result.getPredictedDays()).isEqualTo(20);
        }
    }

    @Test
    @DisplayName("predict → modèle entraîné, beaucoup d'échantillons (>=50) → prédiction ML seule")
    void predict_ml_trained_highSamples_mlOnly() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            EtaRegressionModel model = new EtaRegressionModel(15.0, Collections.emptyMap(), 0.8, 60);
            when(etaTrainingService.loadModel(companyId)).thenReturn(model);

            EtaPrediction result = service.predict(base().carrierName("DHL").origin("FRPAR").destination("USNYC").build());

            assertThat(result.getPredictedDays()).isEqualTo(15);
        }
    }

    @Test
    @DisplayName("predict → features ML : carrierName/origin/destination absents ou trop courts")
    void predict_ml_features_allAbsentOrShort() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            EtaRegressionModel model = new EtaRegressionModel(15.0, Collections.emptyMap(), 0.8, 60);
            when(etaTrainingService.loadModel(companyId)).thenReturn(model);

            EtaPrediction p = base().carrierName(null).origin("A").destination("B").build();
            EtaPrediction result = service.predict(p);

            assertThat(result.getPredictedDays()).isEqualTo(15);
        }
    }

    // ── predict — variance / confidence branches ────────────────────────────

    @Test
    @DisplayName("predict → sans estimation transporteur → varianceDays reste null")
    void predict_variance_noCarrierEstimate() {
        EtaPrediction r = predictNeutral(b -> b.carrierEstimateDays(null));
        assertThat(r.getVarianceDays()).isNull();
    }

    @Test
    @DisplayName("predict → écart faible (<=2) avec estimation transporteur → bonus de confiance, niveau HIGH")
    void predict_confidence_smallVariance_bonus_high() {
        // predictedDays neutre = 31 ; carrierEstimateDays=30 → variance=1 (<=2) → +5 → confiance=90 → HIGH
        EtaPrediction r = predictNeutral(b -> b.carrierEstimateDays(30));
        assertThat(r.getVarianceDays()).isEqualTo(1);
        assertThat(r.getConfidencePercent()).isEqualByComparingTo(BigDecimal.valueOf(90).setScale(2));
        assertThat(r.getConfidenceLevel()).isEqualTo(EtaPrediction.ConfidenceLevel.HIGH);
    }

    @Test
    @DisplayName("predict → écart important (>5) avec estimation transporteur → pénalité de confiance, niveau MEDIUM")
    void predict_confidence_largeVariance_penalty_medium() {
        // predictedDays=31 ; carrierEstimateDays=20 → variance=11 (>5) → -10 → confiance=75 → MEDIUM
        EtaPrediction r = predictNeutral(b -> b.carrierEstimateDays(20));
        assertThat(r.getVarianceDays()).isEqualTo(11);
        assertThat(r.getConfidencePercent()).isEqualByComparingTo(BigDecimal.valueOf(75).setScale(2));
        assertThat(r.getConfidenceLevel()).isEqualTo(EtaPrediction.ConfidenceLevel.MEDIUM);
    }

    @Test
    @DisplayName("predict → écart moyen (3-5) → ni bonus ni pénalité → confiance inchangée")
    void predict_confidence_mediumVariance_noChange() {
        // predictedDays=31 ; carrierEstimateDays=27 → variance=4 (entre 2 et 5) → pas de changement → confiance=85 → HIGH
        EtaPrediction r = predictNeutral(b -> b.carrierEstimateDays(27));
        assertThat(r.getVarianceDays()).isEqualTo(4);
        assertThat(r.getConfidencePercent()).isEqualByComparingTo(BigDecimal.valueOf(85).setScale(2));
        assertThat(r.getConfidenceLevel()).isEqualTo(EtaPrediction.ConfidenceLevel.HIGH);
    }

    @Test
    @DisplayName("predict → haute saison + congestion + météo → pénalités cumulées → niveau MEDIUM")
    void predict_confidence_seasonalCongestionWeather_penalties() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(HIGH_SEASON_NOV);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());

            EtaPrediction p = base().origin("CNSHA").destination("NLRTM").weatherDelayDays(2).build();
            EtaPrediction result = service.predict(p);

            // 85 - 5(seasonal) - 5(congestion) - 5(weather) = 70 → MEDIUM
            assertThat(result.getConfidencePercent()).isEqualByComparingTo(BigDecimal.valueOf(70).setScale(2));
            assertThat(result.getConfidenceLevel()).isEqualTo(EtaPrediction.ConfidenceLevel.MEDIUM);
        }
    }

    private EtaPrediction predictNeutral(java.util.function.Function<EtaPrediction.EtaPredictionBuilder, EtaPrediction.EtaPredictionBuilder> customizer) {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());
            EtaPrediction.EtaPredictionBuilder builder = base();
            builder = customizer.apply(builder);
            return service.predict(builder.build());
        }
    }

    // ── predict — riskFactors branches ──────────────────────────────────────

    @Test
    @DisplayName("predict → tous les facteurs de risque déclenchés (dont cape_routing sur ligne Asie-Europe)")
    void predict_riskFactors_allTriggered() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(HIGH_SEASON_NOV);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());

            EtaPrediction p = base().origin("CNSHA").destination("NLRTM")
                    .weatherDelayDays(2)
                    .carrierEstimateDays(1) // predictedDays sera bien > 1 → variance positive
                    .build();
            EtaPrediction result = service.predict(p);

            // CNSHA→NLRTM est une ligne Asie-Europe : cape_routing se déclenche en premier
            assertThat(result.getRiskFactors()).isEqualTo("cape_routing,seasonal,congestion,customs,weather,carrier_variability");
        }
    }

    @Test
    @DisplayName("predict → seul le facteur douanier est déclenché (toujours présent)")
    void predict_riskFactors_onlyCustoms() {
        // variance = 0 (carrierEstimateDays == predictedDays), mois neutre, pas de congestion, pas de météo
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());

            EtaPrediction p = base().origin("FRPAR").destination("FRLYS")
                    .weatherDelayDays(0)
                    .carrierEstimateDays(31) // == predictedDays neutre → variance 0
                    .build();
            EtaPrediction result = service.predict(p);

            assertThat(result.getRiskFactors()).isEqualTo("customs");
            assertThat(result.getVarianceDays()).isZero();
        }
    }

    // ── predict — lane maritime (Cap de Bonne-Espérance) / ajustement transporteur ──

    @Test
    @DisplayName("predict → Asie-Europe (Cap de Bonne-Espérance) prend nettement plus de jours que le transpacifique")
    void predict_asiaEurope_muchLongerThan_transpacific() {
        EtaPrediction europe = predictWithLane("CNSHA", "NLRTM");
        EtaPrediction transpacific = predictWithLane("CNSHA", "USLAX");

        assertThat(europe.getPredictedDays()).isGreaterThan(transpacific.getPredictedDays() + 15);
    }

    @Test
    @DisplayName("predict → le facteur cape_routing apparaît sur Asie-Europe mais pas sur le transpacifique")
    void predict_capeRoutingFactor_onlyOnAsiaEurope() {
        EtaPrediction europe = predictWithLane("CNSHA", "NLRTM");
        EtaPrediction transpacific = predictWithLane("CNSHA", "USLAX");

        assertThat(europe.getRiskFactors()).contains("cape_routing");
        assertThat(transpacific.getRiskFactors() == null || !transpacific.getRiskFactors().contains("cape_routing")).isTrue();
    }

    @Test
    @DisplayName("predict → CMA CGM (+3j) donne un délai plus long que Maersk (-3j) sur la même ligne")
    void predict_carrierAdjustment_cmaCgmSlowerThanMaersk() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());

            EtaPrediction withCmaCgm = service.predict(
                    base().origin("CNSHA").destination("NLRTM").carrierName("CMA CGM").build());
            EtaPrediction withMaersk = service.predict(
                    base().origin("CNSHA").destination("NLRTM").carrierName("Maersk").build());

            assertThat(withCmaCgm.getPredictedDays()).isGreaterThan(withMaersk.getPredictedDays());
        }
    }

    // ── predict — predictedArrival branch ───────────────────────────────────

    @Test
    @DisplayName("predict → predictedArrival déjà défini → non écrasé")
    void predict_predictedArrival_alreadySet() {
        LocalDateTime existing = LocalDateTime.of(2030, 1, 1, 0, 0);
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(NEUTRAL_MARCH);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());

            EtaPrediction p = base().predictedArrival(existing).build();
            EtaPrediction result = service.predict(p);

            assertThat(result.getPredictedArrival()).isEqualTo(existing);
        }
    }

    @Test
    @DisplayName("predict → predictedArrival non défini → calculé automatiquement")
    void predict_predictedArrival_autoComputed() {
        LocalDateTime now = LocalDateTime.of(2024, 3, 15, 10, 0);
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class);
             MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            time.when(LocalDateTime::now).thenReturn(now);
            stubCommon();
            when(etaTrainingService.loadModel(companyId)).thenReturn(new EtaRegressionModel());

            EtaPrediction p = base().build();
            EtaPrediction result = service.predict(p);

            assertThat(result.getPredictedArrival()).isEqualTo(now.plusDays(result.getPredictedDays()));
        }
    }

    // ── calculateConfidence (réflexion) — branches inatteignables via predict() ──

    @Test
    @DisplayName("calculateConfidence (réflexion) → déductions cumulées incl. customsDelay>2")
    void calculateConfidence_extremeDeductions_viaReflection() throws Exception {
        EtaPrediction p = base()
                .carrierEstimateDays(10)
                .build();
        p.setVarianceDays(9); // abs > 5 → -10
        p.setSeasonalFactor(BigDecimal.valueOf(1.15)); // > 1.1 → -5
        p.setCongestionFactor(BigDecimal.valueOf(1.15)); // > 1.1 → -5
        p.setCustomsDelayDays(5); // > 2 → -3 (jamais atteint via predict())
        p.setWeatherDelayDays(5); // > 0 → -5

        Method m = EtaPredictionService.class.getDeclaredMethod("calculateConfidence", EtaPrediction.class);
        m.setAccessible(true);
        BigDecimal confidence = (BigDecimal) m.invoke(service, p);

        // 85 - 10 - 5 - 5 - 3 - 5 = 57
        assertThat(confidence).isEqualByComparingTo(BigDecimal.valueOf(57).setScale(2));
    }

    // ── updateActual branches ───────────────────────────────────────────────

    @Test
    @DisplayName("updateActual → arrivée à temps → isOnTime=true, précision calculée")
    void updateActual_onTime_withAccuracy() {
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime predictedArrival = LocalDateTime.of(2024, 1, 31, 0, 0);
        LocalDateTime actualArrival = LocalDateTime.of(2024, 1, 30, 0, 0); // avant predictedArrival

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            EtaPrediction p = base()
                    .createdAt(createdAt)
                    .predictedArrival(predictedArrival)
                    .predictedDays(30)
                    .build();
            when(etaPredictionRepo.findByCompanyIdAndId(companyId, predictionId)).thenReturn(Optional.of(p));
            when(etaPredictionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            EtaPrediction result = service.updateActual(predictionId, actualArrival);

            assertThat(result.getIsOnTime()).isTrue();
            assertThat(result.getActualDays()).isEqualTo(29);
            assertThat(result.getPredictionAccuracy()).isNotNull();
        }
    }

    @Test
    @DisplayName("updateActual → arrivée en retard → isOnTime=false")
    void updateActual_late() {
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime predictedArrival = LocalDateTime.of(2024, 1, 31, 0, 0);
        LocalDateTime actualArrival = LocalDateTime.of(2024, 2, 5, 0, 0); // après predictedArrival

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            EtaPrediction p = base()
                    .createdAt(createdAt)
                    .predictedArrival(predictedArrival)
                    .predictedDays(30)
                    .build();
            when(etaPredictionRepo.findByCompanyIdAndId(companyId, predictionId)).thenReturn(Optional.of(p));
            when(etaPredictionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            EtaPrediction result = service.updateActual(predictionId, actualArrival);

            assertThat(result.getIsOnTime()).isFalse();
        }
    }

    @Test
    @DisplayName("updateActual → actualDays <= 0 → précision non calculée")
    void updateActual_actualDaysNotPositive_skipsAccuracy() {
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime predictedArrival = LocalDateTime.of(2024, 1, 31, 0, 0);
        LocalDateTime actualArrival = createdAt; // même jour → actualDays = 0

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            EtaPrediction p = base()
                    .createdAt(createdAt)
                    .predictedArrival(predictedArrival)
                    .predictedDays(30)
                    .build();
            when(etaPredictionRepo.findByCompanyIdAndId(companyId, predictionId)).thenReturn(Optional.of(p));
            when(etaPredictionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            EtaPrediction result = service.updateActual(predictionId, actualArrival);

            assertThat(result.getActualDays()).isZero();
            assertThat(result.getPredictionAccuracy()).isNull();
        }
    }

    // ── getStats branches ────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats → liste vide → valeurs par défaut")
    void getStats_empty() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(etaPredictionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(Collections.emptyList());

            Map<String, Object> stats = service.getStats();

            assertThat(stats.get("total")).isEqualTo(0L);
            assertThat(stats.get("avgAccuracy")).isEqualTo(BigDecimal.ZERO);
            assertThat(stats.get("onTimePercent")).isEqualTo(BigDecimal.ZERO);
            assertThat(stats.get("avgDays")).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("getStats → données mixtes → moyennes calculées correctement")
    void getStats_mixedData() {
        EtaPrediction p1 = base().predictionAccuracy(BigDecimal.valueOf(90)).isOnTime(true).predictedDays(10).build();
        EtaPrediction p2 = base().predictionAccuracy(null).isOnTime(false).predictedDays(20).build();
        EtaPrediction p3 = base().predictionAccuracy(BigDecimal.valueOf(80)).isOnTime(true).predictedDays(null).build();

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(etaPredictionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                    .thenReturn(Arrays.asList(p1, p2, p3));

            Map<String, Object> stats = service.getStats();

            assertThat(stats.get("total")).isEqualTo(3L);
            assertThat((BigDecimal) stats.get("avgAccuracy")).isEqualByComparingTo(BigDecimal.valueOf(85).setScale(2));
            assertThat((BigDecimal) stats.get("onTimePercent")).isEqualByComparingTo(BigDecimal.valueOf(66.67));
            assertThat(stats.get("avgDays")).isEqualTo(15);
        }
    }

    @Test
    @DisplayName("getStats → aucune précision disponible → avgAccuracy = 0 malgré liste non vide")
    void getStats_noAccuracyAvailable() {
        EtaPrediction p1 = base().predictionAccuracy(null).isOnTime(false).predictedDays(null).build();
        EtaPrediction p2 = base().predictionAccuracy(null).isOnTime(null).predictedDays(null).build();

        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(etaPredictionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                    .thenReturn(Arrays.asList(p1, p2));

            Map<String, Object> stats = service.getStats();

            assertThat((BigDecimal) stats.get("avgAccuracy")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(stats.get("avgDays")).isEqualTo(0); // orElse(0.0) branch
        }
    }
}
