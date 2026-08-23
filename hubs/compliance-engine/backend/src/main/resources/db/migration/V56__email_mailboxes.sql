-- Configuration de boîtes email par entreprise pour l'import automatique de documents
-- (auparavant une seule boîte globale configurée en variables d'environnement).
CREATE TABLE email_mailboxes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    imap_host VARCHAR(255) NOT NULL,
    imap_port INTEGER NOT NULL DEFAULT 993,
    username VARCHAR(255) NOT NULL,
    encrypted_password VARCHAR(1000) NOT NULL,
    folder VARCHAR(100) NOT NULL DEFAULT 'INBOX',
    protocol VARCHAR(10) NOT NULL DEFAULT 'IMAP',
    ssl_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auto_import BOOLEAN NOT NULL DEFAULT FALSE,
    target_document_type VARCHAR(30) NOT NULL DEFAULT 'SHIPMENT_ORDER',
    delete_after_import BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_check_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_mailboxes_company_id ON email_mailboxes(company_id);
CREATE INDEX idx_email_mailboxes_active ON email_mailboxes(is_active) WHERE is_active = TRUE;

CREATE TABLE email_intake_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_mailbox_id UUID NOT NULL REFERENCES email_mailboxes(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    message VARCHAR(1000),
    processed_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP
);

CREATE INDEX idx_email_intake_logs_mailbox_id ON email_intake_logs(email_mailbox_id);

ALTER TABLE email_intakes ADD COLUMN mailbox_id UUID REFERENCES email_mailboxes(id) ON DELETE SET NULL;
