package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ECommerceAdapter {

    boolean supports(ECommerceIntegration.Platform platform);

    List<Map<String, Object>> syncOrders(ECommerceIntegration integration);

    Map<String, Object> getOrder(ECommerceIntegration integration, String orderId);

    // mapOrderToShipment(...) -> ShipmentOrder a ete retiree avec le domaine
    // Transport. ECommerceSyncService.syncSingleIntegration() traite desormais la
    // synchronisation commande -> expedition comme un no-op en attendant une
    // reconstruction via fleet-hub (voir ECommerceSyncService).

    /**
     * Marque une commande comme expédiée chez le marchand, avec tracking --
     * Phase 1 J4 (docs/03-plan-migration.md). {@code externalOrderId} est
     * l'identifiant de la commande CÔTÉ PLATEFORME marchande (ex. l'id
     * numérique Shopify), pas le {@code orderNumber} interne fleet-hub : ce
     * service ne maintient aujourd'hui aucune table reliant les deux (la
     * boucle qui l'aurait établie, {@code adapter.mapOrderToShipment}, est un
     * no-op depuis le retrait du domaine Transport -- voir
     * {@code ECommerceSyncService}) -- l'appelant doit connaître/résoudre
     * {@code externalOrderId} lui-même. Jamais de résultat fictif : une
     * plateforme qui n'implémente pas encore cette action renvoie un échec
     * explicite, pas un faux succès.
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
