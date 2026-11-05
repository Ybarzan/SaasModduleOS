-- Montants en décimal exact au lieu de virgule flottante (float(53) / DOUBLE PRECISION) :
-- 0,1 + 0,2 n'y vaut pas 0,3, et les écarts s'accumulent dans les totaux de coûts,
-- les droits et taxes de douane et les factures. Même précision que les montants déjà
-- en DECIMAL du domaine Transport (carrier_booking_requests.quoted_cost, factures).
-- Les valeurs existantes sont arrondies au centime (tarifs unitaires : 4 décimales).

ALTER TABLE cost_record        ALTER COLUMN amount         TYPE DECIMAL(15,2) USING round(amount::numeric, 2);
ALTER TABLE fuel_record        ALTER COLUMN amount         TYPE DECIMAL(15,2) USING round(amount::numeric, 2);
ALTER TABLE maintenance_record ALTER COLUMN cost           TYPE DECIMAL(15,2) USING round(cost::numeric, 2);
ALTER TABLE truck              ALTER COLUMN purchase_price TYPE DECIMAL(15,2) USING round(purchase_price::numeric, 2);

ALTER TABLE shipment_orders ALTER COLUMN goods_value TYPE DECIMAL(15,2) USING round(goods_value::numeric, 2);
ALTER TABLE shipment_orders ALTER COLUMN duty_amount TYPE DECIMAL(15,2) USING round(duty_amount::numeric, 2);
ALTER TABLE shipment_orders ALTER COLUMN vat_amount  TYPE DECIMAL(15,2) USING round(vat_amount::numeric, 2);
ALTER TABLE shipment_orders ALTER COLUMN landed_cost TYPE DECIMAL(15,2) USING round(landed_cost::numeric, 2);
ALTER TABLE shipment_orders ALTER COLUMN quoted_cost TYPE DECIMAL(15,2) USING round(quoted_cost::numeric, 2);
ALTER TABLE shipment_orders ALTER COLUMN final_cost  TYPE DECIMAL(15,2) USING round(final_cost::numeric, 2);

ALTER TABLE shipping_rates ALTER COLUMN base_rate    TYPE DECIMAL(15,4) USING round(base_rate::numeric, 4);
ALTER TABLE shipping_rates ALTER COLUMN rate_per_kg  TYPE DECIMAL(15,4) USING round(rate_per_kg::numeric, 4);
ALTER TABLE shipping_rates ALTER COLUMN rate_per_cbm TYPE DECIMAL(15,4) USING round(rate_per_cbm::numeric, 4);
