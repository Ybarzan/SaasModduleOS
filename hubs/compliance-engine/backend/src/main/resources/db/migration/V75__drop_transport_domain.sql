-- Retrait du domaine Transport (porté vers hubs/fleet-hub, com.fleethub).
-- Ordre de suppression verifie FK-safe : tables dependantes de shipment_orders/
-- carriers d'abord, puis shipment_orders/carriers elles-memes.

DROP TABLE IF EXISTS carrier_invoice_lines;
DROP TABLE IF EXISTS carrier_invoices;
DROP TABLE IF EXISTS carrier_booking_requests;
DROP TABLE IF EXISTS groupage_members;
DROP TABLE IF EXISTS groupages;
DROP TABLE IF EXISTS eta_predictions;
DROP TABLE IF EXISTS eta_model_coefficients;
DROP TABLE IF EXISTS shipment_items;
DROP TABLE IF EXISTS shared_links;
DROP TABLE IF EXISTS shipment_trackings;

-- Tables qui restent mais dont la relation JPA vers shipment_orders/carriers
-- devient une simple colonne UUID (voir modeles correspondants) : on retire
-- leur contrainte de cle etrangere avant de pouvoir dropper shipment_orders.
ALTER TABLE shipment_financials DROP CONSTRAINT IF EXISTS shipment_financials_shipment_id_fkey;
ALTER TABLE carbon_offsets DROP CONSTRAINT IF EXISTS carbon_offsets_shipment_id_fkey;
ALTER TABLE landed_costs DROP CONSTRAINT IF EXISTS landed_costs_shipment_id_fkey;
ALTER TABLE customs_declarations DROP CONSTRAINT IF EXISTS customs_declarations_shipment_id_fkey;
ALTER TABLE deb_declarations DROP CONSTRAINT IF EXISTS deb_declarations_shipment_id_fkey;
ALTER TABLE ics2_declarations DROP CONSTRAINT IF EXISTS ics2_declarations_shipment_id_fkey;
ALTER TABLE export_declarations DROP CONSTRAINT IF EXISTS export_declarations_shipment_id_fkey;

-- shipping_rates est reference par shipment_orders.shipping_rate_id (V4) : shipment_orders
-- doit donc etre droppee avant shipping_rates, pas apres -- ordre corrige par rapport a
-- l'enonce initial (qui plaçait shipping_rates avant shipment_orders alors que
-- shipment_orders.shipping_rate_id la reference encore a ce stade, ce qui aurait fait
-- echouer la migration). tracking_events (FK vers shipment_orders) reste avant, comme prevu.
DROP TABLE IF EXISTS tracking_events;
DROP TABLE IF EXISTS shipment_orders;
DROP TABLE IF EXISTS shipping_rates;
DROP TABLE IF EXISTS carriers;
DROP TABLE IF EXISTS fleethub_configs;

-- customs_invoices.shipment_id est deja une colonne simple sans FK -- aucune
-- action necessaire.
