-- V23: TARIC enrichment + trade agreements supplement
-- Trade agreements supplementaires

INSERT INTO trade_agreements (code, name, partner_country, partner_name, description, agreement_type, hs_chapters_covered, origin_rules) VALUES
('EUSWIFT', 'Accord UE-Suisse', 'CH', 'Suisse', 'Zone industrielle libre-echange (1972). Droits supprimes pour produits industriels.', 'FTA', '25-97', 'Production entiere dans zone Suisse-UE'),
('EAFTA', 'ASEAN-EU Free Trade Agreement', 'ID', 'Indonesie', 'Negociations ASEAN-UE. En l''absence d''accord, droits MFN s''appliquent.', 'PTA', '01-97', NULL),
('EAGOAA', 'African Growth and Opportunity Act', 'NG', 'Nigeria', 'Accord USA-Afrique. Preferences unilaterales USA.', 'PTA', '01-97', NULL),
('EUEAC', 'UE-Afrique de l''Ouest (ECOWAS-EPA)', 'SN', 'Senegal', 'EPA Ouest-Afrique. Libre-echange pour 100pct des produits UE.', 'PTA', '01-97', 'Transit sans transformation admis'),
('AEUM', 'Accord UE-Ukraine', 'UA', 'Ukraine', 'Zone de libre-echange profonde et complete (DCFTA).', 'FTA', '25-97', 'WO + CTSH'),
('AEUMD', 'Accord UE-Moldavie', 'MD', 'Moldavie', 'Zone de libre-echange profonde (DCFTA). Progressif jusqu''en 2027.', 'FTA', '25-97', 'WO + CTSH'),
('AEUGE', 'Accord UE-Georgie', 'GE', 'Georgie', 'Zone de libre-echange profonde (DCFTA).', 'FTA', '25-97', 'WO + CTSH')
ON CONFLICT (code) DO NOTHING;

-- Textiles vetements ch.61-63 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('6101', 'Vetements tricotes manteaux', 'CN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6101', 'Vetements tricotes manteaux', 'BD', 'FR', 9.6, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6101', 'Vetements tricotes manteaux', 'IN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6101', 'Vetements tricotes manteaux', 'VN', 'FR', 8.0, 'AD', TRUE, 'EVFTA', 'CTSH', '2026-01-01'),
('6101', 'Vetements tricotes manteaux', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('6109', 'T-shirts maillots de corps', 'CN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6109', 'T-shirts maillots de corps', 'BD', 'FR', 9.6, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6109', 'T-shirts maillots de corps', 'VN', 'FR', 8.0, 'AD', TRUE, 'EVFTA', 'CTSH', '2026-01-01'),
('6110', 'Pulls gilets chandails tricotes', 'CN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6110', 'Pulls gilets chandails tricotes', 'VN', 'FR', 8.0, 'AD', TRUE, 'EVFTA', 'CTSH', '2026-01-01'),
('6115', 'Bas chaussettes tricotes', 'CN', 'FR', 9.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6115', 'Bas chaussettes tricotes', 'VN', 'FR', 6.0, 'AD', TRUE, 'EVFTA', 'CTSH', '2026-01-01'),
('6201', 'Vetements non tricotes manteaux hommes', 'CN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6201', 'Vetements non tricotes manteaux hommes', 'VN', 'FR', 8.0, 'AD', TRUE, 'EVFTA', 'CTSH', '2026-01-01'),
('6201', 'Vetements non tricotes manteaux hommes', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('6201', 'Vetements non tricotes manteaux hommes', 'BD', 'FR', 9.6, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6203', 'Vetements non tricotes costumes hommes', 'CN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6203', 'Vetements non tricotes costumes hommes', 'VN', 'FR', 8.0, 'AD', TRUE, 'EVFTA', 'CTSH', '2026-01-01'),
('6205', 'Chemises non tricotes hommes', 'CN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6205', 'Chemises non tricotes hommes', 'VN', 'FR', 8.0, 'AD', TRUE, 'EVFTA', 'CTSH', '2026-01-01'),
('6302', 'Linge de lit table bains', 'CN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6302', 'Linge de lit table bains', 'IN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6305', 'Sacs en tissu pour emballage', 'CN', 'FR', 8.0, 'AD', FALSE, NULL, NULL, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Chaussures ch.64 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('6402', 'Chaussures semelles caoutchouc plastique exterieur', 'CN', 'FR', 16.9, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6402', 'Chaussures semelles caoutchouc plastique exterieur', 'VN', 'FR', 12.0, 'AD', TRUE, 'EVFTA', 'CTH', '2026-01-01'),
('6402', 'Chaussures semelles caoutchouc plastique exterieur', 'ID', 'FR', 16.9, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6403', 'Chaussures semelles caoutchouc cuir', 'CN', 'FR', 16.9, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6403', 'Chaussures semelles caoutchouc cuir', 'VN', 'FR', 12.0, 'AD', TRUE, 'EVFTA', 'CTH', '2026-01-01'),
('6403', 'Chaussures semelles caoutchouc cuir', 'IN', 'FR', 16.9, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6403', 'Chaussures semelles caoutchouc cuir', 'ID', 'FR', 16.9, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6404', 'Chaussures semelles caoutchouc textile', 'CN', 'FR', 16.9, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('6404', 'Chaussures semelles caoutchouc textile', 'VN', 'FR', 12.0, 'AD', TRUE, 'EVFTA', 'CTH', '2026-01-01'),
('6405', 'Autres chaussures', 'CN', 'FR', 16.9, 'AD', FALSE, NULL, NULL, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Electronique ch.84-85 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('8471', 'Machines traitement de donnees ordinateurs', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8471', 'Machines traitement de donnees ordinateurs', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'WO', '2026-01-01'),
('8471', 'Machines traitement de donnees ordinateurs', 'TW', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8517', 'Appareils telephonie smartphones', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8517', 'Appareils telephonie smartphones', 'KR', 'FR', 0.0, 'AD', TRUE, 'EUKORF', 'WO', '2026-01-01'),
('8517', 'Appareils telephonie smartphones', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'WO', '2026-01-01'),
('8528', 'Ecrans moniteurs projecteurs', 'CN', 'FR', 14.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8528', 'Ecrans moniteurs projecteurs', 'KR', 'FR', 0.0, 'AD', TRUE, 'EUKORF', 'WO', '2026-01-01'),
('8528', 'Ecrans moniteurs projecteurs', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'WO', '2026-01-01'),
('8541', 'Semi-conducteurs diodes transistors', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8541', 'Semi-conducteurs diodes transistors', 'TW', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8541', 'Semi-conducteurs diodes transistors', 'KR', 'FR', 0.0, 'AD', TRUE, 'EUKORF', 'WO', '2026-01-01'),
('8542', 'Circuits integres electroniques', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8542', 'Circuits integres electroniques', 'TW', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8544', 'Cables fils isoles', 'CN', 'FR', 3.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8544', 'Cables fils isoles', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'CTH', '2026-01-01'),
('8504', 'Transformateurs convertisseurs statiques', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8504', 'Transformateurs convertisseurs statiques', 'KR', 'FR', 0.0, 'AD', TRUE, 'EUKORF', 'WO', '2026-01-01'),
('8507', 'Batteries accumulateurs', 'CN', 'FR', 4.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8507', 'Batteries accumulateurs', 'KR', 'FR', 0.0, 'AD', TRUE, 'EUKORF', 'WO', '2026-01-01'),
('8507', 'Batteries accumulateurs', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'CTH', '2026-01-01'),
('8509', 'Machines electromenageres', 'CN', 'FR', 2.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8509', 'Machines electromenageres', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('8516', 'Appareils de chauffage seche-cheveux', 'CN', 'FR', 2.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8516', 'Appareils de chauffage seche-cheveux', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('8525', 'Cameras camcopes', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8525', 'Cameras camcopes', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8536', 'Appareillage electrique interrupteurs', 'CN', 'FR', 3.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8536', 'Appareillage electrique interrupteurs', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('8539', 'Lampes tubes decharge', 'CN', 'FR', 4.7, 'AD', FALSE, NULL, NULL, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Machines industrielles ch.84 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('8414', 'Machines a souffler pompes a vide', 'CN', 'FR', 1.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8414', 'Machines a souffler pompes a vide', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8418', 'Refrigerateurs congelateurs', 'CN', 'FR', 2.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8418', 'Refrigerateurs congelateurs', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('8422', 'Machines a laver vaisselle lave-linge', 'CN', 'FR', 2.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8422', 'Machines a laver vaisselle lave-linge', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('8428', 'Machines de manutention', 'CN', 'FR', 2.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8428', 'Machines de manutention', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8429', 'Bulldozers niveleuses scrapers', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8429', 'Bulldozers niveleuses scrapers', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8433', 'Machines agricoles moissons battages', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8433', 'Machines agricoles moissons battages', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8443', 'Imprimantes copieurs', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8443', 'Imprimantes copieurs', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8462', 'Machines-outils presses cisailles', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8462', 'Machines-outils presses cisailles', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8467', 'Outils portatifs a moteur', 'CN', 'FR', 2.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8467', 'Outils portatifs a moteur', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8473', 'Pieces detachees machines traitement', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8473', 'Pieces detachees machines traitement', 'TW', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8479', 'Machines a fonctions diverses', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8479', 'Machines a fonctions diverses', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01')
ON CONFLICT DO NOTHING;

-- Vehicules ch.87 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('8703', 'Voitures de tourisme', 'CN', 'FR', 10.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8703', 'Voitures de tourisme', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8703', 'Voitures de tourisme', 'KR', 'FR', 0.0, 'AD', TRUE, 'EUKORF', 'WO', '2026-01-01'),
('8703', 'Voitures de tourisme', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('8704', 'Vehicules de transport de marchandises', 'CN', 'FR', 14.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8704', 'Vehicules de transport de marchandises', 'IN', 'FR', 14.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8708', 'Pieces detachees pour vehicules', 'CN', 'FR', 4.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8708', 'Pieces detachees pour vehicules', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('8708', 'Pieces detachees pour vehicules', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8711', 'Motos et cycles', 'CN', 'FR', 6.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('8711', 'Motos et cycles', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('8711', 'Motos et cycles', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'WO', '2026-01-01'),
('8712', 'Bicyclettes et cycles', 'CN', 'FR', 14.0, 'AD', FALSE, NULL, NULL, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Produits agricoles ch.02-24 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('0201', 'Viande de bovin fraiche refroidie', 'BR', 'FR', 12.8, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0201', 'Viande de bovin fraiche refroidie', 'AR', 'FR', 6.4, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0201', 'Viande de bovin fraiche refroidie', 'AU', 'FR', 6.4, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0202', 'Viande de bovin congelee', 'BR', 'FR', 12.8, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0202', 'Viande de bovin congelee', 'AR', 'FR', 6.4, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0207', 'Viande de volaille', 'BR', 'FR', 102.0, 'SD', FALSE, NULL, NULL, '2026-01-01'),
('0207', 'Viande de volaille', 'TH', 'FR', 102.0, 'SD', FALSE, NULL, NULL, '2026-01-01'),
('0301', 'Poissons vivants', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0302', 'Poissons frais refroidis', 'NO', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0303', 'Poissons congeles', 'NO', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0306', 'Crustaces', 'IN', 'FR', 12.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0402', 'Lait concentre poudre de lait', 'NZ', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0406', 'Fromages', 'CH', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0713', 'Legumes seches lentilles pois chiches', 'CA', 'FR', 0.0, 'AD', TRUE, 'CETA', 'WO', '2026-01-01'),
('0713', 'Legumes seches lentilles pois chiches', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('0901', 'Cafe', 'BR', 'FR', 7.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0901', 'Cafe', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'WO', '2026-01-01'),
('0902', 'The', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('0902', 'The', 'IN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('1001', 'Ble et meteil', 'UA', 'FR', 95.0, 'SD', FALSE, NULL, NULL, '2026-01-01'),
('1001', 'Ble et meteil', 'CA', 'FR', 0.0, 'AD', TRUE, 'CETA', 'WO', '2026-01-01'),
('1005', 'Mais', 'BR', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('1201', 'Soja', 'BR', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('1201', 'Soja', 'US', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('1507', 'Huile de soja', 'BR', 'FR', 3.2, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('1701', 'Sucre', 'BR', 'FR', 339.0, 'SD', FALSE, NULL, NULL, '2026-01-01'),
('1801', 'Cacao en feves', 'CI', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('2101', 'Extraits cafe the matieres alimentaires', 'BR', 'FR', 9.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('2203', 'Biere', 'MX', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('2204', 'Vins de raisin frais', 'AR', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Chimie pharma ch.28-38 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('2941', 'Antibiotiques', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('2941', 'Antibiotiques', 'IN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3004', 'Medicaments en doses', 'CH', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3004', 'Medicaments en doses', 'IN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3004', 'Medicaments en doses', 'US', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3204', 'Matieres colorantes', 'CN', 'FR', 6.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3304', 'Produits de beaute maquillage', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3304', 'Produits de beaute maquillage', 'KR', 'FR', 0.0, 'AD', TRUE, 'EUKORF', 'WO', '2026-01-01'),
('3305', 'Shampoings produits capillaires', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3401', 'Savons', 'CN', 'FR', 2.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3808', 'Insecticides herbicides', 'CN', 'FR', 3.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3808', 'Insecticides herbicides', 'IN', 'FR', 3.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3926', 'Articles en matieres plastiques', 'CN', 'FR', 6.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('3926', 'Articles en matieres plastiques', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('3926', 'Articles en matieres plastiques', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'WO', '2026-01-01')
ON CONFLICT DO NOTHING;

-- Metaux ch.72-73 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('7208', 'Toles en acier lamine a chaud', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7208', 'Toles en acier lamine a chaud', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('7210', 'Toles en acier lamine a froid', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7210', 'Toles en acier lamine a froid', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('7219', 'Acier inoxydable lamine', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7219', 'Acier inoxydable lamine', 'KR', 'FR', 0.0, 'AD', TRUE, 'EUKORF', 'WO', '2026-01-01'),
('7304', 'Tubes en acier sans joint', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7306', 'Tubes et tuyaux en fer ou acier', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7318', 'Vis boulons ecrous en acier', 'CN', 'FR', 3.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7318', 'Vis boulons ecrous en acier', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('7321', 'Appareils de chauffage en fonte acier', 'CN', 'FR', 2.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7604', 'Barres profils en aluminium', 'CN', 'FR', 7.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7604', 'Barres profils en aluminium', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('7606', 'Plaques feuilles en aluminium', 'CN', 'FR', 7.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7607', 'Feuilles d''aluminium', 'CN', 'FR', 6.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7610', 'Ouvrages en aluminium', 'CN', 'FR', 7.5, 'AD', FALSE, NULL, NULL, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Meubles ch.94 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('9401', 'Sieges et parties de sieges', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9401', 'Sieges et parties de sieges', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'WO', '2026-01-01'),
('9401', 'Sieges et parties de sieges', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('9403', 'Autres meubles', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9403', 'Autres meubles', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'WO', '2026-01-01'),
('9403', 'Autres meubles', 'TR', 'FR', 0.0, 'AD', TRUE, 'EUCU', 'WO', '2026-01-01'),
('9404', 'Matelas sommiers', 'CN', 'FR', 3.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9404', 'Matelas sommiers', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'CTH', '2026-01-01'),
('9405', 'Luminaires', 'CN', 'FR', 4.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9405', 'Luminaires', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'CTH', '2026-01-01')
ON CONFLICT DO NOTHING;

-- Jouets sports ch.95 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('9503', 'Jouets poupees velos miniatures puzzles', 'CN', 'FR', 4.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9503', 'Jouets poupees velos miniatures puzzles', 'VN', 'FR', 0.0, 'AD', TRUE, 'EVFTA', 'CTH', '2026-01-01'),
('9504', 'Jeux de societe consoles de jeux', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9504', 'Jeux de societe consoles de jeux', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('9506', 'Equipements sportifs', 'CN', 'FR', 2.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9506', 'Equipements sportifs', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01'),
('9507', 'Cannes a peche filets', 'CN', 'FR', 4.7, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9507', 'Cannes a peche filets', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01')
ON CONFLICT DO NOTHING;

-- Bijoux horlogerie ch.71 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('7101', 'Perles naturelles ou cultivees', 'CN', 'FR', 3.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7113', 'Bijoux en argent platine', 'CN', 'FR', 4.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7113', 'Bijoux en argent platine', 'IN', 'FR', 2.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('7117', 'Bijoux de fantaisie', 'CN', 'FR', 4.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9101', 'Montres a cadran', 'CN', 'FR', 4.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9101', 'Montres a cadran', 'CH', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9102', 'Montres-bracelets', 'CN', 'FR', 4.5, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('9102', 'Montres-bracelets', 'JP', 'FR', 0.0, 'AD', TRUE, 'EUEJPA', 'WO', '2026-01-01')
ON CONFLICT DO NOTHING;

-- Papier carton ch.48 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('4802', 'Papier pour impression', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('4811', 'Papier enduit calandre', 'CN', 'FR', 5.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('4818', 'Papier hygienique', 'CN', 'FR', 5.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('4819', 'Cartonnage emballages', 'CN', 'FR', 5.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('4820', 'Registres cahiers', 'CN', 'FR', 5.0, 'AD', FALSE, NULL, NULL, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Bois ouvrages ch.44 MFN

INSERT INTO taric_rates (hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_prefential, trade_agreement_code, prefential_origin_criteria, valid_from) VALUES
('4407', 'Bois scie longitudinalement', 'CN', 'FR', 0.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('4407', 'Bois scie longitudinalement', 'CA', 'FR', 0.0, 'AD', TRUE, 'CETA', 'WO', '2026-01-01'),
('4411', 'Fibres de bois', 'CN', 'FR', 2.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('4418', 'Menuiserie charpente en bois', 'CN', 'FR', 2.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('4421', 'Autres ouvrages en bois', 'CN', 'FR', 2.0, 'AD', FALSE, NULL, NULL, '2026-01-01'),
('4421', 'Autres ouvrages en bois', 'CA', 'FR', 0.0, 'AD', TRUE, 'CETA', 'WO', '2026-01-01')
ON CONFLICT DO NOTHING;
