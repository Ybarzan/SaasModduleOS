package com.incokalk.service.carrier;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.carrier.api.DhlBookingApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DHLAdapter implements CarrierAdapter {

    private final DhlBookingApiClient dhlApiClient;

    @Override
    public String getCarrierCode() { return "DHL"; }

    @Override
    public boolean supports(String carrierCode) {
        return "DHL".equalsIgnoreCase(carrierCode) || "DHL_EXPRESS".equalsIgnoreCase(carrierCode);
    }

    @Override
    public BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        BookingResponse apiResponse = dhlApiClient.submitBooking(carrier, shipmentOrder, bookingRequest);
        if (apiResponse != null) return apiResponse;

        return simulateBooking(carrier, shipmentOrder, bookingRequest);
    }

    @Override
    public BookingResponse getBookingStatus(String carrierReference) {
        BookingResponse apiResponse = dhlApiClient.getStatus(carrierReference);
        if (apiResponse != null) return apiResponse;

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setSimulated(true);
        resp.setCarrierReference(carrierReference);
        resp.setAdditionalData(Map.of("currentStatus", "IN_TRANSIT", "lastScan", "Hub Rotterdam"));
        return resp;
    }

    @Override
    public boolean cancelBooking(String carrierReference) {
        if (dhlApiClient.isConfigured()) {
            return dhlApiClient.cancelBooking(carrierReference);
        }
        return true;
    }

    private BookingResponse simulateBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        String ref = "DHL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String tracking = "DHL" + System.currentTimeMillis() % 1000000000L;

        String mode = getModeFromCarrier(carrier);
        boolean isAir = "AIR".equalsIgnoreCase(mode);
        String svcType = bookingRequest.getServiceType() != null ? bookingRequest.getServiceType().toUpperCase() : "STANDARD";

        BigDecimal baseCost = isAir ? BigDecimal.valueOf(1250.00) : BigDecimal.valueOf(450.00);
        if (shipmentOrder.getWeightKg() != null && shipmentOrder.getWeightKg() > 100) {
            baseCost = baseCost.add(BigDecimal.valueOf(shipmentOrder.getWeightKg() * 2.5));
        }
        if ("EXPRESS".equals(svcType)) {
            baseCost = baseCost.multiply(BigDecimal.valueOf(1.3)).setScale(2, RoundingMode.HALF_UP);
        } else if ("ECONOMY".equals(svcType)) {
            baseCost = baseCost.multiply(BigDecimal.valueOf(0.85)).setScale(2, RoundingMode.HALF_UP);
        }

        LocalDate pickup = bookingRequest.getRequestedPickupDate() != null
            ? bookingRequest.getRequestedPickupDate()
            : LocalDate.now().plusDays(1);
        int transitDays;
        if (isAir) {
            transitDays = "EXPRESS".equals(svcType) ? 2 : 4;
        } else {
            transitDays = "EXPRESS".equals(svcType) ? 3 : "ECONOMY".equals(svcType) ? 7 : 5;
        }
        LocalDate delivery = pickup.plusDays(transitDays);

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setSimulated(true);
        resp.setCarrierReference(ref);
        resp.setTrackingNumber(tracking);
        resp.setQuotedCost(baseCost);
        resp.setCurrency("EUR");
        resp.setEstimatedPickupDate(pickup.format(DateTimeFormatter.ISO_LOCAL_DATE));
        resp.setEstimatedTransitDays(transitDays);
        resp.setEstimatedDeliveryDate(delivery.format(DateTimeFormatter.ISO_LOCAL_DATE));
        resp.setAdditionalData(Map.of("serviceType", svcType, "insuranceIncluded", true, "source", "simulation"));
        return resp;
    }

    private String getModeFromCarrier(Carrier carrier) {
        String modes = carrier.getTransportModes();
        if (modes == null || modes.isEmpty()) return "ROAD";
        return modes.split(",")[0].trim();
    }
}
