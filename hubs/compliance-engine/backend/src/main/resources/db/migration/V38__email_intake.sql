-- Email intake for automatic quote/shipment creation from emails
CREATE TABLE email_intakes (
    id UUID PRIMARY KEY,
    sender_email VARCHAR(255) NOT NULL,
    sender_name VARCHAR(255),
    subject VARCHAR(500) NOT NULL,
    body_preview TEXT,
    origin VARCHAR(200),
    destination VARCHAR(200),
    goods_description VARCHAR(500),
    estimated_weight DECIMAL(12, 2),
    estimated_volume DECIMAL(12, 2),
    incoterm VARCHAR(10),
    matched_client_id UUID,
    matched_company_id UUID,
    created_shipment_id UUID,
    status VARCHAR(50) NOT NULL DEFAULT 'PARSED',
    error_message VARCHAR(1000),
    received_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_intakes_company ON email_intakes(matched_company_id);
CREATE INDEX idx_email_intakes_status ON email_intakes(status);
CREATE INDEX idx_email_intakes_sender ON email_intakes(sender_email);
CREATE INDEX idx_email_intakes_received ON email_intakes(received_at DESC);

COMMENT ON TABLE email_intakes IS 'Emails reçus et parsés pour création automatique de devis/expéditions';
