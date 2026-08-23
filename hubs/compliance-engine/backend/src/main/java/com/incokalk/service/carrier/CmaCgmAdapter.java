package com.incokalk.service.carrier;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.carrier.api.CmaCgmBookingApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CmaCgmAdapter implements CarrierAdapter {

    private final CmaCgmBookingApiClient cmaCgmApiClient;

    @Override
    public String getCarrierCode() { return "CMA_CGM"; }

    @Override
    public boolean supports(String carrierCode) {
        return "CMA_CGM".equalsIgnoreCase(carrierCode)
            || "CMA".equalsIgnoreCase(carrierCode)
            || "CMACGM".equalsIgnoreCase(carrierCode);
    }

    @Override
    public BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        BookingResponse apiResponse = cmaCgmApiClient.submitBooking(carrier, shipmentOrder, bookingRequest);
        if (apiResponse != null) return apiResponse;

        return simulateBooking(carrier, shipmentOrder, bookingRequest);
    }

    @Override
    public BookingResponse getBookingStatus(String carrierReference) {
        BookingResponse apiResponse = cmaCgmApiClient.getStatus(carrierReference);
        if (apiResponse != null) return apiResponse;

        BookingResponse resp = new BookingResponse();
        resp.setAccepted(true);
        resp.setCarrierReference(carrierReference);
        resp.setAdditionalData(Map.of("currentStatus", "IN_TRANSIT", "vesselPosition", "36.8N 10.2E"));
        return resp;
    }

    @Override
    public boolean cancelBooking(String carrierReference) {
        if (cmaCgmApiClient.isConfigured()) {
            return cmaCgmApiClient.cancelBooking(carrierReference);
        }
        return true;
    }

    private BookingResponse simulateBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest) {
        String ref = "CMA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String blNumber = "CMAU" + (System.currentTimeMillis() % 100000000L);

        String svcType = bookingRequest.getServiceType() != null
            ? bookingRequest.getServiceType().toUpperCase() : "FCL";
        boolean isFcl = svcType.contains("FCL") || svcType.contains("FULL");
        boolean isLcl = svcType.contains("LCL");
        boolean isReefer = svcType.contains("REEFER");

        BigDecimal baseCost = BigDecimal.valueOf(1900.00);
        if (isFcl) {
            baseCost = BigDecimal.valueOf(2600.00);
        } else if (isLcl) {
            baseCost = BigDecimal.valueOf(950.00);
        } else if (isReefer) {
            baseCost = BigDecimal.valueOf(3800.00);
        }

        if (shipmentOrder.getVolumeM3() != null) {
            baseCost = baseCost.add(BigDecimal.valueOf(shipmentOrder.getVolumeM3() * (isFcl ? 55 : 90)));
        }
        if (Boolean.TRUE.equals(shipmentOrder.isDangerous())) {
            baseCost = baseCost.add(BigDecimal.valueOf(400.00));
        }

        LocalDate departure = bookingRequest.getRequestedPickupDate() != null
            ? bookingRequest.getRequestedPickupDate().plusDays(3)
            : LocalDate.now().plusDays(5);
        int transitDays = isFcl ? 30 : isReefer ? 20 : isLcl ? 22 : 28;
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
            "vesselName", "CMA CGM JACQUES SAADE",
            "portOfLoading", "LE_HAVRE",
            "portOfDischarge", isFcl ? "SHANGHAI" : "MARSEILLE",
            "containerType", isReefer ? "REEFER_40HC" : isFcl ? "DRY_40HC" : "DRY_20GP",
            "source", "simulation"
        ));
        return resp;
    }
}
