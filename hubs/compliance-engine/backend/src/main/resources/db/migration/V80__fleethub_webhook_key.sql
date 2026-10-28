-- Cle entrante (hashee, jamais rechiffrable) pour le webhook qui permet a
-- fleet-hub de notifier compliance-engine des evenements d'expedition
-- (creation / changement de statut). Remplace l'ancien chemin interne
-- (ShipmentService -> EventPublisher -> event_outbox), retire avec le
-- domaine Transport (V75) : voir EventOutboxProcessor.dispatch() et
-- FleetHubWebhookService. Meme principe que praxioIntegrationApiKey côté
-- logistix : generee ici, affichee une seule fois, jamais rechiffree.
ALTER TABLE companies
    ADD COLUMN fleethub_webhook_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN fleethub_webhook_key_hash VARCHAR(64);
