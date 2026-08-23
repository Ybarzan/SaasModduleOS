-- V19__p3_billing_eta_financials.sql
-- P3: Carrier Invoice Billing, ETA Predictions, Shipment Financials

-- ============================================================
-- Carrier Invoices
-- ============================================================
CREATE TABLE IF NOT EXISTS carrier_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    carrier_id UUID REFERENCES carriers(id) ON DELETE SET NULL,
    invoice_number VARCHAR(100) NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    carrier_name VARCHAR(200),
    carrier_reference VARCHAR(100),
    total_amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    exchange_rate DECIMAL(10,6) DEFAULT 1,
    total_amount_eur DECIMAL(15,2),
    freight_amount DECIMAL(15,2) DEFAULT 0,
    fuel_surcharge DECIMAL(15,2) DEFAULT 0,
    security_fee DECIMAL(15,2) DEFAULT 0,
    handling_fee DECIMAL(15,2) DEFAULT 0,
    customs_fee DECIMAL(15,2) DEFAULT 0,
    other_charges DECIMAL(15,2) DEFAULT 0,
    other_charges_description VARCHAR(500),
    shipment_reference VARCHAR(100),
    shipment_id UUID,
    negotiated_rate DECIMAL(10,2),
    variance DECIMAL(15,2),
    variance_percent DECIMAL(8,4),
    reconciliation_notes TEXT,
    approved_by_user_id UUID,
    approved_at TIMESTAMP,
    paid_at TIMESTAMP,
    dispute_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_carrier_invoice_company ON carrier_invoices(company_id);
CREATE INDEX idx_carrier_invoice_status ON carrier_invoices(status);
CREATE INDEX idx_carrier_invoice_carrier ON carrier_invoices(carrier_id);
CREATE INDEX idx_carrier_invoice_date ON carrier_invoices(invoice_date);
CREATE INDEX idx_carrier_invoice_number ON carrier_invoices(invoice_number);

-- ============================================================
-- Carrier Invoice Lines
-- ============================================================
CREATE TABLE IF NOT EXISTS carrier_invoice_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES carrier_invoices(id) ON DELETE CASCADE,
    description VARCHAR(500) NOT NULL,
    quantity DECIMAL(10,2) DEFAULT 1,
    unit_type VARCHAR(20),
    unit_price DECIMAL(10,2),
    amount DECIMAL(15,2) NOT NULL,
    hs_code VARCHAR(10),
    origin VARCHAR(2),
    destination VARCHAR(2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_carrier_invoice_lines_invoice ON carrier_invoice_lines(invoice_id);

-- ============================================================
-- ETA Predictions
-- ============================================================
CREATE TABLE IF NOT EXISTS eta_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID REFERENCES shipment_orders(id) ON DELETE SET NULL,
    origin VARCHAR(10) NOT NULL,
    destination VARCHAR(10) NOT NULL,
    mode VARCHAR(10),
    carrier_name VARCHAR(200),
    predicted_arrival TIMESTAMP NOT NULL,
    confidence_percent DECIMAL(5,2),
    confidence_level VARCHAR(10),
    baseline_days INTEGER,
    predicted_days INTEGER,
    carrier_estimate_days INTEGER,
    variance_days INTEGER,
    risk_factors TEXT,
    seasonal_factor DECIMAL(5,3) DEFAULT 1,
    congestion_factor DECIMAL(5,3) DEFAULT 1,
    customs_delay_days INTEGER DEFAULT 0,
    weather_delay_days INTEGER DEFAULT 0,
    is_on_time BOOLEAN,
    actual_arrival TIMESTAMP,
    actual_days INTEGER,
    prediction_accuracy DECIMAL(5,2),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_eta_company ON eta_predictions(company_id);
CREATE INDEX idx_eta_shipment ON eta_predictions(shipment_id);
CREATE INDEX idx_eta_lane ON eta_predictions(origin, destination);
CREATE INDEX idx_eta_mode ON eta_predictions(mode);

-- ============================================================
-- Shipment Financials
-- ============================================================
CREATE TABLE IF NOT EXISTS shipment_financials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    shipment_id UUID NOT NULL REFERENCES shipment_orders(id) ON DELETE CASCADE,
    client_name VARCHAR(200),
    origin VARCHAR(10),
    destination VARCHAR(10),
    mode VARCHAR(10),
    carrier_name VARCHAR(200),
    revenue DECIMAL(15,2) DEFAULT 0,
    revenue_currency VARCHAR(3) DEFAULT 'EUR',
    cost_freight DECIMAL(15,2) DEFAULT 0,
    cost_fuel DECIMAL(15,2) DEFAULT 0,
    cost_handling DECIMAL(15,2) DEFAULT 0,
    cost_customs DECIMAL(15,2) DEFAULT 0,
    cost_insurance DECIMAL(15,2) DEFAULT 0,
    cost_warehouse DECIMAL(15,2) DEFAULT 0,
    cost_last_mile DECIMAL(15,2) DEFAULT 0,
    cost_other DECIMAL(15,2) DEFAULT 0,
    total_cost DECIMAL(15,2) DEFAULT 0,
    gross_margin DECIMAL(15,2) DEFAULT 0,
    gross_margin_percent DECIMAL(8,4) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_shipment_financials_company ON shipment_financials(company_id);
CREATE UNIQUE INDEX idx_shipment_financials_shipment ON shipment_financials(shipment_id);
CREATE INDEX idx_shipment_financials_lane ON shipment_financials(origin, destination);
