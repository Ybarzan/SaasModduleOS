package com.incokalk.controller;

import com.incokalk.model.LandedCost;
import com.incokalk.service.LandedCostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LandedCostControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private LandedCostService landedCostService;

    private LandedCost sample() {
        return LandedCost.builder()
            .id(UUID.randomUUID())
            .originCountry("CN")
            .destinationCountry("FR")
            .incoterm("FOB")
            .hsCode("850110")
            .transportMode("SEA")
            .productValue(BigDecimal.valueOf(1000))
            .currency("EUR")
            .totalLandedCost(BigDecimal.valueOf(1200))
            .build();
    }

    // ── GET / ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/landed-costs → 200 liste")
    void list_success() throws Exception {
        when(landedCostService.getAll()).thenReturn(List.of(sample()));

        mockMvc.perform(get("/v1/landed-costs").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].originCountry").value("CN"));
    }

    // ── GET /{id} ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/landed-costs/{id} → 200")
    void getById_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(landedCostService.getById(id)).thenReturn(sample());

        mockMvc.perform(get("/v1/landed-costs/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hsCode").value("850110"));
    }

    @Test
    @DisplayName("GET /v1/landed-costs/{id} → 400 si introuvable")
    void getById_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(landedCostService.getById(id)).thenThrow(new IllegalArgumentException("Coût débarqué introuvable"));

        mockMvc.perform(get("/v1/landed-costs/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Coût débarqué introuvable"));
    }

    // ── POST /calculate ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /v1/landed-costs/calculate → 201 avec champs minimaux (valeurs par défaut)")
    void calculate_success_minimal() throws Exception {
        when(landedCostService.calculate(any())).thenReturn(sample());

        mockMvc.perform(post("/v1/landed-costs/calculate")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"originCountry":"CN","destinationCountry":"FR","productValue":1000}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.originCountry").value("CN"));
    }

    @Test
    @DisplayName("POST /v1/landed-costs/calculate → 201 avec tous les champs renseignés")
    void calculate_success_full() throws Exception {
        when(landedCostService.calculate(any())).thenReturn(sample());

        mockMvc.perform(post("/v1/landed-costs/calculate")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"calculationName":"Test Calc","originCountry":"CN","destinationCountry":"FR",
                     "incoterm":"CIF","hsCode":"850110","transportMode":"AIR","productValue":1000,
                     "currency":"USD","freightCost":100,"insuranceCost":50,"portCharges":30,
                     "customsFees":20,"handlingFees":10,"lastMileCost":15,"unitCount":5,
                     "sellingPrice":2000,"notes":"Des notes"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.originCountry").value("CN"));
    }

    @Test
    @DisplayName("POST /v1/landed-costs/calculate → 400 si le service refuse")
    void calculate_rejected() throws Exception {
        when(landedCostService.calculate(any())).thenThrow(new IllegalArgumentException("Entreprise introuvable"));

        mockMvc.perform(post("/v1/landed-costs/calculate")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"originCountry":"CN","destinationCountry":"FR","productValue":1000}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Entreprise introuvable"));
    }

    // ── PUT /{id} ────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /v1/landed-costs/{id} → 200 avec champs minimaux (valeurs par défaut)")
    void update_success_minimal() throws Exception {
        UUID id = UUID.randomUUID();
        when(landedCostService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/landed-costs/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"originCountry":"CN","destinationCountry":"FR","productValue":1000}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originCountry").value("CN"));
    }

    @Test
    @DisplayName("PUT /v1/landed-costs/{id} → 200 avec tous les champs renseignés")
    void update_success_full() throws Exception {
        UUID id = UUID.randomUUID();
        when(landedCostService.update(eq(id), any())).thenReturn(sample());

        mockMvc.perform(put("/v1/landed-costs/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"calculationName":"Test Calc","originCountry":"CN","destinationCountry":"FR",
                     "incoterm":"CIF","hsCode":"850110","transportMode":"AIR","productValue":1000,
                     "currency":"USD","freightCost":100,"insuranceCost":50,"portCharges":30,
                     "customsFees":20,"handlingFees":10,"lastMileCost":15,"unitCount":5,
                     "sellingPrice":2000,"notes":"Des notes"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originCountry").value("CN"));
    }

    @Test
    @DisplayName("PUT /v1/landed-costs/{id} → 400 si introuvable")
    void update_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(landedCostService.update(eq(id), any()))
            .thenThrow(new IllegalArgumentException("Coût débarqué introuvable"));

        mockMvc.perform(put("/v1/landed-costs/" + id)
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"originCountry":"CN","destinationCountry":"FR","productValue":1000}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Coût débarqué introuvable"));
    }

    // ── DELETE /{id} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /v1/landed-costs/{id} → 200")
    void delete_success() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/landed-costs/" + id).header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Coût débarqué supprimé"));
    }

    @Test
    @DisplayName("DELETE /v1/landed-costs/{id} → 400 si introuvable")
    void delete_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Coût débarqué introuvable"))
            .when(landedCostService).delete(id);

        mockMvc.perform(delete("/v1/landed-costs/" + id).header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Coût débarqué introuvable"));
    }

    // ── POST /from-shipment/{shipmentId} ────────────────────────────────

    @Test
    @DisplayName("POST /v1/landed-costs/from-shipment/{shipmentId} → 201")
    void createFromShipment_success() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(landedCostService.createFromShipment(shipmentId)).thenReturn(sample());

        mockMvc.perform(post("/v1/landed-costs/from-shipment/" + shipmentId)
                .header("Authorization", authHeader()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.originCountry").value("CN"));
    }

    @Test
    @DisplayName("POST /v1/landed-costs/from-shipment/{shipmentId} → 400 si introuvable")
    void createFromShipment_notFound() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(landedCostService.createFromShipment(shipmentId))
            .thenThrow(new IllegalArgumentException("Ordre de shipment introuvable"));

        mockMvc.perform(post("/v1/landed-costs/from-shipment/" + shipmentId)
                .header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Ordre de shipment introuvable"));
    }

    // ── POST /what-if ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /v1/landed-costs/what-if → 200 avec scénario minimal (valeurs par défaut)")
    void whatIf_success_minimal() throws Exception {
        when(landedCostService.compareScenarios(any())).thenReturn(List.of(Map.of("originCountry", "CN")));

        mockMvc.perform(post("/v1/landed-costs/what-if")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    [{"originCountry":"CN","destinationCountry":"FR","productValue":1000}]
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].originCountry").value("CN"));
    }

    @Test
    @DisplayName("POST /v1/landed-costs/what-if → 200 avec scénarios complets (plusieurs)")
    void whatIf_success_full() throws Exception {
        when(landedCostService.compareScenarios(any())).thenReturn(
            List.of(Map.of("originCountry", "CN"), Map.of("originCountry", "US")));

        mockMvc.perform(post("/v1/landed-costs/what-if")
                .header("Authorization", authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    [
                      {"calculationName":"Scénario A","originCountry":"CN","destinationCountry":"FR",
                       "incoterm":"CIF","hsCode":"850110","transportMode":"AIR","productValue":1000,
                       "currency":"USD","freightCost":100,"insuranceCost":50,"portCharges":30,
                       "customsFees":20,"handlingFees":10,"lastMileCost":15,"unitCount":5,
                       "sellingPrice":2000,"notes":"Des notes"},
                      {"calculationName":"Scénario B","originCountry":"US","destinationCountry":"FR",
                       "incoterm":"CIF","hsCode":"850110","transportMode":"AIR","productValue":2000,
                       "currency":"USD","freightCost":200,"insuranceCost":75,"portCharges":40,
                       "customsFees":25,"handlingFees":15,"lastMileCost":20,"unitCount":3,
                       "sellingPrice":3000,"notes":"Autres notes"}
                    ]
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].originCountry").value("US"));
    }

    // ── POST /{id}/share ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /v1/landed-costs/{id}/share → 200")
    void share_success() throws Exception {
        UUID id = UUID.randomUUID();
        when(landedCostService.generateShareToken(id)).thenReturn("abc123token");

        mockMvc.perform(post("/v1/landed-costs/" + id + "/share").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("abc123token"))
            .andExpect(jsonPath("$.shareUrl").value("/s/landed-cost/abc123token"));
    }

    @Test
    @DisplayName("POST /v1/landed-costs/{id}/share → 400 si introuvable")
    void share_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(landedCostService.generateShareToken(id))
            .thenThrow(new IllegalArgumentException("Coût débarqué introuvable"));

        mockMvc.perform(post("/v1/landed-costs/" + id + "/share").header("Authorization", authHeader()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Coût débarqué introuvable"));
    }

    // ── GET /public/{token} ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/landed-costs/public/{token} → 200 (sans authentification)")
    void getPublic_success() throws Exception {
        when(landedCostService.getByShareToken("abc123token")).thenReturn(sample());

        mockMvc.perform(get("/v1/landed-costs/public/abc123token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originCountry").value("CN"));
    }

    @Test
    @DisplayName("GET /v1/landed-costs/public/{token} → 400 si lien invalide")
    void getPublic_notFound() throws Exception {
        when(landedCostService.getByShareToken("bad-token"))
            .thenThrow(new IllegalArgumentException("Lien de partage invalide ou expiré"));

        mockMvc.perform(get("/v1/landed-costs/public/bad-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Lien de partage invalide ou expiré"));
    }

    // ── GET /stats ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/landed-costs/stats → 200")
    void stats() throws Exception {
        when(landedCostService.getStats()).thenReturn(Map.of("total", 7));

        mockMvc.perform(get("/v1/landed-costs/stats").header("Authorization", authHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(7));
    }
}
