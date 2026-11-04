-- Ouverture multi-flottes : profil métier par société, nouvelles catégories de
-- véhicules (VUL, VL), nouvelles énergies et tachygraphe optionnel par véhicule.

ALTER TABLE company ADD COLUMN fleet_profile VARCHAR(32) NOT NULL DEFAULT 'POIDS_LOURD';
ALTER TABLE company ADD CONSTRAINT company_fleet_profile_check
    CHECK (fleet_profile IN ('POIDS_LOURD','MESSAGERIE','COLLECTE_SANTE','MIXTE'));

-- Contraintes CHECK générées par Hibernate (nommage PostgreSQL par défaut <table>_<colonne>_check)
ALTER TABLE truck DROP CONSTRAINT IF EXISTS truck_truck_type_check;
ALTER TABLE truck ADD CONSTRAINT truck_truck_type_check
    CHECK (truck_type IN ('TRACTEUR','PORTEUR','FOURGON','VUL','VL'));

ALTER TABLE truck DROP CONSTRAINT IF EXISTS truck_fuel_type_check;
ALTER TABLE truck ADD CONSTRAINT truck_fuel_type_check
    CHECK (fuel_type IN ('DIESEL','ELECTRIC','ESSENCE','HYBRIDE','GNV'));

-- NULL = déduit du type (poids lourd => équipé)
ALTER TABLE truck ADD COLUMN tachograph_equipped BOOLEAN;
