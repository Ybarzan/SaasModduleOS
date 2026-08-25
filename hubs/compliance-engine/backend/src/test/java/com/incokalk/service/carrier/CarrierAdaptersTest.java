package com.incokalk.service.carrier;

import com.incokalk.dto.shipment.BookingResponse;
import com.incokalk.model.Carrier;
import com.incokalk.model.CarrierBookingRequest;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.carrier.api.DhlBookingApiClient;
import com.incokalk.service.carrier.api.MscBookingApiClient;
import com.incokalk.service.carrier.api.DbSchenkerBookingApiClient;
import com.incokalk.service.carrier.api.GeodisBookingApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CarrierAdaptersTest {

    private Carrier carrier;
    private ShipmentOrder shipment;
    private CarrierBookingRequest bookingRequest;
    private DhlBookingApiClient dhlApiClient;
    private MscBookingApiClient mscApiClient;
    private DbSchenkerBookingApiClient dbSchenkerApiClient;
    private GeodisBookingApiClient geodisApiClient;

    @BeforeEach
    void setUp() {
        dhlApiClient = mock(DhlBookingApiClient.class);
        mscApiClient = mock(MscBookingApiClient.class);
        dbSchenkerApiClient = mock(DbSchenkerBookingApiClient.class);
        geodisApiClient = mock(GeodisBookingApiClient.class);

        carrier = new Carrier();
        carrier.setCode("DHL");
        carrier.setTransportModes("ROAD,AIR");

        shipment = new ShipmentOrder();
        shipment.setWeightKg(80.0);
        shipment.setVolumeM3(3.0);
        shipment.setDangerous(false);

        bookingRequest = new CarrierBookingRequest();
        bookingRequest.setRequestedPickupDate(LocalDate.now().plusDays(3));
        bookingRequest.setServiceType("EXPRESS");
    }

    @Test
    void dhlAdapter_supported() {
        DHLAdapter adapter = new DHLAdapter(dhlApiClient);
        assertThat(adapter.supports("DHL")).isTrue();
        assertThat(adapter.supports("DHL_EXPRESS")).isTrue();
        assertThat(adapter.supports("MSC")).isFalse();
        assertThat(adapter.getCarrierCode()).isEqualTo("DHL");
    }

    @Test
    void dhlAdapter_submitBooking() {
        when(dhlApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        DHLAdapter adapter = new DHLAdapter(dhlApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);

        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).startsWith("DHL-");
        assertThat(resp.getTrackingNumber()).startsWith("DHL");
        assertThat(resp.getQuotedCost()).isPositive();
        assertThat(resp.getCurrency()).isEqualTo("EUR");
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(3);
        assertThat(resp.getEstimatedPickupDate()).isNotNull();
        assertThat(resp.getEstimatedDeliveryDate()).isNotNull();
    }

    @Test
    void dhlAdapter_getStatus() {
        when(dhlApiClient.getStatus("DHL-ABC123")).thenReturn(null);
        DHLAdapter adapter = new DHLAdapter(dhlApiClient);
        BookingResponse resp = adapter.getBookingStatus("DHL-ABC123");
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("DHL-ABC123");
    }

    @Test
    void dhlAdapter_cancel() {
        DHLAdapter adapter = new DHLAdapter(dhlApiClient);
        assertThat(adapter.cancelBooking("DHL-ABC123")).isTrue();
    }

    @Test
    void mscAdapter_supported() {
        MSCAdapter adapter = new MSCAdapter(mscApiClient);
        assertThat(adapter.supports("MSC")).isTrue();
        assertThat(adapter.supports("CMA_CGM")).isTrue();
        assertThat(adapter.supports("DHL")).isFalse();
        assertThat(adapter.getCarrierCode()).isEqualTo("MSC");
    }

    @Test
    void mscAdapter_submitBooking() {
        when(mscApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        MSCAdapter adapter = new MSCAdapter(mscApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);

        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).startsWith("MSC-");
        assertThat(resp.getTrackingNumber()).startsWith("MSCU");
        assertThat(resp.getQuotedCost()).isPositive();
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(25);
    }

    @Test
    void mscAdapter_dangerousGoods_addsSurcharge() {
        shipment.setDangerous(true);
        when(mscApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        MSCAdapter adapter = new MSCAdapter(mscApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);
        assertThat(resp.getQuotedCost()).isGreaterThan(BigDecimal.valueOf(1800));
    }

    @Test
    void mscAdapter_volumeBasedPricing() {
        shipment.setVolumeM3(10.0);
        when(mscApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        MSCAdapter adapter = new MSCAdapter(mscApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);
        assertThat(resp.getQuotedCost()).isGreaterThan(BigDecimal.valueOf(2600));
    }

    @Test
    void geodisAdapter_supported() {
        GeodisAdapter adapter = new GeodisAdapter(geodisApiClient);
        assertThat(adapter.supports("GEODIS")).isTrue();
        assertThat(adapter.supports("BOLLORE")).isTrue();
        assertThat(adapter.supports("DHL")).isFalse();
        assertThat(adapter.getCarrierCode()).isEqualTo("GEODIS");
    }

    @Test
    void geodisAdapter_submitBooking() {
        when(geodisApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        GeodisAdapter adapter = new GeodisAdapter(geodisApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);

        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).startsWith("GEO-");
        assertThat(resp.getTrackingNumber()).startsWith("GE");
        assertThat(resp.getQuotedCost()).isPositive();
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(2);
    }

    @Test
    void geodisAdapter_heavyFreight() {
        shipment.setWeightKg(2000.0);
        when(geodisApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        GeodisAdapter adapter = new GeodisAdapter(geodisApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);
        assertThat(resp.getQuotedCost()).isGreaterThan(BigDecimal.valueOf(620));
    }

    @Test
    void geodisAdapter_manyPackages() {
        shipment.setPackagesCount(20);
        when(geodisApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        GeodisAdapter adapter = new GeodisAdapter(geodisApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);
        assertThat(resp.getQuotedCost()).isGreaterThan(BigDecimal.valueOf(620));
    }

    @Test
    void geodisAdapter_cancel() {
        GeodisAdapter adapter = new GeodisAdapter(geodisApiClient);
        assertThat(adapter.cancelBooking("GEO-ABC123")).isTrue();
    }

    @Test
    void geodisAdapter_getStatus() {
        when(geodisApiClient.getStatus("GEO-ABC123")).thenReturn(null);
        GeodisAdapter adapter = new GeodisAdapter(geodisApiClient);
        BookingResponse resp = adapter.getBookingStatus("GEO-ABC123");
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("GEO-ABC123");
    }

    @Test
    void dbSchenkerAdapter_supported() {
        DBSchenkerAdapter adapter = new DBSchenkerAdapter(dbSchenkerApiClient);
        assertThat(adapter.supports("DB_SCHENKER")).isTrue();
        assertThat(adapter.supports("SCHENKER")).isTrue();
        assertThat(adapter.supports("DB_SCHENKER_LOGISTICS")).isTrue();
        assertThat(adapter.supports("DHL")).isFalse();
        assertThat(adapter.getCarrierCode()).isEqualTo("DB_SCHENKER");
    }

    @Test
    void dbSchenkerAdapter_submitBooking() {
        when(dbSchenkerApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        DBSchenkerAdapter adapter = new DBSchenkerAdapter(dbSchenkerApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);

        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).startsWith("DBS-");
        assertThat(resp.getTrackingNumber()).startsWith("DB");
        assertThat(resp.getQuotedCost()).isPositive();
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(2);
    }

    @Test
    void dbSchenkerAdapter_standardService() {
        bookingRequest.setServiceType("STANDARD");
        when(dbSchenkerApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        DBSchenkerAdapter adapter = new DBSchenkerAdapter(dbSchenkerApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(6);
    }

    @Test
    void dbSchenkerAdapter_railMode() {
        carrier.setTransportModes("RAIL");
        when(dbSchenkerApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        DBSchenkerAdapter adapter = new DBSchenkerAdapter(dbSchenkerApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);

        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getQuotedCost()).isPositive();
        assertThat(resp.getEstimatedTransitDays()).isEqualTo(14);
    }

    @Test
    void dbSchenkerAdapter_dangerousGoods_addsSurcharge() {
        shipment.setDangerous(true);
        when(dbSchenkerApiClient.submitBooking(carrier, shipment, bookingRequest)).thenReturn(null);
        DBSchenkerAdapter adapter = new DBSchenkerAdapter(dbSchenkerApiClient);
        BookingResponse resp = adapter.submitBooking(carrier, shipment, bookingRequest);
        assertThat(resp.getQuotedCost()).isGreaterThan(BigDecimal.valueOf(540));
    }

    @Test
    void dbSchenkerAdapter_cancel() {
        DBSchenkerAdapter adapter = new DBSchenkerAdapter(dbSchenkerApiClient);
        assertThat(adapter.cancelBooking("DBS-ABC123")).isTrue();
    }

    @Test
    void dbSchenkerAdapter_getStatus() {
        when(dbSchenkerApiClient.getStatus("DBS-ABC123")).thenReturn(null);
        DBSchenkerAdapter adapter = new DBSchenkerAdapter(dbSchenkerApiClient);
        BookingResponse resp = adapter.getBookingStatus("DBS-ABC123");
        assertThat(resp.isAccepted()).isTrue();
        assertThat(resp.getCarrierReference()).isEqualTo("DBS-ABC123");
    }
}
