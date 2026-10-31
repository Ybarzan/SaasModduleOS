-- Purge des taux de douane INVENTÉS enregistrés comme données TARIC.
--
-- En mode simulation (incokalk.taric.simulation-mode, vrai par défaut), TaricApiClient
-- fabriquait un taux « MFN simulé » par chapitre HS (ex. 1,8 % pour tout le chapitre 84) et
-- CustomsDutyService l'enregistrait dans taric_rates via TaricSyncService.saveRates. Ces lignes
-- étaient ensuite relues comme de vraies mesures, y compris pour des destinations hors UE
-- (FR→US, FR→CN) auxquelles le tarif de l'Union ne s'applique pas. Le code ne persiste plus
-- rien en mode simulation ; cette migration retire ce qui a déjà été écrit.

-- 1. Taux MFN simulés (libellé posé par TaricApiClient.simulateRates).
DELETE FROM taric_rates
WHERE description LIKE '%(taux MFN simulé)%';

-- 2. Taux préférentiels simulés : libellé exactement « Taux préférentiel <code accord> ».
DELETE FROM taric_rates
WHERE is_prefential = TRUE
  AND trade_agreement_code IS NOT NULL
  AND description = 'Taux préférentiel ' || trade_agreement_code;

-- 3. TARIC est le tarif douanier commun de l'Union : une ligne dont la destination est hors UE
--    ne peut pas en provenir.
DELETE FROM taric_rates
WHERE destination_country NOT IN (
    'FR','DE','IT','ES','PT','NL','BE','LU','AT','FI','SE','DK','IE','GR',
    'PL','CZ','SK','HU','RO','BG','HR','SI','EE','LV','LT','CY','MT'
);
