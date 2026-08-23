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
public class DhlBookingApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${incokalk.providers.dhl.base-url:https://api-eu.dhl.com/mydhlapi}")
    private String baseUrl;

    @Value("${incokalk.providers.dhl.api-key:}")
    private String apiKey;

    @Value("${incokalk.providers.dhl.api-secret:}")
    private String apiSecret;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipment, CarrierBookingRequest request) {
        if (!isConfigured()) {
            log.info("[DHL-API] Non configuré (clé API manquante), fallback simulation");
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(apiKey, apiSecret != null ? apiSecret : "");
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            Map<String, Object> body = buildBookingRequest(carrier, shipment, request);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = baseUrl + "/shipments";
            log.info("[DHL-API] Envoi réservation à {}", url);
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseResponse(response.getBody());
            }
            log.warn("[DHL-API] Réponse inattendue: {}", response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("[DHL-API] Erreur réservation: {}", e.getMessage());
            return null;
        }
    }

    public BookingResponse getStatus(String carrierReference) {
        if (!isConfigured()) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(apiKey, apiSecret != null ? apiSecret : "");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/shipments/" + carrierReference,
                HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseStatusResponse(response.getBody(), carrierReference);
            }
            return null;
        } catch (Exception e) {
            log.error("[DHL-API] Erreur statut: {}", e.getMessage());
            return null;
        }
    }

    public boolean cancelBooking(String carrierReference) {
        if (!isConfigured()) return false;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(apiKey, apiSecret != null ? apiSecret : "");
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/shipments/" + carrierReference + "/cancel",
                HttpMethod.DELETE, entity, String.class);

            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("[DHL-API] Erreur annulation: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildBookingRequest(Carrier carrier, ShipmentOrder shipment, CarrierBookingRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        Map<String, Object> plannedShippingDateAndTime = new LinkedHashMap<>();
        plannedShippingDateAndTime.put("date", request.getRequestedPickupDate() != null
            ? request.getRequestedPickupDate().toString()
            : LocalDate.now().plusDays(1).toString());
        body.put("plannedShippingDateAndTime", plannedShippingDateAndTime);

        Map<String, Object> shipper = new LinkedHashMap<>();
        shipper.put("companyName", shipment.getShipperName());
        shipper.put("addressLine1", shipment.getShipperAddress());
        shipper.put("city", shipment.getShipperCity());
        shipper.put("countryCode", shipment.getShipperCountry());
        body.put("shipper", shipper);

        Map<String, Object> receiver = new LinkedHashMap<>();
        receiver.put("companyName", shipment.getConsigneeName());
        receiver.put("addressLine1", shipment.getConsigneeAddress());
        receiver.put("city", shipment.getConsigneeCity());
        receiver.put("countryCode", shipment.getConsigneeCountry());
        body.put("receiver", receiver);

        List<Map<String, Object>> packages = new ArrayList<>();
        Map<String, Object> pkg = new LinkedHashMap<>();
        if (shipment.getWeightKg() != null) {
            Map<String, Object> weight = new LinkedHashMap<>();
            weight.put("value", shipment.getWeightKg());
            weight.put("uom", "kg");
            pkg.put("weight", weight);
        }
        packages.add(pkg);
        body.put("packages", packages);

        body.put("shipmentType", request.getServiceType() != null ? request.getServiceType() : "STANDARD");
        body.put("description", shipment.getGoodsDescription());

        return body;
    }

    private BookingResponse parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            BookingResponse resp = new BookingResponse();
            resp.setAccepted(true);
            resp.setCarrierReference(root.path("shipmentTrackingNumber").asText(
                "DHL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            resp.setTrackingNumber(root.path("trackingNumber").asText(
                "DHL" + System.currentTimeMillis() % 1000000000L));

            if (root.has("totalPrice") && root.path("totalPrice").isArray() && root.path("totalPrice").size() > 0) {
                resp.setQuotedCost(BigDecimal.valueOf(
                    root.path("totalPrice").get(0).path("price").asDouble(0)));
            }

            resp.setCurrency("EUR");
            resp.setEstimatedPickupDate(root.path("estimatedPickupDate").asText(
                LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            resp.setEstimatedTransitDays(root.path("estimatedTransitDays").asInt(3));
            resp.setEstimatedDeliveryDate(root.path("estimatedDeliveryDate").asText(
                LocalDate.now().plusDays(4).format(DateTimeFormatter.ISO_LOCAL_DATE)));
            return resp;
        } catch (Exception e) {
            log.error("[DHL-API] Erreur parsing réponse: {}", e.getMessage());
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
                "lastCheckpoint", root.path("estimatedTimeOfDelivery").asText("")
            ));
            return resp;
        } catch (Exception e) {
            log.error("[DHL-API] Erreur parsing statut: {}", e.getMessage());
            return null;
        }
    }
}
