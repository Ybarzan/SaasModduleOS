package com.incokalk.service.carrier;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.carrier.api.DbSchenkerBookingApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DBSchenkerAdapter implements CarrierAdapter {

    private final DbSchenkerBookingApiClient dbSchenkerApiClient;

    @Override
    public String getCarrierCode() { return "DB_SCHENKER"; }

    @Override
    public boolean supports(String carrierCode) {
        return "DB_SCHENKER".equalsIgnoreCase(carrierCode)
            || "SCHENKER".equalsIgnoreCase(carrierCode)
            || "DB_SCHENKER_LOGISTICS".equalsIgnoreCase(carrierCode);
    }

    @Override
    public BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        BookingResponse apiResponse = dbSchenkerApiClient.submitBooking(carrier, shipmentOrder, bookingRequest);
        if (apiResponse != null) return apiResponse;

        return simulateBooking(carrier, shipmentOrder, bookingRequest);
    }

    @Override
    public BookingResponse getBookingStatus(String carrierReference) {
        BookingResponse apiResponse = dbSchenkerApiClient.getStatus(carrierReference);
        if (apiResponse != null) return apiResponse;

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setSimulated(true);
        resp.setCarrierReference(carrierReference);
        resp.setAdditionalData(Map.of("currentStatus", "IN_TRANSIT", "lastCheckpoint", "Nürnberg Terminal"));
        return resp;
    }

    @Override
    public boolean cancelBooking(String carrierReference) {
        if (dbSchenkerApiClient.isConfigured()) {
            return dbSchenkerApiClient.cancelBooking(carrierReference);
        }
        return true;
    }

    private BookingResponse simulateBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        String ref = "DBS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String proNumber = "DB" + (System.currentTimeMillis() % 100000000L);

        BigDecimal baseCost = BigDecimal.valueOf(540.00);
        if (shipmentOrder.getWeightKg() != null && shipmentOrder.getWeightKg() > 1000) {
            baseCost = baseCost.add(BigDecimal.valueOf(shipmentOrder.getWeightKg() * 0.65));
        }
        if (shipmentOrder.getVolumeM3() != null && shipmentOrder.getVolumeM3() > 5) {
            baseCost = baseCost.add(BigDecimal.valueOf(shipmentOrder.getVolumeM3() * 45));
        }
        if (Boolean.TRUE.equals(shipmentOrder.isDangerous())) {
            baseCost = baseCost.add(BigDecimal.valueOf(280.00));
        }

        boolean isExpress = "EXPRESS".equalsIgnoreCase(bookingRequest.getServiceType())
            || "AIR".equalsIgnoreCase(getModeFromCarrier(carrier));
        int transitDays = isExpress ? 2 : 6;
        boolean isRail = "RAIL".equalsIgnoreCase(getModeFromCarrier(carrier));
        if (isRail) {
            transitDays = 14;
            baseCost = baseCost.multiply(BigDecimal.valueOf(0.85));
        }

        LocalDate pickup = bookingRequest.getRequestedPickupDate() != null
            ? bookingRequest.getRequestedPickupDate()
            : LocalDate.now().plusDays(2);
        LocalDate delivery = pickup.plusDays(transitDays);

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setSimulated(true);
        resp.setCarrierReference(ref);
        resp.setTrackingNumber(proNumber);
        resp.setQuotedCost(baseCost);
        resp.setCurrency("EUR");
        resp.setEstimatedPickupDate(pickup.format(DateTimeFormatter.ISO_LOCAL_DATE));
        resp.setEstimatedTransitDays(transitDays);
        resp.setEstimatedDeliveryDate(delivery.format(DateTimeFormatter.ISO_LOCAL_DATE));
        resp.setAdditionalData(Map.of(
            "serviceType", isExpress ? "EXPRESS" : "STANDARD",
            "mode", isRail ? "RAIL" : "ROAD",
            "consolidationAvailable", true,
            "source", "simulation"
        ));
        return resp;
    }

    private String getModeFromCarrier(Carrier carrier) {
        String modes = carrier.getTransportModes();
        if (modes == null || modes.isEmpty()) return "ROAD";
        return modes.split(",")[0].trim();
    }
}
