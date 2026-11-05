-- Identifiant d'événement fourni par le producteur (fleet-hub : eventId de son
-- outbox). fleet-hub livre « au moins une fois » : un renvoi après une réponse
-- perdue est normal. L'index unique garantit qu'un même événement n'est traité
-- qu'une fois par le moteur de règles (pas de double ajustement ERP, pas de
-- double « expédié » e-commerce). NULL pour les événements produits sans
-- identifiant (ancien fleet-hub, événements internes) : non dédupliqués.
ALTER TABLE event_outbox ADD COLUMN source_event_id UUID;
CREATE UNIQUE INDEX uk_event_outbox_source_event ON event_outbox (company_id, source_event_id);
