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
public class DbSchenkerBookingApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.providers.dbschenker.base-url:https://api.dbschenker.com/booking/v1}")
    private String baseUrl;

    @Value("${incokalk.providers.dbschenker.api-key:}")
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

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("customerReference", shipment.getOrderNumber());
            body.put("serviceType", request.getServiceType() != null ? request.getServiceType() : "STANDARD");

            if (carrier.getTransportModes() != null) {
                body.put("transportMode", carrier.getTransportModes().split(",")[0].trim());
            }

            Map<String, Object> shipmentDetails = new LinkedHashMap<>();
            shipmentDetails.put("goodsDescription", shipment.getGoodsDescription());
            shipmentDetails.put("weightKg", shipment.getWeightKg());
            shipmentDetails.put("volumeM3", shipment.getVolumeM3());
            shipmentDetails.put("packageCount", shipment.getPackagesCount());
            shipmentDetails.put("isDangerous", shipment.isDangerous());
            body.put("shipment", shipmentDetails);

            Map<String, Object> pickup = new LinkedHashMap<>();
            pickup.put("company", shipment.getShipperName());
            pickup.put("address", shipment.getShipperAddress());
            pickup.put("city", shipment.getShipperCity());
            pickup.put("country", shipment.getShipperCountry());
            pickup.put("requestedDate", request.getRequestedPickupDate() != null
                ? request.getRequestedPickupDate().toString()
                : LocalDate.now().plusDays(2).toString());
            body.put("pickup", pickup);

            Map<String, Object> delivery = new LinkedHashMap<>();
            delivery.put("company", shipment.getConsigneeName());
            delivery.put("address", shipment.getConsigneeAddress());
            delivery.put("city", shipment.getConsigneeCity());
            delivery.put("country", shipment.getConsigneeCountry());
            body.put("delivery", delivery);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/orders", HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
            return null;
        } catch (Exception e) {
            log.error("[DBS-API] Erreur réservation: {}", e.getMessage());
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
                baseUrl + "/orders/" + carrierReference + "/status",
                HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseStatusResponse(response.getBody(), carrierReference);
            }
            return null;
        } catch (Exception e) {
            log.error("[DBS-API] Erreur statut: {}", e.getMessage());
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
                baseUrl + "/orders/" + carrierReference + "/cancel",
                HttpMethod.POST, entity, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("[DBS-API] Erreur annulation: {}", e.getMessage());
            return false;
        }
    }

    private BookingResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            BookingResponse resp = new BookingResponse();
            resp.setAccepted(true);
            resp.setCarrierReference(root.path("orderNumber").asText(
                "DBS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            resp.setTrackingNumber(root.path("trackingNumber").asText(
                "DB" + (System.currentTimeMillis() % 100000000L)));
            resp.setQuotedCost(BigDecimal.valueOf(root.path("totalCost").asDouble(0)));
            resp.setCurrency("EUR");
            resp.setEstimatedPickupDate(root.path("estimatedPickup").asText(
                LocalDate.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            resp.setEstimatedTransitDays(root.path("estimatedTransitDays").asInt(5));
            resp.setEstimatedDeliveryDate(root.path("estimatedDelivery").asText(
                LocalDate.now().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            return resp;
        } catch (Exception e) {
            log.error("[DBS-API] Erreur parsing: {}", e.getMessage());
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
                "lastCheckpoint", root.path("lastCheckpoint").asText("")
            ));
            return resp;
        } catch (Exception e) {
            log.error("[DBS-API] Erreur parsing statut: {}", e.getMessage());
            return null;
        }
    }
}
