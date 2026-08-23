-- V24: Deduplicate taric_rates (V16 vs V23 overlap) + add unique constraint
-- V16 and V23 both inserted overlapping TARIC rates without a unique constraint,
-- creating duplicates. V23 rows are preferred (they include trade_agreement_code).

-- 1. Remove V16 duplicates: keep the row with trade_agreement_code when both exist
DELETE FROM taric_rates t1
USING taric_rates t2
WHERE t1.hs_code = t2.hs_code
  AND t1.origin_country = t2.origin_country
  AND t1.destination_country = t2.destination_country
  AND t1.is_prefential = t2.is_prefential
  AND t1.id > t2.id
  AND t2.trade_agreement_code IS NOT NULL;

-- 2. Remove remaining exact duplicates (keep earliest id)
DELETE FROM taric_rates t1
USING taric_rates t2
WHERE t1.hs_code = t2.hs_code
  AND t1.origin_country = t2.origin_country
  AND t1.destination_country = t2.destination_country
  AND t1.is_prefential = t2.is_prefential
  AND t1.id > t2.id;

-- 3. Add unique constraint to prevent future duplicates
CREATE UNIQUE INDEX IF NOT EXISTS idx_taric_unique_rate
ON taric_rates(hs_code, origin_country, destination_country, is_prefential);

-- 4. Normalize TARIC descriptions: strip accents for search
ALTER TABLE taric_rates ADD COLUMN IF NOT EXISTS search_text VARCHAR(500);

-- Single-char via translate, multi-char via nested replace -- all using chr() for portability
UPDATE taric_rates SET search_text = replace(
  replace(
    replace(
      replace(
        translate(LOWER(description),
          chr(233)||chr(232)||chr(234)||chr(235)||chr(224)||chr(226)||chr(238)||chr(239)||chr(244)||chr(249)||chr(251)||chr(252)||chr(255)||chr(231)||chr(241)||chr(248)||chr(229)||chr(228)||chr(246)||chr(253)||chr(240),
          'eeeeeaaiioouuuycnaaooyd'),
        chr(339), 'oe'),
      chr(230), 'ae'),
    chr(223), 'ss'),
  chr(254), 'th');
