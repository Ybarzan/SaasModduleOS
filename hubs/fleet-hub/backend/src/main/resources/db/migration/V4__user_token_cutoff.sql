-- Révocation globale de tous les tokens d'un utilisateur (admin).
-- Si revoked_before est défini, tout token émis avant cette date est rejeté,
-- même s'il n'est pas présent dans revoked_token.

CREATE TABLE user_token_cutoff (
    user_id         BIGINT NOT NULL PRIMARY KEY,
    revoked_before  TIMESTAMP(6) NOT NULL
);
