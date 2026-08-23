CREATE TABLE notification_rules (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,

    -- Channels
    send_email BOOLEAN DEFAULT FALSE,
    send_webhook BOOLEAN DEFAULT FALSE,
    send_in_app BOOLEAN DEFAULT TRUE,

    -- Email config
    email_recipients TEXT,

    -- Webhook config
    webhook_url VARCHAR(500),
    webhook_secret VARCHAR(255),

    -- Filters (optional)
    filter_status VARCHAR(50),
    filter_carrier_id UUID,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    user_id UUID,
    rule_id UUID,

    event_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,

    -- Channels used
    channel VARCHAR(20) NOT NULL,

    -- Status
    status VARCHAR(20) DEFAULT 'UNREAD',
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,

    -- Reference to the entity that triggered this
    entity_type VARCHAR(50),
    entity_id UUID,

    -- Webhook delivery
    webhook_status VARCHAR(20),
    webhook_response_code INTEGER,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (rule_id) REFERENCES notification_rules(id) ON DELETE SET NULL
);

CREATE INDEX idx_notif_rules_company ON notification_rules(company_id);
CREATE INDEX idx_notif_rules_event ON notification_rules(event_type);
CREATE INDEX idx_notifications_company ON notifications(company_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_entity ON notifications(entity_type, entity_id);
