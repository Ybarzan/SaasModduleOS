package com.incokalk.controller;

import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.SharedLink;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.TrackingEvent;
import com.incokalk.service.SharedLinkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SharedLinkControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private SharedLinkService sharedLinkService;

    private Company company() {
        return Company.builder()
            .id(companyId)
            .name("Acme Corp")
            .slug("acme")
            .logoUrl("https://acme.example/logo.png")
            .build();
    }

    private ShipmentOrder shipment(UUID id, Carrier carrier) {
        return ShipmentOrder.builder()
            .id(id)
            .orderNumber("ORD-001")
            .status(ShipmentOrder.Status.IN_TRANSIT)
            .carrier(carrier)
            .shipperCity("Paris")
            .shipperCountry("FR")
            .consigneeCity("Berlin")
            .consigneeCountry("DE")
            .goodsDescription("Electronics")
            .weightKg(120.0)
            .incotermCode("FOB")
            .estimatedDeliveryDate(LocalDateTime.now().plusDays(3))
            .createdAt(LocalDateTime.now())
            .trackingEvents(List.of())
            .build();
    }

    private SharedLink link(UUID shipmentId, LocalDateTime expiresAt) {
        return SharedLink.builder()
            .id(UUID.randomUUID())
            .company(company())
            .shipment(shipment(shipmentId, null))
            .token(UUID.randomUUID().toString())
            .label("Lien de suivi")
            .expiresAt(expiresAt)
            .active(true)
            .accessCount(0)
            .createdAt(LocalDateTime.now())
            .build();
    }

    // ── POST / (createLink) ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /v1/shared → 200 avec expiration, companyId/userId en UUID (attributs de requête)")
    void createLink_success_withExpiry_uuidAttrs() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        SharedLink created = link(shipmentId, LocalDateTime.now().plusHours(24));

        when(sharedLinkService.createLink(eq(companyId), eq(shipmentId), eq(userId), eq("Mon lien"), eq(24)))
            .thenReturn(created);

        mockMvc.perform(post("/v1/shared")
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"shipmentId":"%s","label":"Mon lien","expiresHours":24}
                    """.formatted(shipmentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(created.getToken()))
            .andExpect(jsonPath("$.label").value("Lien de suivi"))
            .andExpect(jsonPath("$.url").value("/s/" + created.getToken()))
            .andExpect(jsonPath("$.expiresAt").value(created.getExpiresAt().toString()));
    }

    @Test
    @DisplayName("POST /v1/shared → sans expiration (branche ternaire expiresAt=null) → 200 avec expiresAt absent")
    void createLink_noExpiry_returnsNullExpiresAt() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        SharedLink created = link(shipmentId, null);

        when(sharedLinkService.createLink(eq(companyId), eq(shipmentId), eq(userId), eq((String) null), eq((Integer) null)))
            .thenReturn(created);

        mockMvc.perform(post("/v1/shared")
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"shipmentId":"%s"}
                    """.formatted(shipmentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value(created.getToken()))
            .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    @DisplayName("POST /v1/shared → 403 sans authentification (Authorization absente)")
    void createLink_missingAuth_forbidden() throws Exception {
        UUID shipmentId = UUID.randomUUID();

        mockMvc.perform(post("/v1/shared")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"shipmentId":"%s"}
                    """.formatted(shipmentId)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/shared → 403 pour un rôle USER (gestion réservée à OWNER/ADMIN)")
    void createLink_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        UUID shipmentId = UUID.randomUUID();

        mockMvc.perform(post("/v1/shared")
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"shipmentId":"%s"}
                    """.formatted(shipmentId)))
            .andExpect(status().isForbidden());
    }

    // ── GET / (listLinks) ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/shared → 200 liste des liens de la company")
    void listLinks_success() throws Exception {
        SharedLink l = link(UUID.randomUUID(), null);
        when(sharedLinkService.listLinks(companyId)).thenReturn(List.of(l));

        mockMvc.perform(get("/v1/shared")
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].token").value(l.getToken()))
            .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    @DisplayName("GET /v1/shared → 403 pour un rôle USER (gestion réservée à OWNER/ADMIN)")
    void listLinks_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/shared")
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/shared → 200 pour un rôle ADMIN")
    void listLinks_allowedForAdmin() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.ADMIN);
        when(sharedLinkService.listLinks(companyId)).thenReturn(List.of());

        mockMvc.perform(get("/v1/shared")
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId))
            .andExpect(status().isOk());
    }

    // ── GET /shipment/{shipmentId} (linksForShipment) ───────────────────

    @Test
    @DisplayName("GET /v1/shared/shipment/{shipmentId} → 200 liens de la company courante uniquement")
    void linksForShipment_success() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        SharedLink l = link(shipmentId, null);
        when(sharedLinkService.listLinksForShipment(shipmentId, companyId)).thenReturn(List.of(l));

        mockMvc.perform(get("/v1/shared/shipment/" + shipmentId)
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].shipmentId").value(shipmentId.toString()));
    }

    @Test
    @DisplayName("GET /v1/shared/shipment/{shipmentId} → 403 sans authentification (RolesAllowed)")
    void linksForShipment_unauthenticated_forbidden() throws Exception {
        UUID shipmentId = UUID.randomUUID();

        mockMvc.perform(get("/v1/shared/shipment/" + shipmentId))
            .andExpect(status().isForbidden());
    }

    // ── DELETE /{id} (revokeLink) ────────────────────────────────────────

    @Test
    @DisplayName("DELETE /v1/shared/{id} → 200 révocation")
    void revokeLink_success() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/shared/" + id)
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Lien révoqué"));
    }

    @Test
    @DisplayName("DELETE /v1/shared/{id} → 403 pour un rôle USER (gestion réservée à OWNER/ADMIN)")
    void revokeLink_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/v1/shared/" + id)
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId))
            .andExpect(status().isForbidden());
    }

    // ── GET /stats (linkStats) ───────────────────────────────────────────

    @Test
    @DisplayName("GET /v1/shared/stats → 200 statistiques")
    void linkStats_success() throws Exception {
        when(sharedLinkService.linkStats(companyId)).thenReturn(Map.of(
            "totalLinks", 3, "activeLinks", 2, "totalAccesses", 10));

        mockMvc.perform(get("/v1/shared/stats")
                .header("Authorization", authHeader())
                .requestAttr("companyId", companyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalLinks").value(3))
            .andExpect(jsonPath("$.activeLinks").value(2));
    }

    // ── GET /access/{token} (accessSharedLink, public) ──────────────────

    @Test
    @DisplayName("GET /v1/shared/access/{token} → 200 avec carrier renseigné")
    void accessSharedLink_success_withCarrier() throws Exception {
        Carrier carrier = Carrier.builder().id(UUID.randomUUID()).name("DHL").code("DHL").transportModes("AIR").build();
        UUID shipmentId = UUID.randomUUID();
        ShipmentOrder shipment = shipment(shipmentId, carrier);
        TrackingEvent event = TrackingEvent.builder()
            .id(UUID.randomUUID())
            .shipment(shipment)
            .status("IN_TRANSIT")
            .location("Paris")
            .latitude(48.8)
            .longitude(2.3)
            .description("En transit")
            .eventTime(LocalDateTime.now())
            .source("carrier-api")
            .build();
        shipment.setTrackingEvents(List.of(event));

        SharedLink l = SharedLink.builder()
            .id(UUID.randomUUID())
            .company(company())
            .shipment(shipment)
            .token("tok-123")
            .label("Suivi colis")
            .active(true)
            .accessCount(1)
            .createdAt(LocalDateTime.now())
            .build();

        when(sharedLinkService.accessLink("tok-123")).thenReturn(l);

        mockMvc.perform(get("/v1/shared/access/tok-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shipment.carrierName").value("DHL"))
            .andExpect(jsonPath("$.shipment.status").value("IN_TRANSIT"))
            .andExpect(jsonPath("$.trackingEvents[0].status").value("IN_TRANSIT"))
            .andExpect(jsonPath("$.companyName").value("Acme Corp"))
            .andExpect(jsonPath("$.label").value("Suivi colis"));
    }

    @Test
    @DisplayName("GET /v1/shared/access/{token} → 200 sans carrier (carrierName null)")
    void accessSharedLink_success_withoutCarrier() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        ShipmentOrder shipment = shipment(shipmentId, null);
        shipment.setTrackingEvents(List.of());

        SharedLink l = SharedLink.builder()
            .id(UUID.randomUUID())
            .company(company())
            .shipment(shipment)
            .token("tok-456")
            .label("Suivi colis 2")
            .active(true)
            .accessCount(0)
            .createdAt(LocalDateTime.now())
            .build();

        when(sharedLinkService.accessLink("tok-456")).thenReturn(l);

        mockMvc.perform(get("/v1/shared/access/tok-456"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shipment.carrierName").doesNotExist())
            .andExpect(jsonPath("$.trackingEvents").isEmpty());
    }

    @Test
    @DisplayName("GET /v1/shared/access/{token} → 500 si le lien est introuvable/invalide")
    void accessSharedLink_notFound() throws Exception {
        when(sharedLinkService.accessLink("bad-token"))
            .thenThrow(new RuntimeException("Lien introuvable ou invalide"));

        mockMvc.perform(get("/v1/shared/access/bad-token"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("GET /v1/shared/access/{token} → 500 si le lien a expiré")
    void accessSharedLink_expired() throws Exception {
        when(sharedLinkService.accessLink("expired-token"))
            .thenThrow(new RuntimeException("Ce lien a expiré"));

        mockMvc.perform(get("/v1/shared/access/expired-token"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
