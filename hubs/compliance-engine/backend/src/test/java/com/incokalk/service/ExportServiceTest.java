package com.incokalk.service;

import com.incokalk.model.Carrier;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ExportService — Tests unitaires")
class ExportServiceTest {

    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock CarrierRepository carrierRepo;
    @Mock HttpServletResponse response;

    @InjectMocks ExportService service;

    private UUID companyId;
    private ByteArrayOutputStream responseStream;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();

        responseStream = new ByteArrayOutputStream();
        PrintWriter printWriter = new PrintWriter(responseStream, true, StandardCharsets.UTF_8);
        ServletOutputStream servletOutputStream = new ServletOutputStream() {
            @Override
            public void write(int b) { responseStream.write(b); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(jakarta.servlet.WriteListener listener) {}
        };

        when(response.getWriter()).thenReturn(printWriter);
        when(response.getOutputStream()).thenReturn(servletOutputStream);
    }

    // ── exportShipmentsCsv ───────────────────────────────────────────────

    @Test
    @DisplayName("Export shipments CSV avec données → header + data rows")
    void exportShipmentsCsv_success() throws IOException {
        Carrier carrier = Carrier.builder().name("Maersk").code("MSK").build();

        ShipmentOrder s = ShipmentOrder.builder()
                .orderNumber("ORD-001")
                .status(ShipmentOrder.Status.BOOKED)
                .carrier(carrier)
                .shipperName("Expéditeur A")
                .shipperCity("Paris")
                .shipperCountry("FRA")
                .consigneeName("Destinataire B")
                .consigneeCity("New York")
                .consigneeCountry("USA")
                .goodsDescription("Electronics")
                .weightKg(500.0)
                .volumeM3(2.5)
                .goodsValue(10000.0)
                .currency("EUR")
                .incotermCode("FOB")
                .quotedCost(3500.0)
                .finalCost(3200.0)
                .costCurrency("EUR")
                .requestedPickupDate(LocalDateTime.of(2026, 3, 15, 10, 0))
                .estimatedDeliveryDate(LocalDateTime.of(2026, 4, 1, 14, 0))
                .actualDeliveryDate(LocalDateTime.of(2026, 4, 3, 9, 30))
                .createdAt(LocalDateTime.of(2026, 3, 10, 8, 0))
                .build();

        when(shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(s));

        service.exportShipmentsCsv(companyId, response);

        verify(response).setContentType("text/csv; charset=UTF-8");
        verify(response).setHeader("Content-Disposition", "attachment; filename=shipments_export.csv");

        String csv = responseStream.toString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines.length).isEqualTo(2);
        assertThat(lines[0]).contains("Numéro");
        assertThat(lines[0]).contains("Statut");
        assertThat(lines[0]).contains("Transporteur");
        assertThat(lines[0]).contains("Incoterm");
        assertThat(lines[1]).contains("ORD-001");
        assertThat(lines[1]).contains("BOOKED");
        assertThat(lines[1]).contains("Maersk");
        assertThat(lines[1]).contains("FOB");
    }

    @Test
    @DisplayName("Export shipments CSV vide → header seul sans data rows")
    void exportShipmentsCsv_emptyList_headersOnly() throws IOException {
        when(shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(Collections.emptyList());

        service.exportShipmentsCsv(companyId, response);

        String csv = responseStream.toString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines.length).isEqualTo(1);
        assertThat(lines[0]).contains("Numéro");
        assertThat(lines[0]).contains("Créé le");
    }

    // ── exportCarriersCsv ────────────────────────────────────────────────

    @Test
    @DisplayName("Export carriers CSV avec données → header + data rows")
    void exportCarriersCsv_success() throws IOException {
        Carrier carrier = Carrier.builder()
                .name("DHL Express")
                .code("DHL")
                .transportModes("AIR")
                .contactName("Jean Dupont")
                .contactEmail("jd@dhl.com")
                .contactPhone("+33123456")
                .country("FRA")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 1, 20, 9, 0))
                .build();

        when(carrierRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(carrier));

        service.exportCarriersCsv(companyId, response);

        verify(response).setContentType("text/csv; charset=UTF-8");
        verify(response).setHeader("Content-Disposition", "attachment; filename=carriers_export.csv");

        String csv = responseStream.toString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines.length).isEqualTo(2);
        assertThat(lines[0]).contains("Nom");
        assertThat(lines[0]).contains("Code");
        assertThat(lines[0]).contains("Modes transport");
        assertThat(lines[0]).contains("Actif");
        assertThat(lines[1]).contains("DHL Express");
        assertThat(lines[1]).contains("DHL");
        assertThat(lines[1]).contains("AIR");
        assertThat(lines[1]).contains("Oui");
    }

    @Test
    @DisplayName("Export carriers CSV vide → header seul sans data rows")
    void exportCarriersCsv_emptyList_headersOnly() throws IOException {
        when(carrierRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(Collections.emptyList());

        service.exportCarriersCsv(companyId, response);

        String csv = responseStream.toString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines.length).isEqualTo(1);
        assertThat(lines[0]).contains("Nom");
        assertThat(lines[0]).contains("Créé le");
    }
}
