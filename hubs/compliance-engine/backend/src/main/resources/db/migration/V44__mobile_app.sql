-- V44__mobile_app.sql
-- Mobile native app support: device registration + push notifications

-- ============================================================
-- Mobile Devices
-- ============================================================
CREATE TABLE IF NOT EXISTS mobile_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,
    device_token VARCHAR(500) NOT NULL,
    platform VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    app_version VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_mobile_devices_company ON mobile_devices(company_id);
CREATE INDEX IF NOT EXISTS idx_mobile_devices_user ON mobile_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_mobile_devices_token ON mobile_devices(device_token);

-- ============================================================
-- Mobile Notifications (push)
-- ============================================================
CREATE TABLE IF NOT EXISTS mobile_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,
    title VARCHAR(300) NOT NULL,
    body TEXT,
    type VARCHAR(50) NOT NULL,
    reference_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_mobile_notif_company ON mobile_notifications(company_id);
CREATE INDEX IF NOT EXISTS idx_mobile_notif_user ON mobile_notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_mobile_notif_read ON mobile_notifications(is_read);
CREATE INDEX IF NOT EXISTS idx_mobile_notif_type ON mobile_notifications(type);
