package com.incokalk.service.carrier;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.carrier.api.MscBookingApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MSCAdapter implements CarrierAdapter {

    private final MscBookingApiClient mscApiClient;

    @Override
    public String getCarrierCode() { return "MSC"; }

    @Override
    public boolean supports(String carrierCode) {
        return "MSC".equalsIgnoreCase(carrierCode) || "CMA_CGM".equalsIgnoreCase(carrierCode);
    }

    @Override
    public BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        BookingResponse apiResponse = mscApiClient.submitBooking(carrier, shipmentOrder, bookingRequest);
        if (apiResponse != null) return apiResponse;

        return simulateBooking(carrier, shipmentOrder, bookingRequest);
    }

    @Override
    public BookingResponse getBookingStatus(String carrierReference) {
        BookingResponse apiResponse = mscApiClient.getStatus(carrierReference);
        if (apiResponse != null) return apiResponse;

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setCarrierReference(carrierReference);
        resp.setAdditionalData(Map.of("currentStatus", "IN_TRANSIT", "vesselPosition", "3.2N 101.5E"));
        return resp;
    }

    @Override
    public boolean cancelBooking(String carrierReference) {
        if (mscApiClient.isConfigured()) {
            return mscApiClient.cancelBooking(carrierReference);
        }
        return true;
    }

    private BookingResponse simulateBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        String ref = "MSC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String blNumber = "MSCU" + (System.currentTimeMillis() % 100000000L);

        String svcType = bookingRequest.getServiceType() != null ? bookingRequest.getServiceType().toUpperCase() : "FCL";
        boolean isFcl = svcType.contains("FCL") || svcType.contains("FULL");

        BigDecimal baseCost = BigDecimal.valueOf(1800.00);
        if (isFcl) {
            baseCost = BigDecimal.valueOf(2400.00);
        }
        if (shipmentOrder.getVolumeM3() != null) {
            baseCost = baseCost.add(BigDecimal.valueOf(shipmentOrder.getVolumeM3() * (isFcl ? 60 : 85)));
        }
        if (Boolean.TRUE.equals(shipmentOrder.isDangerous())) {
            baseCost = baseCost.add(BigDecimal.valueOf(350.00));
        }

        LocalDate departure = bookingRequest.getRequestedPickupDate() != null
            ? bookingRequest.getRequestedPickupDate().plusDays(3)
            : LocalDate.now().plusDays(4);
        int transitDays = isFcl ? 35 : 25;
        LocalDate arrival = departure.plusDays(transitDays);

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setCarrierReference(ref);
        resp.setTrackingNumber(blNumber);
        resp.setQuotedCost(baseCost);
        resp.setCurrency("EUR");
        resp.setEstimatedPickupDate(departure.minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE));
        resp.setEstimatedTransitDays(transitDays);
        resp.setEstimatedDeliveryDate(arrival.format(DateTimeFormatter.ISO_LOCAL_DATE));
        resp.setAdditionalData(Map.of(
            "serviceType", svcType,
            "vesselName", "MSC GULSUN",
            "portOfLoading", "ROTTERDAM",
            "portOfDischarge", isFcl ? "SINGAPORE" : "HAMBURG",
            "source", "simulation"
        ));
        return resp;
    }

    private String getModeFromCarrier(Carrier carrier) {
        String modes = carrier.getTransportModes();
        if (modes == null || modes.isEmpty()) return "SEA";
        return modes.split(",")[0].trim();
    }
}
