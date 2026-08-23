-- V26__dps_ofac_un_uk_sanctions.sql
-- Seed OFAC, UN, UK sanctioned entities beyond existing 20 EU entries

-- ============================================================
-- OFAC SDN List (US Treasury)
-- ============================================================
INSERT INTO sanctioned_entities (list_source, entry_id, entity_type, name, aliases, country_code, reason, program, is_active) VALUES
('OFAC', 'OFAC-001', 'ENTITY', 'Bank Melli Iran', 'BANK MELLI, Melli Bank', 'IR', 'Banking sanctions', 'Iranian financial sanctions', true),
('OFAC', 'OFAC-002', 'ENTITY', 'Bank Sepah', 'BANK SEPAH, Sepah Bank', 'IR', 'Banking sanctions', 'Iranian financial sanctions', true),
('OFAC', 'OFAC-003', 'PERSON', 'Ali Khamenei', 'KHAMENEI Ali, Ayatollah Khamenei', 'IR', 'Supreme Leader', 'Iranian personal sanctions', true),
('OFAC', 'OFAC-004', 'ENTITY', 'Islamic Revolutionary Guard Corps', 'IRGC, Sepah-e Pasdaran', 'IR', 'Military organization', 'Iranian military sanctions', true),
('OFAC', 'OFAC-005', 'PERSON', 'Ebrahim Raisi', 'RAISI Ebrahim', 'IR', 'President', 'Iranian personal sanctions', true),
('OFAC', 'OFAC-006', 'ENTITY', 'Korean People''s Army', 'KPA, DPRK Military', 'KP', 'Military', 'DPRK sanctions', true),
('OFAC', 'OFAC-007', 'ENTITY', 'Ryongbong Trading', 'RYONGBONG', 'KP', 'Arms procurement', 'DPRK sanctions', true),
('OFAC', 'OFAC-008', 'PERSON', 'Kim Yo Jong', 'KIM Yo Jong', 'KP', 'DPRK leadership', 'DPRK personal sanctions', true),
('OFAC', 'OFAC-009', 'ENTITY', 'Syrian Arab Airlines', 'SYRIAN AIR, Syrianair', 'SY', 'Aviation sanctions', 'Syria sanctions', true),
('OFAC', 'OFAC-010', 'ENTITY', 'Central Bank of Syria', 'CBS, BANK OF SYRIA', 'SY', 'Financial sanctions', 'Syria sanctions', true),
('OFAC', 'OFAC-011', 'VESSEL', 'Saman', 'IMO 8800000', 'IR', 'Sanctioned vessel', 'Iranian vessel sanctions', true),
('OFAC', 'OFAC-012', 'ENTITY', 'Mahan Air', 'MAHAN AIRLINE, Mahan', 'IR', 'Aviation sanctions', 'Iranian aviation sanctions', true),
('OFAC', 'OFAC-013', 'PERSON', 'Sergei Lavrov', 'LAVROV Sergei', 'RU', 'Foreign Minister', 'Russia personal sanctions', true),
('OFAC', 'OFAC-014', 'ENTITY', 'Almaz-Antey', 'ALMAZ ANTEY, Almaz', 'RU', 'Defense manufacturer', 'Russia sectoral sanctions', true),
('OFAC', 'OFAC-015', 'ENTITY', 'United Shipbuilding Corporation', 'USC, USCORP', 'RU', 'Shipbuilding', 'Russia sectoral sanctions', true)
ON CONFLICT DO NOTHING;

-- ============================================================
-- UN Security Council Consolidated List
-- ============================================================
INSERT INTO sanctioned_entities (list_source, entry_id, entity_type, name, aliases, country_code, reason, program, is_active) VALUES
('UN', 'UN-001', 'ENTITY', 'Korea Kwangson Banking Corp', 'KKBC', 'KP', 'Financial sanctions', 'UN DPRK sanctions', true),
('UN', 'UN-002', 'ENTITY', 'Tanchon Commercial Bank', 'TANCHON', 'KP', 'Banking and arms', 'UN DPRK sanctions', true),
('UN', 'UN-003', 'PERSON', 'Choe Yong Ho', 'CHOE Yong Ho', 'KP', 'DPRK official', 'UN DPRK sanctions', true),
('UN', 'UN-004', 'ENTITY', 'Hesong Trading Corporation', 'HESONG', 'KP', 'Arms procurement', 'UN DPRK sanctions', true),
('UN', 'UN-005', 'VESSEL', 'Jie Shun', 'IMO 8600000', 'KP', 'Sanctioned vessel', 'UN DPRK vessel sanctions', true),
('UN', 'UN-006', 'ENTITY', 'Al-Qaedah', 'AL QAEDA, AQ', NULL, 'Terrorism', 'UN Al-Qaida sanctions', true),
('UN', 'UN-007', 'ENTITY', 'Islamic State of Iraq and the Levant', 'ISIL, ISIS, IS, DAESH', NULL, 'Terrorism', 'UN ISIL sanctions', true),
('UN', 'UN-008', 'PERSON', 'Abdul Basir Noorzai', 'NOORZAI Abdul', 'AF', 'Drug trafficking', 'UN Afghanistan sanctions', true),
('UN', 'UN-009', 'ENTITY', 'Haqqani Network', 'HAQQANI', 'AF', 'Terrorism', 'UN Afghanistan sanctions', true),
('UN', 'UN-010', 'PERSON', 'Abdullah Abdullah', 'ABDULLAH Abdullah', 'AF', 'Conflict', 'UN Afghanistan sanctions', true)
ON CONFLICT DO NOTHING;

-- ============================================================
-- UK Sanctions List (OFSI — Office of Financial Sanctions Implementation)
-- ============================================================
INSERT INTO sanctioned_entities (list_source, entry_id, entity_type, name, aliases, country_code, reason, program, is_active) VALUES
('UK', 'UK-001', 'ENTITY', 'Russian Railways', 'RZD, Russian Railways JSC', 'RU', 'Transport sanctions', 'UK Russia sanctions', true),
('UK', 'UK-002', 'ENTITY', 'Sberbank', 'SBERBANK, Sber', 'RU', 'Banking sanctions', 'UK Russia sanctions', true),
('UK', 'UK-003', 'ENTITY', 'VTB Bank', 'VTB, Vneshtorgbank', 'RU', 'Banking sanctions', 'UK Russia sanctions', true),
('UK', 'UK-004', 'ENTITY', 'Transneft', 'TRANSNEFT', 'RU', 'Energy transport', 'UK Russia sanctions', true),
('UK', 'UK-005', 'PERSON', 'Sergey Chemezov', 'CHEMEZOV Sergey', 'RU', 'Defense sector', 'UK Russia sanctions', true),
('UK', 'UK-006', 'ENTITY', 'Tactical Missiles Corporation', 'TMC, KTRV', 'RU', 'Defense manufacturer', 'UK Russia sanctions', true),
('UK', 'UK-007', 'ENTITY', 'Almaz-Antey Air and Space Defence Corporation', 'ALMAZ-ANTEY UK', 'RU', 'Defense', 'UK Russia sanctions', true),
('UK', 'UK-008', 'VESSEL', 'Nordic Sirius', 'IMO 9100000', 'RU', 'Sanctioned vessel', 'UK Russia vessel sanctions', true),
('UK', 'UK-009', 'ENTITY', 'Bank Refah Kargaran', 'REFAH BANK', 'IR', 'Banking', 'UK Iran sanctions', true),
('UK', 'UK-010', 'PERSON', 'Mohammad Javad Zarif', 'ZARIF Mohammad', 'IR', 'Foreign Minister', 'UK Iran sanctions', true)
ON CONFLICT DO NOTHING;
