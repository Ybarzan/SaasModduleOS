package com.fleethub.integration;

import com.fleethub.integration.dto.GpsPositionDto;
import com.fleethub.model.Truck;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.FuelRecordRepository;
import com.fleethub.repository.TachographDayRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.service.TachographService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Le polling est configuré pour toute l'instance : sans société destinataire
 * explicite (integration.*.company-id), il ne doit rien ingérer plutôt que de
 * rattacher les données à une société au hasard.
 */
class IntegrationSyncPollingTest {

    IntegrationProperties props;
    GpsSource gpsSource;
    TruckRepository truckRepository;
    IntegrationSyncService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        props = new IntegrationProperties();
        props.getGps().setEnabled(true);
        gpsSource = mock(GpsSource.class);
        ObjectProvider<GpsSource> gpsProvider = mock(ObjectProvider.class);
        when(gpsProvider.getIfAvailable()).thenReturn(gpsSource);
        truckRepository = mock(TruckRepository.class);
        service = new IntegrationSyncService(props, mock(ObjectProvider.class), gpsProvider, mock(ObjectProvider.class),
                mock(DriverRepository.class), truckRepository, mock(TachographDayRepository.class),
                mock(FuelRecordRepository.class), mock(TachographService.class));
    }

    @Test
    void enabledSourceWithoutCompany_ingestsNothing() {
        service.syncGpsPositions();

        verifyNoInteractions(gpsSource);
        verifyNoInteractions(truckRepository);
    }

    @Test
    void enabledSourceWithCompany_onlyLooksUpThatCompanysVehicles() {
        props.getGps().setCompanyId(7L);
        when(gpsSource.fetchPositions()).thenReturn(List.of(
                new GpsPositionDto("AB-123-CD", 45.0, 4.0, 50.0, LocalDateTime.now())));
        Truck truck = new Truck();
        when(truckRepository.findByRegistrationAndCompanyId("AB-123-CD", 7L)).thenReturn(Optional.of(truck));

        service.syncGpsPositions();

        verify(truckRepository).findByRegistrationAndCompanyId("AB-123-CD", 7L);
        verify(truckRepository).save(truck);
        verify(truckRepository, never()).findAll();
        verify(truckRepository, never()).findByRegistrationAndCompanyId(any(), org.mockito.ArgumentMatchers.eq(8L));
    }
}
