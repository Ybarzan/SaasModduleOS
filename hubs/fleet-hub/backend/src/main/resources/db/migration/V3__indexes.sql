-- Index company_id sur toutes les tables multi-tenant (requêtes filtrées par tenant)
CREATE INDEX idx_assignment_company ON assignment (company_id);
CREATE INDEX idx_cost_record_company ON cost_record (company_id);
CREATE INDEX idx_driver_company ON driver (company_id);
CREATE INDEX idx_driving_event_company ON driving_event (company_id);
CREATE INDEX idx_fuel_record_company ON fuel_record (company_id);
CREATE INDEX idx_maintenance_record_company ON maintenance_record (company_id);
CREATE INDEX idx_notification_company ON notification (company_id);
CREATE INDEX idx_notification_rule_company ON notification_rule (company_id);
CREATE INDEX idx_tachograph_day_company ON tachograph_day (company_id);
CREATE INDEX idx_trip_company ON trip (company_id);
CREATE INDEX idx_truck_company ON truck (company_id);
CREATE INDEX idx_app_user_company ON app_user (company_id);

-- Index composites pour les requêtes KPI / dashboard (fréquentes et coûteuses)
CREATE INDEX idx_cost_record_company_month ON cost_record (company_id, billing_month);
CREATE INDEX idx_driving_event_company_driver_ts ON driving_event (company_id, driver_id, timestamp);
CREATE INDEX idx_trip_company_driver_start ON trip (company_id, driver_id, start_time);
CREATE INDEX idx_trip_company_truck ON trip (company_id, truck_id);
CREATE INDEX idx_fuel_record_company_truck ON fuel_record (company_id, truck_id);
CREATE INDEX idx_maintenance_record_company_truck ON maintenance_record (company_id, truck_id);
CREATE INDEX idx_tachograph_day_company_driver_date ON tachograph_day (company_id, driver_id, date);
CREATE INDEX idx_assignment_active_company ON assignment (company_id, active);
CREATE INDEX idx_truck_company_registration ON truck (company_id, registration);
