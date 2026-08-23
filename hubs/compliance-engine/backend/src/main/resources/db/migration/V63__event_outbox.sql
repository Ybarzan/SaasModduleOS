-- Bus d'evenements (pattern outbox), Phase 3 J1-J2 du plan de migration
-- (docs/03-plan-migration.md du monorepo SaasModduleOS). Remplace le premier
-- maillon des appels synchrones "best effort" d'EventPublisher par une file
-- durable : un evenement ecrit ici dans la MEME transaction que le
-- changement metier qui le declenche (ex: ShipmentService.updateStatus) est
-- garanti d'etre traite meme si le service de notification est indisponible
-- au moment ou l'evenement se produit -- un worker planifie le relit et
-- reessaie, au lieu de le perdre silencieusement (ancien comportement :
-- EventPublisher.shipmentStatusChanged catch(Exception) + log.warn, fin).
CREATE TABLE event_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    company_id UUID NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);

CREATE INDEX idx_event_outbox_status_created ON event_outbox (status, created_at);
