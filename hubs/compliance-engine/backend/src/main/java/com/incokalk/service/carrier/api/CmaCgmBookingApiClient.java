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
public class CmaCgmBookingApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.providers.cmacgm.base-url:https://api.cma-cgm.com/booking/v1}")
    private String baseUrl;

    @Value("${incokalk.providers.cmacgm.api-key:}")
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
            body.put("customerReference", shipment.getOrderNumber());
            body.put("productCode", request.getServiceType() != null ? request.getServiceType() : "FCL");
            body.put("equipmentQuantity", shipment.getPackagesCount() != null ? shipment.getPackagesCount() : 1);

            Map<String, Object> cargo = new LinkedHashMap<>();
            cargo.put("commodity", shipment.getGoodsDescription());
            cargo.put("grossWeight", shipment.getWeightKg());
            cargo.put("volume", shipment.getVolumeM3());
            cargo.put("dg", shipment.isDangerous());
            body.put("cargo", cargo);

            Map<String, Object> route = new LinkedHashMap<>();
            route.put("loadPort", shipment.getShipperCountry());
            route.put("dischargePort", shipment.getConsigneeCountry());
            route.put("readyDate", request.getRequestedPickupDate() != null
                ? request.getRequestedPickupDate().toString()
                : LocalDate.now().plusDays(3).toString());
            body.put("transport", route);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/bookings", HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
            return null;
        } catch (Exception e) {
            log.error("[CMA-CGM-API] Erreur reservation: {}", e.getMessage());
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
            log.error("[CMA-CGM-API] Erreur statut: {}", e.getMessage());
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
            log.error("[CMA-CGM-API] Erreur annulation: {}", e.getMessage());
            return false;
        }
    }

    private BookingResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            BookingResponse resp = new BookingResponse();
            resp.setAccepted(true);
            resp.setCarrierReference(root.path("bookingRef").asText(
                "CMA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            resp.setTrackingNumber(root.path("containerNumber").asText(
                "CMAU" + (System.currentTimeMillis() % 100000000L)));
            resp.setQuotedCost(BigDecimal.valueOf(root.path("totalAmount").asDouble(0)));
            resp.setCurrency("EUR");
            resp.setEstimatedPickupDate(root.path("estimatedDeparture").asText(
                LocalDate.now().plusDays(4).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            resp.setEstimatedTransitDays(root.path("transitDays").asInt(28));
            resp.setEstimatedDeliveryDate(root.path("estimatedArrival").asText(
                LocalDate.now().plusDays(32).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            return resp;
        } catch (Exception e) {
            log.error("[CMA-CGM-API] Erreur parsing: {}", e.getMessage());
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
                "portOfLoading", root.path("loadPort").asText("")
            ));
            return resp;
        } catch (Exception e) {
            log.error("[CMA-CGM-API] Erreur parsing statut: {}", e.getMessage());
            return null;
        }
    }
}
