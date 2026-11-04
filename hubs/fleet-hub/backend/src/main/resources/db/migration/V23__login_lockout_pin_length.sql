-- Verrouillage temporaire après échecs de connexion répétés (tous les comptes,
-- dont les comptes CHAUFFEUR du portail de pointage, protégés par un simple PIN).
ALTER TABLE app_user ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until TIMESTAMP(6);

-- Longueur du PIN d'un compte CHAUFFEUR : les nouveaux codes font 6 chiffres,
-- les codes à 4 chiffres existants restent valides jusqu'à leur réinitialisation
-- (NULL = code antérieur à cette colonne, donc 4 chiffres). Le clavier du portail
-- valide automatiquement dès que cette longueur est atteinte.
ALTER TABLE app_user ADD COLUMN pin_length INTEGER;

-- Les liens de suivi publics créés sans date d'expiration restaient valides
-- indéfiniment : on leur laisse 30 jours à partir de cette migration.
UPDATE shared_links SET expires_at = CURRENT_TIMESTAMP + INTERVAL '30 days'
WHERE expires_at IS NULL;
