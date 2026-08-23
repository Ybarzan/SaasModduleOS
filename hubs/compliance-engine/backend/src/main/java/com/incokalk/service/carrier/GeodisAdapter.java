package com.incokalk.service.carrier;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.carrier.api.GeodisBookingApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GeodisAdapter implements CarrierAdapter {

    private final GeodisBookingApiClient geodisApiClient;

    @Override
    public String getCarrierCode() { return "GEODIS"; }

    @Override
    public boolean supports(String carrierCode) {
        return "GEODIS".equalsIgnoreCase(carrierCode) || "BOLLORE".equalsIgnoreCase(carrierCode);
    }

    @Override
    public BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        BookingResponse apiResponse = geodisApiClient.submitBooking(carrier, shipmentOrder, bookingRequest);
        if (apiResponse != null) return apiResponse;

        return simulateBooking(carrier, shipmentOrder, bookingRequest);
    }

    @Override
    public BookingResponse getBookingStatus(String carrierReference) {
        BookingResponse apiResponse = geodisApiClient.getStatus(carrierReference);
        if (apiResponse != null) return apiResponse;

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setSimulated(true);
        resp.setCarrierReference(carrierReference);
        resp.setAdditionalData(Map.of("currentStatus", "IN_TRANSIT", "lastCheckpoint", "Paris物流Hub"));
        return resp;
    }

    @Override
    public boolean cancelBooking(String carrierReference) {
        if (geodisApiClient.isConfigured()) {
            return geodisApiClient.cancelBooking(carrierReference);
        }
        return true;
    }

    private BookingResponse simulateBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        String ref = "GEO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String proNumber = "GE" + (System.currentTimeMillis() % 100000000L);

        String svcType = bookingRequest.getServiceType() != null ? bookingRequest.getServiceType().toUpperCase() : "ROAD_FREIGHT";
        boolean isTempControlled = svcType.contains("TEMP") || svcType.contains("REFRIGERATED");
        boolean isExpress = "EXPRESS".equals(svcType);

        BigDecimal baseCost = BigDecimal.valueOf(620.00);
        if (shipmentOrder.getWeightKg() != null && shipmentOrder.getWeightKg() > 500) {
            baseCost = baseCost.add(BigDecimal.valueOf(shipmentOrder.getWeightKg() * 0.8));
        }
        if (shipmentOrder.getPackagesCount() != null && shipmentOrder.getPackagesCount() > 10) {
            baseCost = baseCost.add(BigDecimal.valueOf(75.00));
        }
        if (isTempControlled) {
            baseCost = baseCost.add(BigDecimal.valueOf(250.00));
        }
        if (isExpress) {
            baseCost = baseCost.multiply(BigDecimal.valueOf(1.25));
        }

        LocalDate pickup = bookingRequest.getRequestedPickupDate() != null
            ? bookingRequest.getRequestedPickupDate()
            : LocalDate.now().plusDays(1);
        int transitDays = isExpress ? 2 : isTempControlled ? 3 : 4;
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
            "serviceType", svcType,
            "vehicleType", isTempControlled ? "REFRIGERATED_20T" : "FTL_24T",
            "temperatureControlled", isTempControlled,
            "source", "simulation"
        ));
        return resp;
    }
}
