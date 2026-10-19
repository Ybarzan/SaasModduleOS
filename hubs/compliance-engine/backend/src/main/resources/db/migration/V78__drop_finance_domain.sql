-- Retrait du domaine Finance & Tresorerie (porte vers hubs/finance, com.finance).
-- Ordre de suppression verifie FK-safe : invoice_financing (-> client_invoices)
-- et client_invoices (-> payment_terms) d'abord, puis payment_terms elle-meme.
-- fintech_connections/shipment_financials/carbon_offsets n'ont aucune
-- dependance entrante ni sortante restante (leurs anciennes FK vers
-- shipment_orders ont deja ete retirees par V75).

DROP TABLE IF EXISTS invoice_financing;
DROP TABLE IF EXISTS client_invoices;
DROP TABLE IF EXISTS payment_terms;
DROP TABLE IF EXISTS fintech_connections;
DROP TABLE IF EXISTS shipment_financials;
DROP TABLE IF EXISTS carbon_offsets;
