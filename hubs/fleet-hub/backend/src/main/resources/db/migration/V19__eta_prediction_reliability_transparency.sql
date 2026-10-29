-- Phase 2 (UI transparence du score, docs/03-plan-migration.md) : expose si
-- la pondération par fiabilité réellement mesurée (EtaSourceReliabilityService)
-- a été appliquée à cette prédiction, et avec combien d'échantillons réels --
-- jusqu'ici calculé puis jeté dans EtaPredictionService.applyReliabilityWeighting(),
-- invisible pour l'utilisateur qui ne pouvait pas distinguer un score mesuré
-- d'un repli heuristique affichant pourtant le même pourcentage.

ALTER TABLE eta_predictions ADD COLUMN reliability_weighted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE eta_predictions ADD COLUMN reliability_sample_count INTEGER;
