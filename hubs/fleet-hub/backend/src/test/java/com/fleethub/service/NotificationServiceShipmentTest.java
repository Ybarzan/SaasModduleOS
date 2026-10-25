package com.fleethub.service;

import com.fleethub.model.Company;
import com.fleethub.model.Notification;
import com.fleethub.model.NotificationRule;
import com.fleethub.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests ciblés sur NotificationService.notifyShipmentStatusChanged -- la
 * notification poussée immédiatement au changement de statut d'une expédition
 * (voir ShipmentEventPublisher), par opposition aux autres types d'alerte de
 * ce service, générés par balayage périodique.
 */
@DisplayName("NotificationService — notifyShipmentStatusChanged")
class NotificationServiceShipmentTest {

    NotificationRepository notificationRepository;
    NotificationRuleRepository ruleRepository;
    CompanyRepository companyRepository;
    NotificationService service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        ruleRepository = mock(NotificationRuleRepository.class);
        companyRepository = mock(CompanyRepository.class);
        service = new NotificationService(
                notificationRepository,
                ruleRepository,
                mock(MaintenanceRepository.class),
                mock(TachographDayRepository.class),
                mock(DrivingEventRepository.class),
                mock(DriverRepository.class),
                mock(TruckRepository.class),
                companyRepository);
    }

    @Test
    @DisplayName("règle activée par défaut (aucune règle existante) → notification créée")
    void notifyShipmentStatusChanged_noRulesYet_createsDefaultRuleAndNotifies() {
        NotificationRule defaultRule = new NotificationRule();
        defaultRule.setType(NotificationRule.AlertType.EXPEDITION_STATUT_CHANGE);
        defaultRule.setEnabled(true);
        // 1er appel (dans ensureDefaultRules) : aucune règle -> création des règles
        // par défaut. 2e appel (relecture dans notifyShipmentStatusChanged) : la
        // règle par défaut, activée, est maintenant "en base".
        when(ruleRepository.findByCompanyId(42L)).thenReturn(List.of(), List.of(defaultRule));
        when(ruleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(companyRepository.getReferenceById(42L)).thenReturn(new Company());

        service.notifyShipmentStatusChanged(42L, "ORD-001", "EN_TRANSIT", "LIVRE");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationRule.AlertType.EXPEDITION_STATUT_CHANGE);
        assertThat(saved.getTitle()).contains("ORD-001");
        assertThat(saved.getMessage()).contains("EN_TRANSIT").contains("LIVRE");
        assertThat(saved.getEntityType()).isEqualTo("SHIPMENT");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    @DisplayName("règle existante et activée → notification créée sans recréer de règle par défaut")
    void notifyShipmentStatusChanged_ruleEnabled_createsNotification() {
        NotificationRule enabled = new NotificationRule();
        enabled.setType(NotificationRule.AlertType.EXPEDITION_STATUT_CHANGE);
        enabled.setEnabled(true);
        when(ruleRepository.findByCompanyId(42L)).thenReturn(List.of(enabled));
        when(companyRepository.getReferenceById(42L)).thenReturn(new Company());

        service.notifyShipmentStatusChanged(42L, "ORD-003", "CREE", "EN_TRANSIT");

        verify(notificationRepository).save(any());
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("règle désactivée explicitement → aucune notification créée")
    void notifyShipmentStatusChanged_ruleDisabled_doesNotNotify() {
        NotificationRule disabled = new NotificationRule();
        disabled.setType(NotificationRule.AlertType.EXPEDITION_STATUT_CHANGE);
        disabled.setEnabled(false);
        when(ruleRepository.findByCompanyId(42L)).thenReturn(List.of(disabled));

        service.notifyShipmentStatusChanged(42L, "ORD-001", "EN_TRANSIT", "LIVRE");

        verifyNoInteractions(notificationRepository);
    }
}
