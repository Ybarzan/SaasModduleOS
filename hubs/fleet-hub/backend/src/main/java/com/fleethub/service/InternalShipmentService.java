package com.fleethub.service;

import com.fleethub.dto.ShipmentOrderDTO;
import com.fleethub.dto.internal.InternalShipmentDto;
import com.fleethub.dto.internal.InternalShipmentItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lecture / création d'expéditions pour les autres hubs ({@code /api/internal/shipments}).
 * La conversion en DTO se fait dans la transaction : l'entité a des relations
 * paresseuses (camion, transporteur, tarif) qu'on ne peut plus lire une fois la
 * session fermée — renvoyer l'entité telle quelle échouait en 500 dès qu'un camion
 * était affecté à l'expédition.
 */
@Service
@RequiredArgsConstructor
public class InternalShipmentService {

    private static final int RECENT_LIMIT = 50;
    private static final int SEARCH_LIMIT = 20;

    private final ShipmentService shipmentService;

    @Transactional(readOnly = true)
    public List<InternalShipmentDto> recentOrSearch(Long companyId, String query) {
        var page = query != null && !query.isBlank()
                ? shipmentService.searchShipments(companyId, query.trim(), PageRequest.of(0, SEARCH_LIMIT))
                : shipmentService.listShipments(companyId, PageRequest.of(0, RECENT_LIMIT));
        return page.getContent().stream().map(s -> InternalShipmentDto.from(s, false)).toList();
    }

    @Transactional(readOnly = true)
    public InternalShipmentDto detail(UUID shipmentId, Long companyId) {
        return InternalShipmentDto.from(shipmentService.getShipmentWithTracking(shipmentId, companyId), true);
    }

    @Transactional(readOnly = true)
    public List<InternalShipmentItemDto> items(UUID shipmentId, Long companyId) {
        return shipmentService.getShipmentItems(shipmentId, companyId).stream()
                .map(InternalShipmentItemDto::from).toList();
    }

    @Transactional
    public InternalShipmentDto createDraft(ShipmentOrderDTO dto, Long companyId) {
        return InternalShipmentDto.from(shipmentService.createShipment(dto, null, companyId), false);
    }
}
