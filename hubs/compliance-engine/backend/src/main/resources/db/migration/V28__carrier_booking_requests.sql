CREATE TABLE carrier_booking_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shipment_order_id UUID NOT NULL REFERENCES shipment_orders(id) ON DELETE CASCADE,
    carrier_id UUID NOT NULL REFERENCES carriers(id) ON DELETE CASCADE,

    carrier_reference VARCHAR(100),
    carrier_tracking_number VARCHAR(200),
    carrier_booking_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    carrier_response_json TEXT,
    error_message VARCHAR(1000),

    service_type VARCHAR(50),
    special_instructions TEXT,

    requested_pickup_date DATE,
    estimated_pickup_date DATE,
    estimated_transit_days INTEGER,
    estimated_delivery_date DATE,
    quoted_cost DECIMAL(15, 2),
    quoted_cost_currency VARCHAR(3) DEFAULT 'EUR',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cbr_company ON carrier_booking_requests(company_id);
CREATE INDEX idx_cbr_shipment ON carrier_booking_requests(shipment_order_id);
CREATE INDEX idx_cbr_carrier ON carrier_booking_requests(carrier_id);
CREATE INDEX idx_cbr_status ON carrier_booking_requests(carrier_booking_status);
