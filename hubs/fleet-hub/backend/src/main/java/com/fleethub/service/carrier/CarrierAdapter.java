package com.fleethub.service.carrier;

import com.fleethub.dto.BookingResponse;
import com.fleethub.model.Carrier;
import com.fleethub.model.CarrierBookingRequest;
import com.fleethub.model.ShipmentOrder;

public interface CarrierAdapter {

    String getCarrierCode();

    boolean supports(String carrierCode);

    BookingResponse submitBooking(Carrier carrier, ShipmentOrder shipmentOrder, CarrierBookingRequest bookingRequest);

    BookingResponse getBookingStatus(String carrierReference);

    boolean cancelBooking(String carrierReference);
}
