CREATE TABLE dashboard_config (
    id BIGINT PRIMARY KEY,
    compliance_engine_api_key VARCHAR(255),
    fleet_hub_api_key VARCHAR(255),
    logistix_api_key VARCHAR(255)
);
