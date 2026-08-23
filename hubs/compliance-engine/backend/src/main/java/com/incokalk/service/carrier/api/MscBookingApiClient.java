package com.incokalk.service.carrier.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MscBookingApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.providers.msc.base-url:https://api.msc.com/booking/v1}")
    private String baseUrl;

    @Value("${incokalk.providers.msc.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipment, CarrierBookingRequest request) {
        if (!isConfigured()) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reference", shipment.getOrderNumber());
            body.put("serviceType", request.getServiceType() != null ? request.getServiceType() : "FCL");
            body.put("containerCount", shipment.getPackagesCount() != null ? shipment.getPackagesCount() : 1);

            Map<String, Object> cargo = new LinkedHashMap<>();
            cargo.put("description", shipment.getGoodsDescription());
            cargo.put("weight", shipment.getWeightKg());
            cargo.put("volume", shipment.getVolumeM3());
            cargo.put("isDangerous", shipment.isDangerous());
            body.put("cargo", cargo);

            Map<String, Object> route = new LinkedHashMap<>();
            route.put("origin", shipment.getShipperCountry());
            route.put("destination", shipment.getConsigneeCountry());
            route.put("pickupDate", request.getRequestedPickupDate() != null
                ? request.getRequestedPickupDate().toString()
                : LocalDate.now().plusDays(3).toString());
            body.put("route", route);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/bookings", HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
            return null;
        } catch (Exception e) {
            log.error("[MSC-API] Erreur réservation: {}", e.getMessage());
            return null;
        }
    }

    public BookingResponse getStatus(String carrierReference) {
        if (!isConfigured()) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/bookings/" + carrierReference, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseStatusResponse(response.getBody(), carrierReference);
            }
            return null;
        } catch (Exception e) {
            log.error("[MSC-API] Erreur statut: {}", e.getMessage());
            return null;
        }
    }

    public boolean cancelBooking(String carrierReference) {
        if (!isConfigured()) return false;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/bookings/" + carrierReference + "/cancel",
                HttpMethod.POST, entity, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("[MSC-API] Erreur annulation: {}", e.getMessage());
            return false;
        }
    }

    private BookingResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            BookingResponse resp = new BookingResponse();
            resp.setAccepted(true);
            resp.setCarrierReference(root.path("bookingReference").asText(
                "MSC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            resp.setTrackingNumber(root.path("containerNumber").asText(
                "MSCU" + (System.currentTimeMillis() % 100000000L)));
            resp.setQuotedCost(BigDecimal.valueOf(root.path("totalCost").asDouble(0)));
            resp.setCurrency("EUR");
            resp.setEstimatedPickupDate(root.path("estimatedDeparture").asText(
                LocalDate.now().plusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            resp.setEstimatedTransitDays(root.path("estimatedTransitDays").asInt(25));
            resp.setEstimatedDeliveryDate(root.path("estimatedArrival").asText(
                LocalDate.now().plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            return resp;
        } catch (Exception e) {
            log.error("[MSC-API] Erreur parsing: {}", e.getMessage());
            return null;
        }
    }

    private BookingResponse parseStatusResponse(String json, String reference) {
        try {
            JsonNode root = objectMapper.readTree(json);
            BookingResponse resp = new BookingResponse();
            resp.setAccepted(true);
            resp.setCarrierReference(reference);
            resp.setAdditionalData(Map.of(
                "currentStatus", root.path("status").asText("IN_TRANSIT"),
                "vesselPosition", root.path("vesselPosition").asText(""),
                "portOfLoading", root.path("portOfLoading").asText("")
            ));
            return resp;
        } catch (Exception e) {
            log.error("[MSC-API] Erreur parsing statut: {}", e.getMessage());
            return null;
        }
    }
}
