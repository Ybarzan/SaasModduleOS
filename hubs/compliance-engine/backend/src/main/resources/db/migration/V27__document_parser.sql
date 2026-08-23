-- V27__document_parser.sql
-- P2: Document Parser / OCR — parsed documents tracking

CREATE TABLE IF NOT EXISTS parsed_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    document_type VARCHAR(30) NOT NULL,
    original_filename VARCHAR(500),
    file_key VARCHAR(500),
    raw_text TEXT,
    parsed_data JSONB,
    confidence DECIMAL(4,3),
    status VARCHAR(20) NOT NULL DEFAULT 'PARSED',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_parsed_doc_company ON parsed_documents(company_id);
CREATE INDEX idx_parsed_doc_type ON parsed_documents(document_type);
CREATE INDEX idx_parsed_doc_status ON parsed_documents(status);
CREATE INDEX idx_parsed_doc_created_at ON parsed_documents(created_at);
