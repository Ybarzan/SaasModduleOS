-- Fusion du domaine Transport (carrier/shipment/tracking) depuis compliance-engine
-- (com.incokalk) dans FleetHub : expéditions multimodales, transporteurs tiers,
-- tarifs, réservations, facturation transporteur, suivi live, groupage/consolidation,
-- prédiction ETA, et le portail client self-service (shippers suivant leurs propres
-- expéditions, authentification séparée des comptes staff AppUser).
--
-- Chaque entité portée garde son PK UUID d'origine (GenerationType.UUID, assigné côté
-- Java avant insertion -- pas de valeur par défaut nécessaire côté base). Seules les
-- références vers une société ou un utilisateur FleetHub sont BIGINT, pointant vers
-- company(id) / app_user(id) comme partout ailleurs dans ce schéma.

-- ---------------------------------------------------------------------------
-- Transporteurs tiers et tarifs
-- ---------------------------------------------------------------------------

CREATE TABLE carriers (
    id UUID PRIMARY KEY,
    company_id BIGINT,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500),
    transport_modes VARCHAR(255) NOT NULL,
    api_endpoint VARCHAR(500),
    api_key_encrypted VARCHAR(500),
    contact_name VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    country VARCHAR(3),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_carrier_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT uk_carrier_company_code UNIQUE (company_id, code)
);
CREATE INDEX idx_carriers_company ON carriers (company_id);

CREATE TABLE shipping_rates (
    id UUID PRIMARY KEY,
    carrier_id UUID NOT NULL,
    company_id BIGINT,
    name VARCHAR(255) NOT NULL,
    origin_country VARCHAR(3) NOT NULL,
    destination_country VARCHAR(3) NOT NULL,
    transport_mode VARCHAR(20) NOT NULL,
    min_weight_kg DOUBLE PRECISION,
    max_weight_kg DOUBLE PRECISION,
    base_rate DOUBLE PRECISION NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    rate_per_kg DOUBLE PRECISION NOT NULL DEFAULT 0,
    rate_per_cbm DOUBLE PRECISION NOT NULL DEFAULT 0,
    transit_days_min INTEGER,
    transit_days_max INTEGER,
    co2_estimate_kg DOUBLE PRECISION,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from TIMESTAMP(6),
    valid_until TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shipping_rate_carrier FOREIGN KEY (carrier_id) REFERENCES carriers ON DELETE CASCADE,
    CONSTRAINT fk_shipping_rate_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE
);
CREATE INDEX idx_shipping_rates_carrier ON shipping_rates (carrier_id);
CREATE INDEX idx_shipping_rates_route ON shipping_rates (origin_country, destination_country, transport_mode);

-- ---------------------------------------------------------------------------
-- Expéditions (cœur du domaine) et suivi
-- ---------------------------------------------------------------------------

CREATE TABLE shipment_orders (
    id UUID PRIMARY KEY,
    company_id BIGINT,
    user_id BIGINT,
    client_id UUID,
    order_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','QUOTED','BOOKED','IN_TRANSIT','DELIVERED','CANCELLED')),
    carrier_id UUID,
    -- Camion de la flotte propre assigné, si livrée en interne plutôt que par un
    -- transporteur tiers (carrier_id) -- relation JPA directe désormais que ce
    -- domaine vit dans FleetHub (voir LiveTrackingService). Remplace l'ancienne
    -- référence texte fleethub_truck_registration de compliance-engine (pont HTTP
    -- cross-hub, devenu inutile).
    truck_id BIGINT,
    shipping_rate_id UUID,

    shipper_name VARCHAR(255),
    shipper_address TEXT,
    shipper_city VARCHAR(255),
    shipper_country VARCHAR(3),
    shipper_postal_code VARCHAR(20),

    consignee_name VARCHAR(255),
    consignee_address TEXT,
    consignee_city VARCHAR(255),
    consignee_country VARCHAR(3),
    consignee_postal_code VARCHAR(20),

    goods_description TEXT,
    goods_value DOUBLE PRECISION,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    weight_kg DOUBLE PRECISION,
    volume_m3 DOUBLE PRECISION,
    packages_count INTEGER NOT NULL DEFAULT 1,
    hs_code VARCHAR(10),
    incoterm_code VARCHAR(5),
    is_dangerous BOOLEAN NOT NULL DEFAULT FALSE,
    country_of_origin VARCHAR(2),

    container_type VARCHAR(30) NOT NULL DEFAULT 'DRY_20FT' CHECK (container_type IN
        ('DRY_20FT','DRY_40FT','REEFER_20FT','REEFER_40FT','TANDEM_40FT','FLAT_RAIL',
         'OPEN_TOP_DRY','OPEN_TOP_REEFER','FRIGO_40FT','ISO_45','CUSTOM')),
    container_length_m DOUBLE PRECISION,
    container_width_m DOUBLE PRECISION,
    container_height_m DOUBLE PRECISION,
    refrigerated_min_celsius INTEGER,
    refrigerated_max_celsius INTEGER,
    is_insulated BOOLEAN NOT NULL DEFAULT FALSE,
    is_temperature_monitored BOOLEAN NOT NULL DEFAULT FALSE,
    custom_container_spec TEXT,

    customs_declaration_number VARCHAR(50),
    customs_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    duty_amount DOUBLE PRECISION,
    vat_amount DOUBLE PRECISION,
    landed_cost DOUBLE PRECISION,

    quoted_cost DOUBLE PRECISION,
    final_cost DOUBLE PRECISION,
    cost_currency VARCHAR(3) NOT NULL DEFAULT 'EUR',

    requested_pickup_date TIMESTAMP(6),
    estimated_delivery_date TIMESTAMP(6),
    actual_delivery_date TIMESTAMP(6),
    booked_at TIMESTAMP(6),
    shipped_at TIMESTAMP(6),

    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_shipment_order_number UNIQUE (order_number),
    CONSTRAINT fk_shipment_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_shipment_user FOREIGN KEY (user_id) REFERENCES app_user ON DELETE SET NULL,
    CONSTRAINT fk_shipment_carrier FOREIGN KEY (carrier_id) REFERENCES carriers ON DELETE SET NULL,
    CONSTRAINT fk_shipment_truck FOREIGN KEY (truck_id) REFERENCES truck ON DELETE SET NULL,
    CONSTRAINT fk_shipment_rate FOREIGN KEY (shipping_rate_id) REFERENCES shipping_rates ON DELETE SET NULL
);
CREATE INDEX idx_shipments_company ON shipment_orders (company_id);
CREATE INDEX idx_shipments_user ON shipment_orders (user_id);
CREATE INDEX idx_shipments_status ON shipment_orders (status);
CREATE INDEX idx_shipments_client ON shipment_orders (company_id, client_id);
CREATE INDEX idx_shipments_truck ON shipment_orders (truck_id);

CREATE TABLE tracking_events (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    location VARCHAR(500),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    description TEXT,
    event_time TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(100),
    data_source VARCHAR(10) NOT NULL DEFAULT 'MANUAL' CHECK (data_source IN ('LIVE','MANUAL')),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tracking_shipment FOREIGN KEY (shipment_id) REFERENCES shipment_orders ON DELETE CASCADE
);
CREATE INDEX idx_tracking_shipment ON tracking_events (shipment_id);

CREATE TABLE shipment_items (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    shipment_id UUID NOT NULL,
    item_id UUID,
    sku VARCHAR(100),
    name VARCHAR(300),
    description TEXT,
    hs_code VARCHAR(20),
    origin_country VARCHAR(3),
    quantity DECIMAL(15,2) NOT NULL DEFAULT 1,
    unit VARCHAR(10) NOT NULL DEFAULT 'PCS',
    unit_price DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shipment_item_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_shipment_item_shipment FOREIGN KEY (shipment_id) REFERENCES shipment_orders ON DELETE CASCADE
);
CREATE INDEX idx_shipment_items_company ON shipment_items (company_id);
CREATE INDEX idx_shipment_items_shipment ON shipment_items (shipment_id);

-- ---------------------------------------------------------------------------
-- Réservations transporteur et facturation (AP)
-- ---------------------------------------------------------------------------

CREATE TABLE carrier_booking_requests (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    shipment_order_id UUID NOT NULL,
    carrier_id UUID NOT NULL,

    carrier_reference VARCHAR(100),
    carrier_tracking_number VARCHAR(200),
    carrier_booking_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (carrier_booking_status IN ('PENDING','SUBMITTED','CONFIRMED','REJECTED','CANCELLED','COMPLETED','FAILED')),
    carrier_response_json TEXT,
    error_message VARCHAR(1000),

    service_type VARCHAR(50),
    special_instructions TEXT,

    requested_pickup_date DATE,
    estimated_pickup_date DATE,
    estimated_transit_days INTEGER,
    estimated_delivery_date DATE,
    quoted_cost DECIMAL(15,2),
    quoted_cost_currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    is_simulated BOOLEAN NOT NULL DEFAULT FALSE,

    container_type VARCHAR(30) CHECK (container_type IN
        ('DRY_20FT','DRY_40FT','REEFER_20FT','REEFER_40FT','TANDEM_40FT','FLAT_RAIL',
         'OPEN_TOP_DRY','OPEN_TOP_REEFER','FRIGO_40FT','ISO_45','CUSTOM')),
    container_length_m DOUBLE PRECISION,
    container_width_m DOUBLE PRECISION,
    container_height_m DOUBLE PRECISION,
    is_reefers BOOLEAN,
    refrigerated_min_celsius INTEGER,
    refrigerated_max_celsius INTEGER,
    is_insulated BOOLEAN,
    is_temperature_monitored BOOLEAN,
    custom_container_spec TEXT,
    quantity INTEGER,

    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cbr_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_cbr_user FOREIGN KEY (user_id) REFERENCES app_user ON DELETE CASCADE,
    CONSTRAINT fk_cbr_shipment FOREIGN KEY (shipment_order_id) REFERENCES shipment_orders ON DELETE CASCADE,
    CONSTRAINT fk_cbr_carrier FOREIGN KEY (carrier_id) REFERENCES carriers ON DELETE CASCADE
);
CREATE INDEX idx_cbr_company ON carrier_booking_requests (company_id);
CREATE INDEX idx_cbr_shipment ON carrier_booking_requests (shipment_order_id);
CREATE INDEX idx_cbr_carrier ON carrier_booking_requests (carrier_id);
CREATE INDEX idx_cbr_status ON carrier_booking_requests (carrier_booking_status);

CREATE TABLE carrier_invoices (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    carrier_id UUID,
    invoice_number VARCHAR(255) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('DRAFT','RECEIVED','UNDER_REVIEW','APPROVED','PAID','DISPUTED','REJECTED')),
    carrier_name VARCHAR(255),
    carrier_reference VARCHAR(255),
    total_amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    exchange_rate DECIMAL(10,6) NOT NULL DEFAULT 1,
    total_amount_eur DECIMAL(15,2),
    freight_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    fuel_surcharge DECIMAL(15,2) NOT NULL DEFAULT 0,
    security_fee DECIMAL(15,2) NOT NULL DEFAULT 0,
    handling_fee DECIMAL(15,2) NOT NULL DEFAULT 0,
    customs_fee DECIMAL(15,2) NOT NULL DEFAULT 0,
    other_charges DECIMAL(15,2) NOT NULL DEFAULT 0,
    other_charges_description VARCHAR(255),
    shipment_reference VARCHAR(255),
    shipment_id UUID,
    negotiated_rate DECIMAL(15,2),
    variance DECIMAL(15,2),
    variance_percent DECIMAL(8,2),
    reconciliation_notes TEXT,
    approved_by_user_id BIGINT,
    approved_at TIMESTAMP(6),
    paid_at TIMESTAMP(6),
    dispute_reason TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_carrier_invoice_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_carrier_invoice_carrier FOREIGN KEY (carrier_id) REFERENCES carriers ON DELETE SET NULL
);
CREATE INDEX idx_carrier_invoice_company ON carrier_invoices (company_id);
CREATE INDEX idx_carrier_invoice_status ON carrier_invoices (status);
CREATE INDEX idx_carrier_invoice_carrier ON carrier_invoices (carrier_id);
CREATE INDEX idx_carrier_invoice_date ON carrier_invoices (invoice_date);
CREATE INDEX idx_carrier_invoice_number ON carrier_invoices (invoice_number);

CREATE TABLE carrier_invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity DECIMAL(10,3) NOT NULL DEFAULT 1,
    unit_type VARCHAR(255),
    unit_price DECIMAL(15,4),
    amount DECIMAL(15,2) NOT NULL,
    hs_code VARCHAR(255),
    origin VARCHAR(255),
    destination VARCHAR(255),

    CONSTRAINT fk_carrier_invoice_line_invoice FOREIGN KEY (invoice_id) REFERENCES carrier_invoices ON DELETE CASCADE
);
CREATE INDEX idx_carrier_invoice_lines_invoice ON carrier_invoice_lines (invoice_id);

-- ---------------------------------------------------------------------------
-- Groupage / consolidation multi-exportateurs (co-loading)
-- ---------------------------------------------------------------------------

CREATE TABLE groupages (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    reference VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED'
        CHECK (status IN ('PLANNED','FORMING','BOOKED','DEPARTED','DELIVERED','CANCELLED')),
    transport_mode VARCHAR(10),
    carrier_name VARCHAR(120),
    origin VARCHAR(100),
    destination VARCHAR(100),
    capacity_weight_kg DECIMAL(12,2),
    capacity_volume_m3 DECIMAL(12,2),
    booked_weight_kg DECIMAL(12,2) NOT NULL DEFAULT 0,
    booked_volume_m3 DECIMAL(12,2) NOT NULL DEFAULT 0,
    planned_departure DATE,
    planned_arrival DATE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_groupage_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE
);
CREATE INDEX idx_groupages_company ON groupages (company_id);
CREATE INDEX idx_groupages_status ON groupages (status);

CREATE TABLE groupage_members (
    id UUID PRIMARY KEY,
    groupage_id UUID NOT NULL,
    shipment_order_id UUID,
    company_id BIGINT NOT NULL,
    external_company VARCHAR(150),
    reference VARCHAR(60),
    weight_kg DECIMAL(12,2) NOT NULL DEFAULT 0,
    volume_m3 DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_groupage_member_groupage FOREIGN KEY (groupage_id) REFERENCES groupages ON DELETE CASCADE,
    CONSTRAINT fk_groupage_member_shipment FOREIGN KEY (shipment_order_id) REFERENCES shipment_orders ON DELETE SET NULL,
    CONSTRAINT fk_groupage_member_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE
);
CREATE INDEX idx_groupage_members_groupage ON groupage_members (groupage_id);
CREATE INDEX idx_groupage_members_shipment ON groupage_members (shipment_order_id);

-- ---------------------------------------------------------------------------
-- Prédiction ETA (temps d'arrivée)
-- ---------------------------------------------------------------------------

CREATE TABLE eta_predictions (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    shipment_id UUID,
    origin VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    mode VARCHAR(10) NOT NULL,
    carrier_name VARCHAR(255),
    predicted_arrival TIMESTAMP(6) NOT NULL,
    confidence_percent DECIMAL(5,2),
    confidence_level VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK (confidence_level IN ('HIGH','MEDIUM','LOW')),
    baseline_days INTEGER,
    predicted_days INTEGER,
    carrier_estimate_days INTEGER,
    variance_days INTEGER,
    risk_factors TEXT,
    seasonal_factor DECIMAL(5,2) NOT NULL DEFAULT 1,
    congestion_factor DECIMAL(5,2) NOT NULL DEFAULT 1,
    customs_delay_days INTEGER NOT NULL DEFAULT 0,
    weather_delay_days INTEGER NOT NULL DEFAULT 0,
    is_on_time BOOLEAN,
    actual_arrival TIMESTAMP(6),
    actual_days INTEGER,
    prediction_accuracy DECIMAL(8,2),
    notes TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_eta_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_eta_shipment FOREIGN KEY (shipment_id) REFERENCES shipment_orders ON DELETE SET NULL
);
CREATE INDEX idx_eta_company ON eta_predictions (company_id);
CREATE INDEX idx_eta_shipment ON eta_predictions (shipment_id);
CREATE INDEX idx_eta_lane ON eta_predictions (origin, destination);
CREATE INDEX idx_eta_mode ON eta_predictions (mode);

CREATE TABLE eta_model_coefficients (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    feature_name VARCHAR(50) NOT NULL,
    feature_value VARCHAR(100) NOT NULL,
    coefficient DECIMAL(12,6) NOT NULL,
    samples_count INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    trained_at TIMESTAMP(6),
    intercept DECIMAL(12,6) NOT NULL DEFAULT 0,
    r_squared DECIMAL(8,6),

    CONSTRAINT fk_eta_coeff_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT uk_eta_coeff UNIQUE (company_id, feature_name, feature_value)
);

-- ---------------------------------------------------------------------------
-- Portail client self-service (shippers suivant leurs propres expéditions) --
-- authentification séparée des comptes staff app_user, voir ClientAuthFilter.
-- ---------------------------------------------------------------------------

CREATE TABLE client_users (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    phone VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_client_user_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT uk_client_email_company UNIQUE (company_id, email)
);
CREATE INDEX idx_client_users_company ON client_users (company_id);
CREATE INDEX idx_client_users_email ON client_users (email);

CREATE TABLE shared_links (
    id UUID PRIMARY KEY,
    company_id BIGINT NOT NULL,
    shipment_id UUID NOT NULL,
    token VARCHAR(36) NOT NULL,
    created_by BIGINT,
    label VARCHAR(255),
    expires_at TIMESTAMP(6),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    access_count INTEGER NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_shared_link_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT fk_shared_link_shipment FOREIGN KEY (shipment_id) REFERENCES shipment_orders ON DELETE CASCADE,
    CONSTRAINT fk_shared_link_user FOREIGN KEY (created_by) REFERENCES app_user ON DELETE SET NULL,
    CONSTRAINT uk_shared_link_token UNIQUE (token)
);
CREATE INDEX idx_shared_links_company ON shared_links (company_id);
CREATE INDEX idx_shared_links_shipment ON shared_links (shipment_id);

-- ---------------------------------------------------------------------------
-- Fournisseurs de devis transport (DHL, Shippo, ...) -- équivalent local,
-- propre à ce seul domaine, du ProviderConfig générique de compliance-engine
-- (partagé là-bas avec ERP/fintech) -- volontairement non importé.
-- ---------------------------------------------------------------------------

CREATE TABLE carrier_provider_config (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    api_key_encrypted VARCHAR(500),
    api_secret VARCHAR(500),
    priority INTEGER NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    health_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    last_health_check TIMESTAMP(6),
    config_json TEXT,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),

    CONSTRAINT fk_carrier_provider_config_company FOREIGN KEY (company_id) REFERENCES company ON DELETE CASCADE,
    CONSTRAINT uk_carrier_provider_config UNIQUE (company_id, provider_type)
);

CREATE TABLE carrier_rate_cache (
    id BIGSERIAL PRIMARY KEY,
    provider_type VARCHAR(50) NOT NULL,
    cache_key VARCHAR(255) NOT NULL,
    response_json TEXT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_carrier_rate_cache_key UNIQUE (cache_key)
);
