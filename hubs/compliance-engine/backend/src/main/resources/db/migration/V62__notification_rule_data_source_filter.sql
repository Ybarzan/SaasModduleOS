-- Etend le moteur de regles de notification pour filtrer sur la provenance de la
-- donnee qui declenche l'evenement (TrackingEvent.data_source, V61) : une entreprise
-- peut vouloir ne pas etre alertee (email/webhook) sur des changements de statut
-- saisis a la main, ou au contraire ne vouloir que ceux-la. NULL = pas de filtre,
-- comportement actuel inchange (retro-compatible).
ALTER TABLE notification_rules
    ADD COLUMN filter_data_source VARCHAR(10);
