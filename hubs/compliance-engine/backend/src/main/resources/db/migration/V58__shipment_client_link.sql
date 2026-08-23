-- Lien optionnel entre une expédition et un compte client du portail.
-- Une expédition non assignée (client_id NULL) n'est visible d'AUCUN client du portail
-- (défaut sûr : chaque client ne voit que SES expéditions, pas celles de toute l'entreprise).
ALTER TABLE shipment_orders ADD COLUMN client_id UUID;

ALTER TABLE shipment_orders
    ADD CONSTRAINT fk_shipment_client
    FOREIGN KEY (client_id) REFERENCES client_users(id) ON DELETE SET NULL;

CREATE INDEX idx_shipments_client ON shipment_orders(client_id);
