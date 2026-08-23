package com.incokalk.service;

import com.incokalk.dto.shipment.ShipmentOrderDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.ShippingRate;
import com.incokalk.model.User;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ClientUserRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentItemRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.ShippingRateRepository;
import com.incokalk.repository.TrackingEventRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShipmentServiceTest {

    @Mock private ShipmentOrderRepository shipmentRepo;
    @Mock private TrackingEventRepository trackingEventRepo;
    @Mock private CarrierRepository carrierRepo;
    @Mock private ShippingRateRepository shippingRateRepo;
    @Mock private CompanyRepository companyRepo;
    @Mock private UserRepository userRepo;
    @Mock private EventPublisher eventPublisher;
    @Mock private ShipmentItemRepository shipmentItemRepo;
    @Mock private InventoryService inventoryService;
    @Mock private ClientUserRepository clientUserRepo;

    private ShipmentService service;

    private final UUID companyId = UUID.randomUUID();
    private final UUID otherCompanyId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Company company;
    private Company otherCompany;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ShipmentService(shipmentRepo, trackingEventRepo, carrierRepo,
                shippingRateRepo, companyRepo, userRepo, eventPublisher,
                shipmentItemRepo, inventoryService, clientUserRepo);
        TenantContext.set(companyId);

        company = new Company();
        company.setId(companyId);
        otherCompany = new Company();
        otherCompany.setId(otherCompanyId);

        User user = new User();
        user.setId(userId);
        user.setCompany(company);

        lenient().when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        lenient().when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(shipmentRepo.save(any())).thenAnswer(inv -> {
            ShipmentOrder s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createShipment refuse un transporteur appartenant à une autre entreprise (fuite inter-tenant)")
    void createShipment_carrierFromOtherTenant_rejected() {
        UUID foreignCarrierId = UUID.randomUUID();
        ShipmentOrderDTO dto = ShipmentOrderDTO.builder()
                .carrierId(foreignCarrierId)
                .build();

        // Scoped lookup for the current tenant finds nothing (carrier belongs to another company)
        when(carrierRepo.findByIdAndCompanyId(eq(foreignCarrierId), eq(companyId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createShipment(dto, userId, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transporteur");

        verify(shipmentRepo, never()).save(any());
    }

    @Test
    @DisplayName("createShipment refuse un tarif appartenant à une autre entreprise (fuite inter-tenant)")
    void createShipment_shippingRateFromOtherTenant_rejected() {
        UUID foreignRateId = UUID.randomUUID();
        ShipmentOrderDTO dto = ShipmentOrderDTO.builder()
                .shippingRateId(foreignRateId)
                .build();

        ShippingRate foreignRate = new ShippingRate();
        foreignRate.setId(foreignRateId);
        foreignRate.setCompany(otherCompany);
        // Repository returns the raw entity by id; the service must filter it out by company
        when(shippingRateRepo.findById(foreignRateId)).thenReturn(Optional.of(foreignRate));

        assertThatThrownBy(() -> service.createShipment(dto, userId, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tarif");

        verify(shipmentRepo, never()).save(any());
    }

    @Test
    @DisplayName("createShipment accepte un transporteur de la même entreprise")
    void createShipment_ownCarrier_success() {
        UUID carrierId = UUID.randomUUID();
        Carrier carrier = new Carrier();
        carrier.setId(carrierId);
        carrier.setCompany(company);

        ShipmentOrderDTO dto = ShipmentOrderDTO.builder()
                .carrierId(carrierId)
                .shipperName("ACME")
                .consigneeName("Globex")
                .build();

        when(carrierRepo.findByIdAndCompanyId(eq(carrierId), eq(companyId)))
                .thenReturn(Optional.of(carrier));

        ShipmentOrder result = service.createShipment(dto, userId, companyId);

        assertThat(result).isNotNull();
        assertThat(result.getCompany()).isEqualTo(company);
        assertThat(result.getCarrier()).isEqualTo(carrier);
        verify(shipmentRepo).save(any());
        verify(eventPublisher).shipmentCreated(any(), any(), eq(companyId));
    }

    @Test
    @DisplayName("createShipment refuse un clientId appartenant à une autre entreprise (fuite inter-tenant)")
    void createShipment_clientFromOtherTenant_rejected() {
        UUID foreignClientId = UUID.randomUUID();
        ShipmentOrderDTO dto = ShipmentOrderDTO.builder().clientId(foreignClientId).build();

        when(clientUserRepo.findByIdAndCompanyId(foreignClientId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createShipment(dto, userId, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Client");

        verify(shipmentRepo, never()).save(any());
    }

    @Test
    @DisplayName("createShipment rattache l'expédition au client fourni s'il appartient à l'entreprise")
    void createShipment_withOwnClient_setsClientId() {
        UUID clientId = UUID.randomUUID();
        com.incokalk.model.ClientUser client = com.incokalk.model.ClientUser.builder()
                .id(clientId).company(company).build();
        ShipmentOrderDTO dto = ShipmentOrderDTO.builder().clientId(clientId).build();

        when(clientUserRepo.findByIdAndCompanyId(clientId, companyId)).thenReturn(Optional.of(client));

        ShipmentOrder result = service.createShipment(dto, userId, companyId);

        assertThat(result.getClientId()).isEqualTo(clientId);
    }

    @Test
    @DisplayName("assignClient rattache une expédition existante à un client de la même entreprise")
    void assignClient_success() {
        UUID shipmentId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        ShipmentOrder shipment = ShipmentOrder.builder().id(shipmentId).company(company).build();
        com.incokalk.model.ClientUser client = com.incokalk.model.ClientUser.builder()
                .id(clientId).company(company).build();

        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(clientUserRepo.findByIdAndCompanyId(clientId, companyId)).thenReturn(Optional.of(client));

        ShipmentOrder result = service.assignClient(shipmentId, clientId, companyId);

        assertThat(result.getClientId()).isEqualTo(clientId);
    }

    @Test
    @DisplayName("assignClient refuse un client d'une autre entreprise (fuite inter-tenant)")
    void assignClient_foreignClient_rejected() {
        UUID shipmentId = UUID.randomUUID();
        UUID foreignClientId = UUID.randomUUID();
        ShipmentOrder shipment = ShipmentOrder.builder().id(shipmentId).company(company).build();

        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(clientUserRepo.findByIdAndCompanyId(foreignClientId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignClient(shipmentId, foreignClientId, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Client");

        verify(shipmentRepo, never()).save(any());
    }

    @Test
    @DisplayName("assignClient(null) détache le client d'une expédition")
    void assignClient_nullClientId_detaches() {
        UUID shipmentId = UUID.randomUUID();
        ShipmentOrder shipment = ShipmentOrder.builder().id(shipmentId).company(company)
                .clientId(UUID.randomUUID()).build();

        when(shipmentRepo.findById(shipmentId)).thenReturn(Optional.of(shipment));

        ShipmentOrder result = service.assignClient(shipmentId, null, companyId);

        assertThat(result.getClientId()).isNull();
    }
}
