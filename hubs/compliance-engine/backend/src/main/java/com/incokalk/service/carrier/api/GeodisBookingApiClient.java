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
public class GeodisBookingApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.providers.geodis.base-url:https://api.geodis.com/booking/v1}")
    private String baseUrl;

    @Value("${incokalk.providers.geodis.api-key:}")
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
            body.put("reference", shipment.getOrderNumber());
            body.put("serviceCode", request.getServiceType() != null ? request.getServiceType() : "ROAD_FREIGHT");

            Map<String, Object> shipmentDetails = new LinkedHashMap<>();
            shipmentDetails.put("description", shipment.getGoodsDescription());
            shipmentDetails.put("weightKg", shipment.getWeightKg());
            shipmentDetails.put("volumeM3", shipment.getVolumeM3());
            shipmentDetails.put("packageCount", shipment.getPackagesCount());
            shipmentDetails.put("dangerousGoods", shipment.isDangerous());
            body.put("shipment", shipmentDetails);

            Map<String, Object> collection = new LinkedHashMap<>();
            collection.put("companyName", shipment.getShipperName());
            collection.put("address", shipment.getShipperAddress());
            collection.put("city", shipment.getShipperCity());
            collection.put("countryCode", shipment.getShipperCountry());
            collection.put("date", request.getRequestedPickupDate() != null
                ? request.getRequestedPickupDate().toString()
                : LocalDate.now().plusDays(1).toString());
            body.put("collection", collection);

            Map<String, Object> delivery = new LinkedHashMap<>();
            delivery.put("companyName", shipment.getConsigneeName());
            delivery.put("address", shipment.getConsigneeAddress());
            delivery.put("city", shipment.getConsigneeCity());
            delivery.put("countryCode", shipment.getConsigneeCountry());
            body.put("delivery", delivery);

            if (request.getSpecialInstructions() != null) {
                body.put("instructions", request.getSpecialInstructions());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/shipments", HttpMethod.POST, entity, String.class);

            if ((response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK)
                && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
            return null;
        } catch (Exception e) {
            log.error("[GEODIS-API] Erreur réservation: {}", e.getMessage());
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
                baseUrl + "/shipments/" + carrierReference + "/status",
                HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseStatusResponse(response.getBody(), carrierReference);
            }
            return null;
        } catch (Exception e) {
            log.error("[GEODIS-API] Erreur statut: {}", e.getMessage());
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
                baseUrl + "/shipments/" + carrierReference,
                HttpMethod.DELETE, entity, String.class);
            return response.getStatusCode() == HttpStatus.NO_CONTENT
                || response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("[GEODIS-API] Erreur annulation: {}", e.getMessage());
            return false;
        }
    }

    private BookingResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            BookingResponse resp = new BookingResponse();
            resp.setAccepted(true);
            resp.setCarrierReference(root.path("shipmentNumber").asText(
                "GEO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            resp.setTrackingNumber(root.path("trackingNumber").asText(
                "GE" + (System.currentTimeMillis() % 100000000L)));
            resp.setQuotedCost(BigDecimal.valueOf(root.path("totalPrice").asDouble(0)));
            resp.setCurrency("EUR");
            resp.setEstimatedPickupDate(root.path("estimatedPickupDate").asText(
                LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            resp.setEstimatedTransitDays(root.path("estimatedTransitDays").asInt(4));
            resp.setEstimatedDeliveryDate(root.path("estimatedDeliveryDate").asText(
                LocalDate.now().plusDays(5).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            return resp;
        } catch (Exception e) {
            log.error("[GEODIS-API] Erreur parsing: {}", e.getMessage());
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
                "lastCheckpoint", root.path("lastEvent").asText("")
            ));
            return resp;
        } catch (Exception e) {
            log.error("[GEODIS-API] Erreur parsing statut: {}", e.getMessage());
            return null;
        }
    }
}
