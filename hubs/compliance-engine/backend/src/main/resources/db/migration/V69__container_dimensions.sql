-- The whole container-spec/reefer block is mapped in ShipmentOrder.java and
-- CarrierBookingRequest.java (container type/dimensions, refrigeration range,
-- insulation, custom spec) but never got a migration -- only surfaced against a
-- real Postgres, since the H2 dev profile auto-generates its schema and never
-- hit this gap.
ALTER TABLE shipment_orders
    ADD COLUMN container_type VARCHAR(30) DEFAULT 'DRY_20FT',
    ADD COLUMN container_length_m DOUBLE PRECISION,
    ADD COLUMN container_width_m DOUBLE PRECISION,
    ADD COLUMN container_height_m DOUBLE PRECISION,
    ADD COLUMN refrigerated_min_celsius INTEGER,
    ADD COLUMN refrigerated_max_celsius INTEGER,
    ADD COLUMN is_insulated BOOLEAN DEFAULT FALSE,
    ADD COLUMN is_temperature_monitored BOOLEAN DEFAULT FALSE,
    ADD COLUMN custom_container_spec TEXT;

ALTER TABLE carrier_booking_requests
    ADD COLUMN container_type VARCHAR(30),
    ADD COLUMN container_length_m DOUBLE PRECISION,
    ADD COLUMN container_width_m DOUBLE PRECISION,
    ADD COLUMN container_height_m DOUBLE PRECISION,
    ADD COLUMN is_reefers BOOLEAN,
    ADD COLUMN refrigerated_min_celsius INTEGER,
    ADD COLUMN refrigerated_max_celsius INTEGER,
    ADD COLUMN is_insulated BOOLEAN,
    ADD COLUMN is_temperature_monitored BOOLEAN,
    ADD COLUMN custom_container_spec TEXT,
    ADD COLUMN quantity INTEGER;
