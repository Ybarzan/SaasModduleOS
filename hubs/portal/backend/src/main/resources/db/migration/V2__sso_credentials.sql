CREATE TABLE sso_credentials (
    id BIGINT PRIMARY KEY,
    compliance_engine_email VARCHAR(255),
    compliance_engine_password VARCHAR(255),
    fleet_hub_email VARCHAR(255),
    fleet_hub_password VARCHAR(255),
    logistix_email VARCHAR(255),
    logistix_password VARCHAR(255)
);
