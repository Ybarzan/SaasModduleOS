-- V31: Fix stale V16 TARIC rates that override V23 corrections
-- V24 dedup kept earliest ID (V16), but V23 had corrected rates.
-- This migration updates the V16 rows to V23's corrected values.

-- Vehicles ch.87: V16 had 6.5% for CN, V23 corrected to 10.0%
UPDATE taric_rates SET duty_rate = 10.0
WHERE hs_code = '8703'
  AND origin_country = 'CN'
  AND destination_country = 'FR'
  AND is_prefential = FALSE
  AND duty_rate = 6.5;

-- Verify: no other MFN mismatches remain between V16 and V23
-- (All other overlapping codes have identical rates)
