-- P5: Intégrations Gouvernementales France
-- DEB auto-generation tracking + French fiscal data + DGDDI submission log

CREATE TABLE french_fiscal_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL,
    eori_number         VARCHAR(20),
    default_regime      VARCHAR(10),
    tai_applicable      BOOLEAN DEFAULT true,
    accises_applicable  BOOLEAN DEFAULT true,
    regime_perfectionnement_actif BOOLEAN DEFAULT false,
    created_at          TIMESTAMP DEFAULT now(),
    updated_at          TIMESTAMP DEFAULT now(),
    CONSTRAINT fk_french_fiscal_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE INDEX idx_french_fiscal_company ON french_fiscal_config(company_id);

CREATE TABLE dgddi_submissions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id              UUID NOT NULL,
    declaration_ref         VARCHAR(50) NOT NULL,
    eori_sender             VARCHAR(20) NOT NULL,
    message_type            VARCHAR(10),
    procedure_code          VARCHAR(10),
    regime_code             VARCHAR(10),
    country_of_export       VARCHAR(2),
    country_of_destination  VARCHAR(2),
    items_count             INT DEFAULT 0,
    cif_value               NUMERIC(15,2),
    status                  VARCHAR(20) DEFAULT 'DRAFT',
    edifact_message         TEXT,
    acknowledgment_ref      VARCHAR(50),
    submitted_at            TIMESTAMP,
    acknowledged_at         TIMESTAMP,
    error_message           TEXT,
    created_at              TIMESTAMP DEFAULT now(),
    updated_at              TIMESTAMP DEFAULT now(),
    CONSTRAINT fk_dgddi_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT chk_dgddi_status CHECK (status IN ('DRAFT','READY','SUBMITTED','ACKNOWLEDGED','ACCEPTED','REJECTED','ERROR'))
);

CREATE INDEX idx_dgddi_company ON dgddi_submissions(company_id);
CREATE INDEX idx_dgddi_status ON dgddi_submissions(status);

CREATE TABLE deb_auto_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL,
    reporting_period INT NOT NULL,
    shipments_count INT DEFAULT 0,
    generated_count INT DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'PENDING',
    triggered_by    VARCHAR(50),
    started_at      TIMESTAMP DEFAULT now(),
    completed_at    TIMESTAMP,
    notes           TEXT,
    created_at      TIMESTAMP DEFAULT now(),
    CONSTRAINT fk_deb_log_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE INDEX idx_deb_log_company ON deb_auto_log(company_id);
CREATE INDEX idx_deb_log_period ON deb_auto_log(reporting_period);
