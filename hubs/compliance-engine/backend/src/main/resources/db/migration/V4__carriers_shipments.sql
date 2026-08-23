-- ============================================================
-- V4 — TMS Phase 1: Carriers, Shipping Rates, Shipments, Tracking
-- ============================================================

-- carriers: transporteur
CREATE TABLE carriers (
    id UUID PRIMARY KEY,
    company_id UUID,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    logo_url VARCHAR(500),
    transport_modes VARCHAR(255) NOT NULL,
    api_endpoint VARCHAR(500),
    api_key_encrypted VARCHAR(500),
    contact_name VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    country VARCHAR(3),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- shipping_rates: tarifs par transporteur
CREATE TABLE shipping_rates (
    id UUID PRIMARY KEY,
    carrier_id UUID NOT NULL,
    company_id UUID,
    name VARCHAR(255) NOT NULL,
    origin_country VARCHAR(3) NOT NULL,
    destination_country VARCHAR(3) NOT NULL,
    transport_mode VARCHAR(20) NOT NULL,
    min_weight_kg DOUBLE PRECISION,
    max_weight_kg DOUBLE PRECISION,
    base_rate DOUBLE PRECISION NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    rate_per_kg DOUBLE PRECISION DEFAULT 0,
    rate_per_cbm DOUBLE PRECISION DEFAULT 0,
    transit_days_min INTEGER,
    transit_days_max INTEGER,
    co2_estimate_kg DOUBLE PRECISION,
    is_active BOOLEAN DEFAULT TRUE,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (carrier_id) REFERENCES carriers(id) ON DELETE CASCADE,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- shipment_orders: commandes d'expédition
CREATE TABLE shipment_orders (
    id UUID PRIMARY KEY,
    company_id UUID,
    user_id UUID,
    order_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) DEFAULT 'DRAFT',
    carrier_id UUID,
    shipping_rate_id UUID,

    -- Shipper info
    shipper_name VARCHAR(255),
    shipper_address TEXT,
    shipper_city VARCHAR(255),
    shipper_country VARCHAR(3),
    shipper_postal_code VARCHAR(20),

    -- Consignee info
    consignee_name VARCHAR(255),
    consignee_address TEXT,
    consignee_city VARCHAR(255),
    consignee_country VARCHAR(3),
    consignee_postal_code VARCHAR(20),

    -- Cargo
    goods_description TEXT,
    goods_value DOUBLE PRECISION,
    currency VARCHAR(3) DEFAULT 'EUR',
    weight_kg DOUBLE PRECISION,
    volume_m3 DOUBLE PRECISION,
    packages_count INTEGER DEFAULT 1,
    hs_code VARCHAR(10),
    incoterm_code VARCHAR(5),
    is_dangerous BOOLEAN DEFAULT FALSE,

    -- Cost
    quoted_cost DOUBLE PRECISION,
    final_cost DOUBLE PRECISION,
    cost_currency VARCHAR(3) DEFAULT 'EUR',

    -- Dates
    requested_pickup_date TIMESTAMP,
    estimated_delivery_date TIMESTAMP,
    actual_delivery_date TIMESTAMP,
    booked_at TIMESTAMP,
    shipped_at TIMESTAMP,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (carrier_id) REFERENCES carriers(id) ON DELETE SET NULL,
    FOREIGN KEY (shipping_rate_id) REFERENCES shipping_rates(id) ON DELETE SET NULL
);

-- tracking_events: jalons de suivi
CREATE TABLE tracking_events (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    location VARCHAR(500),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    description TEXT,
    event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(100),
    FOREIGN KEY (shipment_id) REFERENCES shipment_orders(id) ON DELETE CASCADE
);

CREATE INDEX idx_carriers_company ON carriers(company_id);
CREATE INDEX idx_shipping_rates_carrier ON shipping_rates(carrier_id);
CREATE INDEX idx_shipping_rates_route ON shipping_rates(origin_country, destination_country, transport_mode);
CREATE INDEX idx_shipments_company ON shipment_orders(company_id);
CREATE INDEX idx_shipments_user ON shipment_orders(user_id);
CREATE INDEX idx_shipments_status ON shipment_orders(status);
CREATE INDEX idx_tracking_shipment ON tracking_events(shipment_id);
