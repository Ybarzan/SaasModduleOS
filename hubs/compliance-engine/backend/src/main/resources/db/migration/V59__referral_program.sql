-- Programme de parrainage (item marketing #11) : chaque société a un code de
-- parrainage unique généré à la création. Si une nouvelle société s'inscrit avec
-- le code d'une société existante, le lien est enregistré ; referral_reward_granted
-- évite un double crédit si le webhook Stripe de fin de checkout se déclenche plus
-- d'une fois pour le même abonnement (Stripe ne garantit pas une livraison unique).
ALTER TABLE companies ADD COLUMN referral_code VARCHAR(12);
ALTER TABLE companies ADD COLUMN referred_by_company_id UUID;
ALTER TABLE companies ADD COLUMN referral_reward_granted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE companies
    ADD CONSTRAINT fk_companies_referred_by
    FOREIGN KEY (referred_by_company_id) REFERENCES companies(id) ON DELETE SET NULL;

-- Index unique partiel : autorise plusieurs sociétés existantes avec referral_code
-- NULL (backfill paresseux à la première consultation, pas de migration de données
-- en masse) tout en garantissant l'unicité une fois un code réellement attribué.
CREATE UNIQUE INDEX idx_companies_referral_code ON companies(referral_code) WHERE referral_code IS NOT NULL;

CREATE INDEX idx_companies_referred_by ON companies(referred_by_company_id);
