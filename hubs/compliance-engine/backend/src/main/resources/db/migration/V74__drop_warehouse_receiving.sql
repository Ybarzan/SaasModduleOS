-- V74__drop_warehouse_receiving.sql
-- Le domaine entrepôt/réception (warehouses, catalogue articles, réception,
-- stock) a été extrait vers le hub Logistix autonome (hubs/logistix/backend/).
-- Ces tables ne sont plus utilisées par compliance-engine : on les supprime.
-- Ordre FK-safe (enfants avant parents). shipment_items n'est PAS concerné :
-- sa FK shipment_id -> shipment_orders en fait un sujet du périmètre
-- Transport/ShipmentOrder, traité séparément.

DROP TABLE IF EXISTS discrepancies;
DROP TABLE IF EXISTS receiving_scans;
DROP TABLE IF EXISTS receiving_order_lines;
DROP TABLE IF EXISTS receiving_orders;
DROP TABLE IF EXISTS item_barcodes;
DROP TABLE IF EXISTS stock_balances;
DROP TABLE IF EXISTS stock_movements;
DROP TABLE IF EXISTS inventory_items;
DROP TABLE IF EXISTS warehouses;
