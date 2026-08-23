package com.incokalk.controller;

import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.User;
import com.incokalk.service.CarrierBookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que /v1/carrier-bookings/** applique effectivement le contrôle de rôle.
 *
 * Avant correctif, ces endpoints étaient annotés avec @PreAuthorize alors que la
 * sécurité de méthode Spring (@EnableMethodSecurity) n'est jamais activée dans ce
 * projet — seule l'AOP @RolesAllowed (RolesAllowedAspect) est réellement appliquée.
 * @PreAuthorize était donc silencieusement ignoré et ces endpoints (dont la soumission
 * et l'annulation d'une réservation transporteur, qui engagent financièrement la
 * société) étaient ouverts à n'importe quel utilisateur authentifié, quel que soit
 * son rôle.
 */
class CarrierBookingControllerTest extends ControllerTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @MockBean
    private CarrierBookingService bookingService;

    // La réservation expose companyId/userId/shipmentOrderId/carrierId via des
    // accesseurs @JsonProperty qui déréférencent les relations JPA : celles-ci
    // doivent donc être renseignées pour que la sérialisation JSON n'échoue pas.
    private CarrierBookingRequest fullyPopulatedBooking() {
        Company company = new Company();
        company.setId(companyId);
        User user = new User();
        user.setId(userId);
        Carrier carrier = new Carrier();
        carrier.setId(UUID.randomUUID());
        ShipmentOrder shipment = new ShipmentOrder();
        shipment.setId(UUID.randomUUID());

        CarrierBookingRequest booking = new CarrierBookingRequest();
        booking.setId(UUID.randomUUID());
        booking.setCompany(company);
        booking.setUser(user);
        booking.setCarrier(carrier);
        booking.setShipmentOrder(shipment);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.PENDING);
        return booking;
    }

    private String createBookingBody(UUID carrierId, UUID shipmentId) {
        return "{"
                + "\"carrierId\":\"" + carrierId + "\","
                + "\"shipmentOrderId\":\"" + shipmentId + "\","
                + "\"carrierName\":\"DHL\","
                + "\"carrierCode\":\"DHL\","
                + "\"carrierApiEndpoint\":\"https://api.dhl.test\","
                + "\"originCountry\":\"FR\","
                + "\"destinationCountry\":\"DE\","
                + "\"quantity\":1"
                + "}";
    }

    @Test
    @DisplayName("POST /v1/carrier-bookings → 200 pour un rôle USER (création autorisée à tous les rôles)")
    void createBooking_allowedForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        UUID carrierId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        CarrierBookingRequest booking = fullyPopulatedBooking();
        when(bookingService.createBooking(any(), any())).thenReturn(booking);

        mockMvc.perform(post("/v1/carrier-bookings")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBookingBody(carrierId, shipmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carrierBookingStatus").value("PENDING"));
    }

    @Test
    @DisplayName("POST /v1/carrier-bookings/{id}/submit → 403 pour un rôle USER (soumission = engagement financier réservé à OWNER/ADMIN/MANAGER)")
    void submitBooking_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/v1/carrier-bookings/" + bookingId + "/submit")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/carrier-bookings/{id}/submit → 200 pour un rôle MANAGER")
    void submitBooking_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        UUID bookingId = UUID.randomUUID();

        CarrierBookingRequest booking = fullyPopulatedBooking();
        booking.setId(bookingId);
        booking.setCarrierBookingStatus(CarrierBookingRequest.BookingStatus.CONFIRMED);
        when(bookingService.submitBooking(bookingId)).thenReturn(booking);

        mockMvc.perform(post("/v1/carrier-bookings/" + bookingId + "/submit")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrierBookingStatus").value("CONFIRMED"));
    }

    @Test
    @DisplayName("POST /v1/carrier-bookings/{id}/cancel → 403 pour un rôle USER (annulation réservée à OWNER/ADMIN/MANAGER)")
    void cancelBooking_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/v1/carrier-bookings/" + bookingId + "/cancel")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/carrier-bookings/stats → 403 pour un rôle USER (statistiques réservées à OWNER/ADMIN/MANAGER)")
    void getStats_forbiddenForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);

        mockMvc.perform(get("/v1/carrier-bookings/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /v1/carrier-bookings/stats → 200 pour un rôle MANAGER")
    void getStats_allowedForManager() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);
        when(bookingService.getStats()).thenReturn(Map.of("total", 5L, "pending", 2L));

        mockMvc.perform(get("/v1/carrier-bookings/stats")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    @DisplayName("GET /v1/carrier-bookings → 200 pour un rôle USER (lecture autorisée à tous les rôles)")
    void listBookings_allowedForUser() throws Exception {
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.USER);
        when(bookingService.listBookings()).thenReturn(List.of());

        mockMvc.perform(get("/v1/carrier-bookings")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk());
    }
}
