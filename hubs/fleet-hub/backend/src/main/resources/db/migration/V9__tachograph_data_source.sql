-- Traçabilité de provenance des données tachygraphe : une donnée saisie
-- manuellement, importée depuis un CSV, ou décodée depuis un fichier DDD
-- binaire (tachygraphe européen) n'a pas le même niveau de confiance. En
-- particulier, le décodeur DDD n'a jamais été validé contre un fichier réel
-- (aucun accès test disponible) — les jours importés par ce canal doivent
-- rester visiblement "à vérifier" tant qu'une validation réelle n'a pas eu
-- lieu, plutôt que de se présenter comme une donnée de conformité fiable.
ALTER TABLE tachograph_day ADD COLUMN data_source VARCHAR(30);
