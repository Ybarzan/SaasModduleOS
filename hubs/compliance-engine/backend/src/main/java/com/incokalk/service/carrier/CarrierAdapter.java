package com.incokalk.service.carrier;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;

public interface CarrierAdapter {

    String getCarrierCode();

    boolean supports(String carrierCode);

    BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest);

    BookingResponse getBookingStatus(String carrierReference);

    boolean cancelBooking(String carrierReference);
}
