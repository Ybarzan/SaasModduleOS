package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.service.integration.fleethub.ExternalShipmentCreationRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ECommerceAdapter {

    boolean supports(ECommerceIntegration.Platform platform);

    List<Map<String, Object>> syncOrders(ECommerceIntegration integration);

    Map<String, Object> getOrder(ECommerceIntegration integration, String orderId);

    /** Identifiant de la commande CÔTÉ PLATEFORME marchande (ex. l'id numérique
     *  Shopify, converti en String) à partir d'un élément de la liste renvoyée
     *  par {@link #syncOrders} -- {@code null} si cette commande brute n'a pas
     *  d'identifiant exploitable (ne devrait jamais arriver en pratique, mais
     *  {@code ECommerceSyncService} doit pouvoir l'ignorer proprement plutôt
     *  que planter toute la synchronisation). */
    String extractExternalOrderId(Map<String, Object> rawOrder);

    /**
     * Reconstruit une expédition brouillon (ADR-0002,
     * docs/adr/0002-creation-expedition-externe.md) depuis une commande brute
     * renvoyée par {@link #syncOrders} -- restaure {@code mapOrderToShipment},
     * retirée avec le domaine Transport (lisait directement ShipmentOrderRepository).
     * {@code Optional.empty()} si cette commande précise ne peut pas être
     * mappée de façon fiable (adresse de livraison absente ou incomplète) OU
     * si la plateforme n'implémente pas encore ce mapping -- jamais de champ
     * deviné pour forcer un résultat. Ne construit JAMAIS la demande sans
     * shipperName/consigneeName (obligatoires côté fleet-hub).
     */
    Optional<ExternalShipmentCreationRequest> mapOrderToShipment(ECommerceIntegration integration, Map<String, Object> rawOrder);

    /**
     * Marque une commande comme expédiée chez le marchand, avec tracking --
     * Phase 1 J4 (docs/03-plan-migration.md). {@code externalOrderId} est
     * l'identifiant de la commande CÔTÉ PLATEFORME marchande (ex. l'id
     * numérique Shopify), pas le {@code orderNumber} interne fleet-hub --
     * {@code EcommerceShipmentLink} (V81) relie désormais les deux pour les
     * commandes passées par {@link #mapOrderToShipment} ou liées manuellement
     * (voir {@code EcommerceShipmentLinkService}), mais un appelant de cette
     * méthode continue de fournir {@code externalOrderId} directement. Jamais
     * de résultat fictif : une plateforme qui n'implémente pas encore cette
     * action renvoie un échec explicite, pas un faux succès.
     */
    PushResult markOrderShipped(ECommerceIntegration integration, String externalOrderId,
                                 String trackingNumber, String carrierName, boolean notifyCustomer);

    /** Enregistre l'ETA courante en métadonnée sur la commande d'origine --
     *  pas un champ standard universel aux plateformes e-commerce, voir le
     *  javadoc de l'implémentation pour le mécanisme utilisé. */
    PushResult pushEta(ECommerceIntegration integration, String externalOrderId, LocalDateTime estimatedDeliveryDate);

    record PushResult(boolean success, String message) {
        public static PushResult ok(String message) {
            return new PushResult(true, message);
        }

        public static PushResult fail(String message) {
            return new PushResult(false, message);
        }
    }
}
