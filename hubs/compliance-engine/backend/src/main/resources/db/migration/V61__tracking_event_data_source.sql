-- Meme principe de transparence que V60 (carrier_booking_requests.is_simulated),
-- adapte au tracking : aucun provider de tracking ne fabrique de fausses donnees
-- (ils renvoient une liste vide en echec), donc la distinction pertinente ici est
-- LIVE (provider externe verifie ou webhook transporteur) vs MANUAL (saisie humaine
-- via le changement de statut) -- pas "simule".
ALTER TABLE tracking_events
    ADD COLUMN data_source VARCHAR(10) NOT NULL DEFAULT 'MANUAL';

-- Backfill best-effort des lignes existantes a partir du texte libre "source" deja
-- en base (AviationStack/VesselAPI/Ship24 = sync provider ; DHL/SHIPPO/webhook = push
-- transporteur entrant). Les valeurs non reconnues restent MANUAL par defaut, le choix
-- le plus prudent pour une donnee dont l'origine n'est plus certaine.
UPDATE tracking_events
SET data_source = 'LIVE'
WHERE source IN ('AviationStack', 'VesselAPI', 'Ship24', 'DHL', 'SHIPPO', 'WEBHOOK')
   OR source ILIKE 'webhook%';
