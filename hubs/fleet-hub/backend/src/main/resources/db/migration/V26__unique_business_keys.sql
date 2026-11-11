-- Unicité des clés métier par société : l'immatriculation d'un véhicule et le numéro de
-- permis d'un chauffeur. Jusqu'ici seul le code applicatif le vérifiait (deux requêtes
-- simultanées passaient) ; un doublon faisait ensuite échouer toute recherche par clé
-- métier (ingestion GPS / tachygraphe / carburant, imports, marketplace).
--
-- Si des doublons existent déjà, la migration s'arrête avec la liste à corriger plutôt
-- que de choisir arbitrairement quelle ligne garder.
DO $$
DECLARE
    trucks TEXT;
    drivers TEXT;
BEGIN
    SELECT string_agg(format('société %s : %s (%s véhicules)', company_id, registration, n), '; ')
      INTO trucks
      FROM (SELECT company_id, registration, count(*) AS n FROM truck
            GROUP BY company_id, registration HAVING count(*) > 1) d;
    SELECT string_agg(format('société %s : %s (%s chauffeurs)', company_id, license_number, n), '; ')
      INTO drivers
      FROM (SELECT company_id, license_number, count(*) AS n FROM driver
            GROUP BY company_id, license_number HAVING count(*) > 1) d;
    IF trucks IS NOT NULL OR drivers IS NOT NULL THEN
        RAISE EXCEPTION 'Doublons à corriger avant V26 — immatriculations : % — permis : %',
            coalesce(trucks, 'aucun'), coalesce(drivers, 'aucun');
    END IF;
END $$;

DROP INDEX IF EXISTS idx_truck_company_registration;
ALTER TABLE truck ADD CONSTRAINT uk_truck_company_registration UNIQUE (company_id, registration);
ALTER TABLE driver ADD CONSTRAINT uk_driver_company_license UNIQUE (company_id, license_number);
