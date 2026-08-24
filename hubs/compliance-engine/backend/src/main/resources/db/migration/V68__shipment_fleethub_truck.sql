-- Lien entre une expédition et un camion de la flotte propre du client (fleet-hub),
-- distinct d'un transporteur tiers (carrier_id). Voir docs/07-integration-fleet-hub.md
-- et V67 (fleethub_configs). Une expédition livrée par la flotte propre du client
-- n'a pas forcément de transporteur (carrier) au sens IncoKalk du terme.

ALTER TABLE shipment_orders
    ADD COLUMN fleethub_truck_registration VARCHAR(50);
