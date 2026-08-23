package com.incokalk.controller;

import com.incokalk.service.ShipmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShipmentService shipmentService;

    // ── /v1/webhooks/shippo ─────────────────────────────────────

    @Test
    @DisplayName("POST /v1/webhooks/shippo → 200 statut 'delivered' mappé en DELIVERED")
    void shippo_delivered() throws Exception {
        mockMvc.perform(post("/v1/webhooks/shippo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"ABC123","status":"delivered","location":"Paris","description":"Livré"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value("true"));

        verify(shipmentService).processWebhookEvent(
            eq("ABC123"), eq("DELIVERED"), eq("Paris"), eq("Livré"), eq("SHIPPO"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/shippo → 200 statut 'in_transit' mappé en IN_TRANSIT")
    void shippo_inTransit() throws Exception {
        mockMvc.perform(post("/v1/webhooks/shippo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"ABC123","status":"in_transit"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("ABC123"), eq("IN_TRANSIT"), any(), any(), eq("SHIPPO"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/shippo → 200 statut 'pre_transit' mappé en BOOKED")
    void shippo_preTransit() throws Exception {
        mockMvc.perform(post("/v1/webhooks/shippo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"ABC123","status":"pre_transit"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("ABC123"), eq("BOOKED"), any(), any(), eq("SHIPPO"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/shippo → 200 statut 'returned' mappé en CANCELLED")
    void shippo_returned() throws Exception {
        mockMvc.perform(post("/v1/webhooks/shippo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"ABC123","status":"returned"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("ABC123"), eq("CANCELLED"), any(), any(), eq("SHIPPO"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/shippo → 200 statut inconnu passé en majuscules (défaut)")
    void shippo_unknownStatus() throws Exception {
        mockMvc.perform(post("/v1/webhooks/shippo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"ABC123","status":"weird_status"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("ABC123"), eq("WEIRD_STATUS"), any(), any(), eq("SHIPPO"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/shippo → 200 sans tracking_number, service non appelé")
    void shippo_missingTrackingNumber() throws Exception {
        mockMvc.perform(post("/v1/webhooks/shippo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"delivered"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value("true"));

        verify(shipmentService, never()).processWebhookEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/webhooks/shippo → 200 sans status, service non appelé")
    void shippo_missingStatus() throws Exception {
        mockMvc.perform(post("/v1/webhooks/shippo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"ABC123"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService, never()).processWebhookEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/webhooks/shippo → 200 même si le service lève une exception (capturée)")
    void shippo_serviceThrows() throws Exception {
        doThrow(new RuntimeException("boom"))
            .when(shipmentService).processWebhookEvent(any(), any(), any(), any(), any());

        mockMvc.perform(post("/v1/webhooks/shippo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"ABC123","status":"delivered"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value("true"));
    }

    // ── /v1/webhooks/dhl ─────────────────────────────────────────

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 statut 'DELIVERED' mappé en DELIVERED")
    void dhl_delivered() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789","status":"DELIVERED","location":"Lyon","description":"Remis"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value("true"));

        verify(shipmentService).processWebhookEvent(
            eq("XYZ789"), eq("DELIVERED"), eq("Lyon"), eq("Remis"), eq("DHL"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 statut 'D5' mappé en DELIVERED")
    void dhl_d5() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789","status":"D5"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("XYZ789"), eq("DELIVERED"), any(), any(), eq("DHL"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 statut 'TRANSIT' mappé en IN_TRANSIT")
    void dhl_transit() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789","status":"TRANSIT"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("XYZ789"), eq("IN_TRANSIT"), any(), any(), eq("DHL"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 statut 'IT' mappé en IN_TRANSIT")
    void dhl_it() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789","status":"IT"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("XYZ789"), eq("IN_TRANSIT"), any(), any(), eq("DHL"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 statut 'CUSTOMS' mappé en IN_TRANSIT")
    void dhl_customs() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789","status":"CUSTOMS"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("XYZ789"), eq("IN_TRANSIT"), any(), any(), eq("DHL"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 statut 'FAILED' mappé en IN_TRANSIT")
    void dhl_failed() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789","status":"FAILED"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("XYZ789"), eq("IN_TRANSIT"), any(), any(), eq("DHL"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 statut inconnu passé en majuscules (défaut)")
    void dhl_unknownStatus() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789","status":"weird"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("XYZ789"), eq("WEIRD"), any(), any(), eq("DHL"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 sans trackingNumber, service non appelé")
    void dhl_missingTrackingNumber() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"DELIVERED"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService, never()).processWebhookEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 sans status, service non appelé")
    void dhl_missingStatus() throws Exception {
        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService, never()).processWebhookEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/webhooks/dhl → 200 même si le service lève une exception (capturée)")
    void dhl_serviceThrows() throws Exception {
        doThrow(new RuntimeException("boom"))
            .when(shipmentService).processWebhookEvent(any(), any(), any(), any(), any());

        mockMvc.perform(post("/v1/webhooks/dhl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"trackingNumber":"XYZ789","status":"DELIVERED"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value("true"));
    }

    // ── /v1/webhooks/generic ────────────────────────────────────

    @Test
    @DisplayName("POST /v1/webhooks/generic → 200 avec source explicite")
    void generic_withSource() throws Exception {
        mockMvc.perform(post("/v1/webhooks/generic")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"GEN1","status":"CUSTOM_STATUS","location":"Marseille",
                     "description":"desc","source":"CUSTOM_SOURCE"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value("true"));

        verify(shipmentService).processWebhookEvent(
            eq("GEN1"), eq("CUSTOM_STATUS"), eq("Marseille"), eq("desc"), eq("CUSTOM_SOURCE"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/generic → 200 sans source, défaut WEBHOOK")
    void generic_defaultSource() throws Exception {
        mockMvc.perform(post("/v1/webhooks/generic")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"GEN1","status":"CUSTOM_STATUS"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService).processWebhookEvent(
            eq("GEN1"), eq("CUSTOM_STATUS"), any(), any(), eq("WEBHOOK"));
    }

    @Test
    @DisplayName("POST /v1/webhooks/generic → 200 sans tracking_number, service non appelé")
    void generic_missingTrackingNumber() throws Exception {
        mockMvc.perform(post("/v1/webhooks/generic")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"CUSTOM_STATUS"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService, never()).processWebhookEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/webhooks/generic → 200 sans status, service non appelé")
    void generic_missingStatus() throws Exception {
        mockMvc.perform(post("/v1/webhooks/generic")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"GEN1"}
                    """))
            .andExpect(status().isOk());

        verify(shipmentService, never()).processWebhookEvent(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/webhooks/generic → 200 même si le service lève une exception (capturée)")
    void generic_serviceThrows() throws Exception {
        doThrow(new RuntimeException("boom"))
            .when(shipmentService).processWebhookEvent(any(), any(), any(), any(), any());

        mockMvc.perform(post("/v1/webhooks/generic")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tracking_number":"GEN1","status":"CUSTOM_STATUS"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.received").value("true"));
    }
}
