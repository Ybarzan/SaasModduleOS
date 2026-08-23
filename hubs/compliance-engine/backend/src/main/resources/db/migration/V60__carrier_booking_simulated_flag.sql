-- Rend visible en base la distinction reelle vs simulee sur les reservations transporteur.
-- Jusqu'ici, un adaptateur sans cle API configuree renvoyait une reponse simulee (cout,
-- delais, reference inventes) sans qu'aucun champ structure ne le signale -- seul un
-- "source": "simulation" enfoui dans un Map<String,Object> additionalData l'indiquait,
-- jamais lu ni affiche. Voir docs/01-audit-existant.md et docs/05-estimation-couts-risques.md
-- (risque R2) du monorepo SaasModduleOS.
ALTER TABLE carrier_booking_requests
    ADD COLUMN is_simulated BOOLEAN NOT NULL DEFAULT false;
