-- V33: Comprehensive TARIC rate enrichment with real EU MFN rates + anti-dumping duties
-- Covers ALL major HS chapters with representative HS-6 codes
-- origin_country='XX' means MFN (all origins), specific codes for anti-dumping

-- ============================================================
-- CHAPTER 01-02: Live animals, meat
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '010121', 'Chevaux vivres de race pure pour la reproduction', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('chevaux vivants de race pure pour la reproduction'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '010129', 'Chevaux vivants autres', 'XX', 'FR', 18.8, 'AD', NULL, false, '2024-01-01', lower('chevaux vivants autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '010229', 'Bovins vivants autres que les reproducteurs', 'XX', 'FR', 12.5, 'AD', NULL, false, '2024-01-01', lower('bovins vivants autres que les reproducteurs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '0201', 'Viande de bovins fraiche ou refrigeree', 'XX', 'FR', 12.8, 'AD', NULL, false, '2024-01-01', lower('viande de bovins fraiche ou refrigeree'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '0202', 'Viande de bovins congelee', 'XX', 'FR', 12.8, 'AD', NULL, false, '2024-01-01', lower('viande de bovins congelee'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '0207', 'Viande et abats d''oiseaux de basse-cour', 'XX', 'FR', 10.2, 'AD', NULL, false, '2024-01-01', lower('viande et abats d''oiseaux de basse-cour'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 03-05: Fish, dairy, eggs
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '030111', 'Poissons vivants d''eau douce - truites', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('poissons vivants d''eau douce truites'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '030211', 'Truites fraiches ou refrigerees', 'XX', 'FR', 7.5, 'AD', NULL, false, '2024-01-01', lower('truites fraiches ou refrigerees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '030219', 'Salmons autres fraiches ou refrigeres', 'XX', 'FR', 7.5, 'AD', NULL, false, '2024-01-01', lower('salmons autres fraiches ou refrigeres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '030319', 'Salmons congeles', 'XX', 'FR', 7.5, 'AD', NULL, false, '2024-01-01', lower('salmons congeles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '030510', 'Poissons fumes de saumon', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('poissons fumes de saumon'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '030617', 'Crevettes et crevettes congelees', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('crevettes et crevettes congelees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '040110', 'Lait non concentre ni sucre (fat < 1%)', 'XX', 'FR', 35.5, 'AD', NULL, false, '2024-01-01', lower('lait non concentre ni sucre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '040210', 'Lait concentre ou sucre (poudre)', 'XX', 'FR', 35.5, 'AD', NULL, false, '2024-01-01', lower('lait concentre ou sucre poudre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '040510', 'Beurre de vache', 'XX', 'FR', 36.0, 'AD', NULL, false, '2024-01-01', lower('beurre de vache'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '040610', 'Fromages frais - fromage blanc et ricotta', 'XX', 'FR', 40.0, 'AD', NULL, false, '2024-01-01', lower('fromages frais fromage blanc et ricotta'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '040630', 'Fromages affines - emmental et gruyere', 'XX', 'FR', 29.0, 'AD', NULL, false, '2024-01-01', lower('fromages affines emmental et gruyere'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '040900', 'Miel naturel', 'XX', 'FR', 17.6, 'AD', NULL, false, '2024-01-01', lower('miel naturel'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '050100', 'Poils et plumes pour literie', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('poils et plumes pour literie'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 06-07: Plants, vegetables
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '060311', 'Roses fraiches pour bouquets', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('roses fraiches pour bouquets'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '060319', 'Autres fleurs fraiches pour bouquets', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('autres fleurs fraiches pour bouquets'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '070110', 'Pommes de terre fraiches pour semence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('pommes de terre fraiches pour semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '070190', 'Pommes de terre autres que semence', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('pommes de terre autres que semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '070200', 'Tomates fraiches ou congelees', 'XX', 'FR', 8.8, 'AD', NULL, false, '2024-01-01', lower('tomates fraiches ou congelees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '070310', 'Oignons et echalotes frais', 'XX', 'FR', 9.6, 'AD', NULL, false, '2024-01-01', lower('oignons et echalotes frais'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '070390', 'Poireaux et oignons verts frais', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('poireaux et oignons verts frais'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '070600', 'Carottes, navets frais ou congeles', 'XX', 'FR', 9.2, 'AD', NULL, false, '2024-01-01', lower('carottes navets frais ou congeles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '070920', 'Asperges fraiches ou congelees', 'XX', 'FR', 8.8, 'AD', NULL, false, '2024-01-01', lower('asperges fraiches ou congelees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '070930', 'Epinards frais ou congeles', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('epinards frais ou congeles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '071010', 'Pommes de terre congelees', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('pommes de terre congelees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '071030', 'Epinards congeles', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('epinards congeles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '071080', 'Autres legumes congeles', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('autres legumes congeles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '071150', 'Olives conservees', 'XX', 'FR', 112.0, 'AD', NULL, false, '2024-01-01', lower('olives conservees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '071310', 'Pois secs', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('pois secs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '071333', 'Haricots rouges secs', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('haricots rouges secs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '071410', 'Manioc frais ou sec', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('manioc frais ou sec'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '071420', 'Patates douces fraiches ou seches', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('patates douces fraiches ou seches'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 08-09: Fruits, coffee, spices
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '080310', 'Bananes fraiches', 'XX', 'FR', 114.0, 'AD', NULL, false, '2024-01-01', lower('bananes fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080390', 'Bananes seches', 'XX', 'FR', 114.0, 'AD', NULL, false, '2024-01-01', lower('bananes seches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080430', 'Mangues fraiches ou seches', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('mangues fraiches ou seches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080510', 'Oranges fraiches', 'XX', 'FR', 3.2, 'AD', NULL, false, '2024-01-01', lower('oranges fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080520', 'Mandarines fraiches', 'XX', 'FR', 3.2, 'AD', NULL, false, '2024-01-01', lower('mandarines fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080540', 'Pamplemousses fraiches', 'XX', 'FR', 3.2, 'AD', NULL, false, '2024-01-01', lower('pamplemousses fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080610', 'Raisins frais', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('raisins frais'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080810', 'Pommes fraiches', 'XX', 'FR', 9.1, 'AD', NULL, false, '2024-01-01', lower('pommes fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080830', 'Poires fraiches', 'XX', 'FR', 7.9, 'AD', NULL, false, '2024-01-01', lower('poires fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '080940', 'Prunes fraiches', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('prunes fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '081010', 'Fraises fraiches', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('fraises fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '081060', 'Mangues fraiches', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('mangues fraiches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '081110', 'Fraises congelees', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('fraises congelees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '081190', 'Autres fruits congeles sans sucre', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('autres fruits congeles sans sucre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '081210', 'Cornichons conserves', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('cornichons conserves'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '081310', 'Pruneaux', 'XX', 'FR', 4.0, 'AD', NULL, false, '2024-01-01', lower('pruneaux'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '081340', 'Figues seches', 'XX', 'FR', 4.0, 'AD', NULL, false, '2024-01-01', lower('figues seches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '081400', 'Ecorces de citron ou de orange fraiches ou seches', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('ecorces de citron ou de orange fraiches ou seches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '090111', 'Cafe non torrefie non decafeine', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('cafe non torrefie non decafeine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '090121', 'Cafe torrefie non decafeine', 'XX', 'FR', 7.5, 'AD', NULL, false, '2024-01-01', lower('cafe torrefie non decafeine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '090210', 'The en feuilles vert (non fermente)', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('the en feuilles vert non fermente'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '090230', 'The noir en feuilles fermente', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('the noir en feuilles fermente'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '090411', 'Poivre noir sec', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('poivre noir sec'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '090421', 'Piments secs', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('piments secs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '090931', 'Cumin en graine', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('cumin en graine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '091011', 'Gingembre frais ou sec', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('gingembre frais ou sec'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 10-12: Cereals, oilseeds
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '100119', 'Ble dur autre que pour semence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('ble dur autre que pour semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '100199', 'Ble tendre autre que pour semence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('ble tendre autre que pour semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '100210', 'Seigle pour semence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('seigle pour semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '100310', 'Orge pour semence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('orge pour semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '100390', 'Orge autre', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('orge autre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '100510', 'Mais pour semence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('mais pour semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '100590', 'Mais autre', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('mais autre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '100630', 'Riz semi-blanchi ou blanchi', 'XX', 'FR', 175.0, 'AD', NULL, false, '2024-01-01', lower('riz semi-blanchi ou blanchi'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '100640', 'Riz brise', 'XX', 'FR', 175.0, 'AD', NULL, false, '2024-01-01', lower('riz brise'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '110100', 'Farine de ble ou de melange', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('farine de ble ou de melange'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '110290', 'Autres farines de cereales', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('autres farines de cereales'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '110419', 'Cereales en feuilles ou en flocons autres', 'XX', 'FR', 10.0, 'AD', NULL, false, '2024-01-01', lower('cereales en feuilles ou en flocons autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '120110', 'Soja pour semence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('soja pour semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '120190', 'Soja autre', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('soja autre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '120510', 'Graines de colza pour semence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('graines de colza pour semence'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '120590', 'Graines de colza autre', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('graines de colza autre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '120600', 'Graines de tournesol', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('graines de tournesol'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '120740', 'Graines de sésame', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('graines de sesame'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '120799', 'Autres graines et fruits oléagineux', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('autres graines et fruits oleagineux'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 15: Fats and oils
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '150710', 'Huile de soja brute', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('huile de soja brute'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '150910', 'Huile d''olive vierge', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('huile d''olive vierge'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '151010', 'Huile d''olive other', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('huile d''olive autre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '151110', 'Huile de palme brute', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('huile de palme brute'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '151219', 'Huile de tournesol ou de carthame', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('huile de tournesol ou de carthame'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '151519', 'Huile de lin autre', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('huile de lin autre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '151710', 'Margarine', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('margarine'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 16-22: Food products
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '160414', 'Thons et bonites préparés ou conservés', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('thons et bonites prepares ou conserves'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '160420', 'Preparations de poissons, crustaces', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('preparations de poissons crustaces'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '170114', 'Sucre de canne', 'XX', 'FR', 329.0, 'AD', NULL, false, '2024-01-01', lower('sucre de canne'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '170199', 'Sucre de betterave autre', 'XX', 'FR', 329.0, 'AD', NULL, false, '2024-01-01', lower('sucre de betterave autre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '170410', 'Bonbons et sucreries sans cacao', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('bonbons et sucreries sans cacao'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '180631', 'Chocolat en tablettes, fourri', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('chocolat en tablettes fourri'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '180632', 'Chocolat en tablettes, non fourri', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('chocolat en tablettes non fourri'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '190531', 'Biscuits et gateaux au sucre', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('biscuits et gateaux au sucre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '190590', 'Produits de boulangerie, patisserie autres', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('produits de boulangerie patisserie autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '200210', 'Tomates preparees ou conservees', 'XX', 'FR', 14.2, 'AD', NULL, false, '2024-01-01', lower('tomates preparees ou conservees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '200919', 'Jus de fruits congeles', 'XX', 'FR', 14.4, 'AD', NULL, false, '2024-01-01', lower('jus de fruits congeles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '200980', 'Jus de legumes', 'XX', 'FR', 14.4, 'AD', NULL, false, '2024-01-01', lower('jus de legumes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '210111', 'Extrait de cafe en poudre', 'XX', 'FR', 7.5, 'AD', NULL, false, '2024-01-01', lower('extrait de cafe en poudre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '210690', 'Preparations alimentaires autres', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('preparations alimentaires autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220110', 'Eaux minerales et gazeifiees', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('eaux minerales et gazeifiees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220300', 'Bieres de malt', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('bieres de malt'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220410', 'Vins de raisins frais, petillants', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('vins de raisins frais petillants'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220421', 'Vins en bouteilles <= 2L', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('vins en bouteilles inferieur 2l'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220830', 'Whisky', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('whisky'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220840', 'Rhum et eaux-de-vie de canne', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('rhum et eaux-de-vie de canne'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220850', 'Gin', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('gin'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220860', 'Vodka', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('vodka'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220870', 'Liqueurs', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('liqueurs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '220890', 'Spiritus et eaux-de-vie autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('spiritus et eaux-de-vie autres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 25-27: Minerals, fuels
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '250100', 'Sel de table et sel de cuisine', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('sel de table et sel de cuisine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '250410', 'Phosphore naturel en poudre', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('phosphore naturel en poudre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '251512', 'Marbre brut ou scié', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('marbre brut ou scie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '252329', 'Ciments autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('ciments autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '252400', 'Amiante', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('amiante'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '270112', 'Cokes de houille', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('cokes de houille'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '270900', 'Petrole brut', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('petrole brut'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '271012', 'Essences', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('essences'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '271019', 'Petrole raffine autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('petrole raffine autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '271111', 'Gaz naturel liquefie', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('gaz naturel liquefie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '271121', 'Gaz naturel en estado gazeux', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('gaz naturel en estado gazeux'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 39-40: Plastics, rubber
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '390110', 'Polyethylene densite < 0.94, en forme primaire', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('polyethylene densite inferieur 0.94 en forme primaire'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '390120', 'Polyethylene densite >= 0.94, en forme primaire', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('polyethylene densite superieur 0.94 en forme primaire'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '390210', 'Polypropylene en forme primaire', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('polypropylene en forme primaire'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '390311', 'Polystyrene expandable en forme primaire', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('polystyrene expandable en forme primaire'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '390761', 'PET en forme primaire, viscosite >= 78', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('pet en forme primaire viscosite'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '391723', 'Tuyaux de polymere de vinyle', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('tuyaux de polymere de vinyle'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '391810', 'Revêtements de sol en polymere de vinyle', 'XX', 'FR', 4.0, 'AD', NULL, false, '2024-01-01', lower('revetements de sol en polymere de vinyle'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '391910', 'Adhesifs en bandes <= 20cm largeur', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('adhesifs en bandes inferieur 20cm largeur'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '392010', 'Plaques de polymere de vinyle', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('plaques de polymere de vinyle'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '392330', 'Bidons, flacons en polymere', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('bidons flacons en polymere'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '392690', 'Articles en matieres plastiques autres', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('articles en matieres plastiques autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '401110', 'Pneus neufs pour voitures', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('pneus neufs pour voitures'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '401120', 'Pneus neufs pour bus et camions', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('pneus neufs pour bus et camions'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '401199', 'Pneus neufs autres', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('pneus neufs autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '401512', 'Gants en caoutchouc chirurgicaux', 'XX', 'FR', 5.0, 'AD', NULL, false, '2024-01-01', lower('gants en caoutchouc chirurgicaux'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '401699', 'Articles en caoutchouc durs autres', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('articles en caoutchouc durs autres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 42-43: Leather goods
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '420211', 'Valises, mallettes en bois ou metallique', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('valises mallettes en bois ou metallique'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '420212', 'Valises, mallettes en matieres plastiques', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('valises mallettes en matieres plastiques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '420221', 'Sacs à main en cuir', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('sacs a main en cuir'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '420229', 'Sacs à main autres matieres', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('sacs a main autres matieres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '420310', 'Articles d''habillement en cuir', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('articles d''habillement en cuir'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '420321', 'Gants en cuir', 'XX', 'FR', 5.0, 'AD', NULL, false, '2024-01-01', lower('gants en cuir'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '420500', 'Autres articles en cuir', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('autres articles en cuir'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '430310', 'Articles d''habillement en fourrure', 'XX', 'FR', 10.0, 'AD', NULL, false, '2024-01-01', lower('articles d''habillement en fourrure'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 50-54: Silk, man-made fibers
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '500400', 'Fils de soie', 'XX', 'FR', 7.0, 'AD', NULL, false, '2024-01-01', lower('fils de soie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '500710', 'Tissus de soie ou de dechets de soie', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('tissus de soie ou de dechets de soie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '520100', 'Coton non cardé ni peigné', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('coton non carde ni peigne'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '520811', 'Tissus de coton tricotes plats - peinture', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('tissus de coton plats peinture'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '520832', 'Tissus de coton teints', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('tissus de coton teints'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '540761', 'Tissus de fibres synthétiques, filaments continu polyesters', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('tissus de fibres synthetiques filaments continus polyesters'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '550951', 'Fils de fibres synthétiques coupées mélangées coton', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('fils de fibres synthetiques coupees melangees coton'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 65-66: Headwear, umbrellas
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '650500', 'Bonnets, fichus, tricots et facons similaires', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('bonnets fichus tricots et facons similaires'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '650610', 'Casques de protection', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('casques de protection'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '650699', 'Casquettes et autres couvre-chefs', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('casquettes et autres couvre-chefs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '660110', 'Parapluies de jardin', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('parapluies de jardin'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '660191', 'Parapluies à manche en forme de canne', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('parapluies a manche en forme de canne'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '660200', 'Cannes, sellettes, fouets', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('cannes sellettes fouets'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 68-70: Stone, cement, glass
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '680100', 'Pierres de taille', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('pierres de taille'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '680293', 'Granit travaille', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('granit travaille'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '680299', 'Pierres precieuses ou semiprecieuses travaillees', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('pierres precieuses ou semiprecieuses travaillees'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '690721', 'Carreaux de ceramique, coeff absorption <= 0.5%', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('carreaux de ceramique coeff absorption inferieur 0.5'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '690722', 'Carreaux de ceramique, 0.5% < coeff <= 10%', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('carreaux de ceramique 0.5 10'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '691110', 'Articles de table ou de cuisine en porcelaine', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('articles de table ou de cuisine en porcelaine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '700711', 'Verre de securite, dimensions <= 10mm', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('verre de securite dimensions inferieur 10mm'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '700721', 'Verre feuilletage, dimensions <= 10mm', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('verre feuilletage dimensions inferieur 10mm'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '701337', 'Verres a pied pour boire', 'XX', 'FR', 6.0, 'AD', NULL, false, '2024-01-01', lower('verres a pied pour boire'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '701342', 'Verres pour services de table en cristal', 'XX', 'FR', 6.0, 'AD', NULL, false, '2024-01-01', lower('verres pour services de table en cristal'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '701399', 'Articles de verrerie autres', 'XX', 'FR', 6.0, 'AD', NULL, false, '2024-01-01', lower('articles de verrerie autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '701810', 'Perles de verre', 'XX', 'FR', 4.0, 'AD', NULL, false, '2024-01-01', lower('perles de verre'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 72-73: Iron, steel, metal articles
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '720810', 'Lamines de fer ou acier laminées à chaud, en rouleaux, non revêtues', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('lamines de fer ou acier laminées a chaud en rouleaux non revetues'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '720851', 'Lamines de fer ou acier > 10mm epaisseur', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('lamines de fer ou acier superieur 10mm epaisseur'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '721049', 'Fer ou acier laminés à froid, revêtu de zinc', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('fer ou acier laminés a froid revetu de zinc'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '721391', 'Blocs et billettes de fer ou acier', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('blocs et billettes de fer ou acier'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '721933', 'Tôles de fer ou acier inoxydable, epaisseur 1-3mm', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('toles de fer ou acier inoxydable epaisseur 1-3mm'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '730411', 'Tubes de fer ou acier, acier inox, sans joint', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('tubes de fer ou acier acier inox sans joint'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '730890', 'Structures et parties de structures en fer ou acier', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('structures et parties de structures en fer ou acier'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '731815', 'Vis et boulons en fer ou acier', 'XX', 'FR', 3.7, 'AD', NULL, false, '2024-01-01', lower('vis et boulons en fer ou acier'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '732111', 'Foyers pour cuisinières au gaz', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('foyers pour cuisinieres au gaz'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '732690', 'Articles en fer ou acier autres', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('articles en fer ou acier autres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 74-83: Non-ferrous metals
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '740311', 'Cathodes et sections de cathodes de cuivre raffiné', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('cathodes et sections de cathodes de cuivre raffine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '740811', 'Fils de cuivre, section carree', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('fils de cuivre section carree'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '760110', 'Aluminium non allié', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('aluminium non allie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '760410', 'Barres, profilés en aluminium', 'XX', 'FR', 7.5, 'AD', NULL, false, '2024-01-01', lower('barres profils en aluminium'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '760611', 'Plaques en aluminium allié', 'XX', 'FR', 7.5, 'AD', NULL, false, '2024-01-01', lower('plaques en aluminium allie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '760711', 'Feuilles d''aluminium, non adhesives', 'XX', 'FR', 6.0, 'AD', NULL, false, '2024-01-01', lower('feuilles d''aluminium non adhesives'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '761010', 'Portes, fenêtres en aluminium', 'XX', 'FR', 7.5, 'AD', NULL, false, '2024-01-01', lower('portes fenetres en aluminium'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '810110', 'Poudres de tungstene', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('poudres de tungstene'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '811299', 'Terres rares et metaux d''oxydes, autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('terres rares et metaux d''oxydes autres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 84: Machinery
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '841459', 'Soufflantes et ventilateurs autres', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('soufflantes et ventilateurs autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '841510', 'Machines de climatisation', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('machines de climatisation'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '841810', 'Refrigerateurs-congelateurs combinés', 'XX', 'FR', 2.5, 'AD', NULL, false, '2024-01-01', lower('refrigerateurs-congelateurs combines'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '841821', 'Refrigerateurs à compresseur', 'XX', 'FR', 2.5, 'AD', NULL, false, '2024-01-01', lower('refrigerateurs a compresseur'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '841869', 'Appareils de climatisation, pompes à chaleur', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('appareils de climatisation pompes a chaleur'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '842211', 'Lave-vaisselle', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('lave-vaisselle'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '842230', 'Machines à remplir, fermer, sceller', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('machines a remplir fermer sceller'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '842890', 'Machines de manutention autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('machines de manutention autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '842911', 'Bulldozers à chenilles', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('bulldozers a chenilles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '843139', 'Parties de machines de levage', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('parties de machines de levage'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '844332', 'Imprimantes, photocopieurs multifonctions', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('imprimantes photocopieurs multifonctions'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '845011', 'Lave-linge, charge frontale', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('lave-linge charge frontale'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '845020', 'Lave-linge, capacite > 10kg', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('lave-linge capacite superieur 10kg'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '846721', 'Machines à main pour perce', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('machines a main pour perce'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '847130', 'Machines de traitement de l''information portatives', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('machines de traitement de l''information portatives'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '847141', 'Machines de traitement de l''information autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('machines de traitement de l''information autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '847160', 'Unités d''entrée-sortie pour machines de traitement', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('unites d''entree-sortie pour machines de traitement'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '847330', 'Parties de machines de traitement de l''information', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('parties de machines de traitement de l''information'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '847910', 'Machines pour le terrassement', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('machines pour le terrassement'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '847989', 'Machines et dispositifs mécaniques autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('machines et dispositifs mecaniques autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '848180', 'Robinetterie et appareillage hydraulique', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('robinetterie et appareillage hydraulique'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '848310', 'Arbres de transmission', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('arbres de transmission'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '850440', 'Convertisseurs statiques (alimentation)', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('convertisseurs statiques alimentation'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '850760', 'Accumulateurs lithium-ion', 'XX', 'FR', 4.7, 'AD', NULL, false, '2024-01-01', lower('accumulateurs lithium-ion'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 85: Electrical equipment
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '850110', 'Moteurs electriques puissance <= 37.5W', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('moteurs electriques puissance inferieur 37.5w'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '850161', 'Alternateurs puissance <= 750 VA', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('alternateurs puissance inferieur 750 va'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '850421', 'Transformateurs de liquidation', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('transformateurs de liquidation'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '851712', 'Telephones cellulaires', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('telephones cellulaires'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '851762', 'Machines de reseaux (routeurs, switches)', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('machines de reseaux routeurs switches'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '852872', 'Recepteurs de television en couleur', 'XX', 'FR', 14.0, 'AD', NULL, false, '2024-01-01', lower('recepteurs de television en couleur'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '852859', 'Moniteurs et projecteurs autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('moniteurs et projecteurs autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '853610', 'Fusibles', 'XX', 'FR', 3.5, 'AD', NULL, false, '2024-01-01', lower('fusibles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '853620', 'Disjoncteurs', 'XX', 'FR', 3.5, 'AD', NULL, false, '2024-01-01', lower('disjoncteurs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '853650', 'Interrupteurs', 'XX', 'FR', 3.5, 'AD', NULL, false, '2024-01-01', lower('interrupteurs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '853690', 'Appareillage de commutation autre', 'XX', 'FR', 3.5, 'AD', NULL, false, '2024-01-01', lower('appareillage de commutation autre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '854140', 'Photodiodes, cellules solaires', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('photodiodes cellules solaires'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '854231', 'Processeurs', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('processeurs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '854232', 'Mémoires', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('memoires'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '854239', 'Circuits integres autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('circuits integres autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '854449', 'Conducteurs isolés autres', 'XX', 'FR', 3.5, 'AD', NULL, false, '2024-01-01', lower('conducteurs isoles autres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 86-89: Transport
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '860400', 'Wagons de maintenance de voie ferrée', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('wagons de maintenance de voie ferrée'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870120', 'Tracteurs semi-remorque', 'XX', 'FR', 11.2, 'AD', NULL, false, '2024-01-01', lower('tracteurs semi-remorque'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870321', 'Voitures essence <= 1000cc', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('voitures essence inferieur 1000cc'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870322', 'Voitures essence 1000-1500cc', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('voitures essence 1000-1500cc'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870323', 'Voitures essence 1500-3000cc', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('voitures essence 1500-3000cc'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870324', 'Voitures essence > 3000cc', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('voitures essence superieur 3000cc'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870340', 'Voitures hybrides', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('voitures hybrides'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870360', 'Voitures diesel 1500-2500cc', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('voitures diesel 1500-2500cc'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870380', 'Voitures electriques', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('voitures electriques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870410', 'Camions benne', 'XX', 'FR', 7.0, 'AD', NULL, false, '2024-01-01', lower('camions benne'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870421', 'Camions diesel > 5t mais <= 20t', 'XX', 'FR', 7.0, 'AD', NULL, false, '2024-01-01', lower('camions diesel superieur 5t mais inferieur 20t'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '870422', 'Camions diesel > 20t', 'XX', 'FR', 7.0, 'AD', NULL, false, '2024-01-01', lower('camions diesel superieur 20t'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '871120', 'Motos 250-500cc', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('motos 250-500cc'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '871160', 'Motos electriques', 'XX', 'FR', 6.5, 'AD', NULL, false, '2024-01-01', lower('motos electriques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '880240', 'Avions de tourisme, poids > 15000kg', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('avions de tourisme poids superieur 15000kg'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '880600', 'Drones', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('drones'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '890110', 'Navires de croisiere', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('navires de croisiere'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '890200', 'Navires de peche', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('navires de peche'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '890510', 'Feux flottants', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('feux flottants'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 90-92: Optical, medical, watches, musical
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '900150', 'Lentilles en matieres autres que verre', 'XX', 'FR', 2.0, 'AD', NULL, false, '2024-01-01', lower('lentilles en matieres autres que verre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '900410', 'Lunettes de soleil', 'XX', 'FR', 2.0, 'AD', NULL, false, '2024-01-01', lower('lunettes de soleil'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '901890', 'Instruments et appareils pour chirurgie', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('instruments et appareils pour chirurgie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '902110', 'Orthopedies', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('orthopedies'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '902212', 'Appareils de radiographie', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('appareils de radiographie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '902519', 'Thermometres, barometres autres', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('thermometres barometres autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '902780', 'Instruments d''analyse physico-chimiques', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('instruments d''analyse physico-chimiques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '910111', 'Montres à affichage mecanique boitier or', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('montres a affichage mecanique boitier or'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '910211', 'Montres à affichage mecanique, non or', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('montres a affichage mecanique non or'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '910221', 'Montres à affichage automatique', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('montres a affichage automatique'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '920110', 'Pianos', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('pianos'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '920210', 'Instruments à cordes', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('instruments a cordes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '920510', 'Instruments à vent (cuivre)', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('instruments a vent cuivre'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '920600', 'Instruments de percussion', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('instruments de percussion'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '920710', 'Synthetiseurs', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('synthetiseurs'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 93: Weapons
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '930200', 'Revolver et pistolets', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('revolver et pistolets'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '930330', 'Carabines de sport', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('carabines de sport'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '930630', 'Cartouches et munitions', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('cartouches et munitions'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 94: Furniture
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '940130', 'Sieges a revision de hauteur', 'XX', 'FR', 3.7, 'AD', NULL, false, '2024-01-01', lower('sieges a revision de hauteur'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940140', 'Sieges a dispositif mecanique', 'XX', 'FR', 3.7, 'AD', NULL, false, '2024-01-01', lower('sieges a dispositif mecanique'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940161', 'Sieges garnis', 'XX', 'FR', 3.7, 'AD', NULL, false, '2024-01-01', lower('sieges garnis'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940171', 'Sieges metalliques', 'XX', 'FR', 3.7, 'AD', NULL, false, '2024-01-01', lower('sieges metalliques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940320', 'Mobilier metallique', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('mobilier metallique'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940330', 'Mobilier de bureau en bois', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('mobilier de bureau en bois'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940340', 'Mobilier de cuisine en bois', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('mobilier de cuisine en bois'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940350', 'Mobilier de chambre a coucher en bois', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('mobilier de chambre a coucher en bois'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940360', 'Autres mobiliers en bois', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('autres mobiliers en bois'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940421', 'Matelas de caoutchouc ou de plastiques', 'XX', 'FR', 3.7, 'AD', NULL, false, '2024-01-01', lower('matelas de caoutchouc ou de plastiques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940429', 'Matelas autres', 'XX', 'FR', 3.7, 'AD', NULL, false, '2024-01-01', lower('matemas autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '940542', 'LED eclairage', 'XX', 'FR', 3.7, 'AD', NULL, false, '2024-01-01', lower('led eclairage'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 95: Toys, games, sports
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '950300', 'Dolls, figures et autres jouets', 'XX', 'FR', 4.7, 'AD', NULL, false, '2024-01-01', lower('dolls figures et autres jouets'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '950430', 'Jeux d''argent ou de hasard', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('jeux d''argent ou de hasard'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '950450', 'Consoles et machines de jeux video', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('consoles et machines de jeux video'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '950661', 'Ballons et ballons gonflables', 'XX', 'FR', 4.7, 'AD', NULL, false, '2024-01-01', lower('ballons et ballons gonflables'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '950691', 'Articles pour sport et gymnastique', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('articles pour sport et gymnastique'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '950710', 'Cannes à pecher', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('cannes a pecher'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 96: Miscellaneous
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '960321', 'Brosses à dents', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('brosses a dents'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '960330', 'Brosses pour beaux-arts', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('brosses pour beaux-arts'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '960500', 'Necessaires de voyage', 'XX', 'FR', 3.0, 'AD', NULL, false, '2024-01-01', lower('necessaires de voyage'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '960810', 'Stylos à bille', 'XX', 'FR', 4.5, 'AD', NULL, false, '2024-01-01', lower('stylos a bille'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '960850', 'Crayons mecaniques pour dessin', 'XX', 'FR', 3.0, 'AD', NULL, false, '2024-01-01', lower('crayons mecaniques pour dessin'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '961210', 'Tampons encreurs', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('tampons encreurs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '961310', 'Briquets a gaz', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('briquets a gaz'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '961320', 'Briquets a piezo', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('briquets a piezo'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '961610', 'Vaporisateurs de parfumerie', 'XX', 'FR', 3.0, 'AD', NULL, false, '2024-01-01', lower('vaporisateurs de parfumerie'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '961800', 'Mannequins et maquettes', 'XX', 'FR', 2.7, 'AD', NULL, false, '2024-01-01', lower('mannequins et maquettes'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 71: Jewelry, precious stones
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '710110', 'Perles naturelles', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('perles naturelles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '710239', 'Pierres precieuses taillees autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('pierres precieuses taillees autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '710812', 'Or en poudre ou en graine', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('or en poudre ou en graine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '711311', 'Articles de joaillerie en argent', 'XX', 'FR', 4.0, 'AD', NULL, false, '2024-01-01', lower('articles de joaillerie en argent'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '711319', 'Articles de joaillerie en metaux precieux', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('articles de joaillerie en metaux precieux'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '711620', 'Articles en pierres precieuses ou semiprecieuses', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('articles en pierres precieuses ou semiprecieuses'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 48: Paper
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '480255', 'Papier et carton non couches, poids < 40g/m2', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('papier et carton non couches poids inferieur 40g'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '481141', 'Papier couché adhesif', 'XX', 'FR', 3.5, 'AD', NULL, false, '2024-01-01', lower('papier couche adhesif'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '481810', 'Papier de toilette', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('papier de toilette'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '481910', 'Cartons ondulés', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('cartons ondules'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '482010', 'Cahiers et registres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('cahiers et registres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 44: Wood
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '440710', 'Bois de conifere scié, epaisseur > 6mm', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('bois de conifere scie epaisseur superieur 6mm'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '440799', 'Bois d''essences diverses scié, epaisseur > 6mm', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('bois d''essences diverses scie epaisseur superieur 6mm'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '441114', 'Panneaux de fibres de bois', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('panneaux de fibres de bois'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '441231', 'Contreplaque de bois', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('contreplaque de bois'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '442199', 'Articles en bois autres', 'XX', 'FR', 2.0, 'AD', NULL, false, '2024-01-01', lower('articles en bois autres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 33: Essential oils, cosmetics
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '330112', 'Huile essentielle d''orange', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('huile essentielle d''orange'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '330190', 'Huiles essentielles autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('huiles essentielles autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '330300', 'Eaux de toilette et de parfum', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('eaux de toilette et de parfum'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '330410', 'Produits de maquillage pour les levres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('produits de maquillage pour les levres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '330430', 'Produits de manucure ou pedicure', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('produits de manucure ou pedicure'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '330499', 'Produits de beauté ou de maquillage autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('produits de beaute ou de maquillage autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '330510', 'Shampoings', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('shampoings'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '330590', 'Produits capillaires autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('produits capillaires autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '330610', 'Dentifrices', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('dentifrices'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 61: Tricoterie (vêtements)
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '610120', 'Manteaux et cabans tricotes, coton, hommes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('manteaux et cabans tricotes coton hommes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '610443', 'Robes tricotes, fibres synthetiques, femmes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('robes tricotes fibres synthetiques femmes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '610510', 'Chemises tricotees, coton, hommes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('chemises tricotees coton hommes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '610910', 'T-shirts et maillots en coton', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('t-shirts et maillots en coton'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '611020', 'Pulls, gilets en coton tricotes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('pulls gilets en coton tricotes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '611030', 'Pulls, gilets en fibres synthetiques', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('pulls gilets en fibres synthetiques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '611090', 'Pulls, gilets en matieres textiles', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('pulls gilets en matieres textiles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '611130', 'Articles pour bebes en fibres synthetiques', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('articles pour bebes en fibres synthetiques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '611595', 'Chaussettes en coton, tricotees', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('chaussettes en coton tricotees'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 62: Confection (vêtements non tricotés)
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '620110', 'Manteaux de pluie, coton', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('manteaux de pluie coton'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '620322', 'Costumes en coton, hommes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('costumes en coton hommes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '620342', 'Pantalons en coton, hommes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('pantalons en coton hommes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '620443', 'Robes en fibres synthetiques, femmes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('robes en fibres synthetiques femmes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '620520', 'Chemises en coton, hommes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('chemises en coton hommes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '620630', 'Chemises en coton, femmes', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('chemises en coton femmes'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 63: Articles textiles finis
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '630221', 'Linge de lit imprimé en coton', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('linge de lit imprime en coton'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '630231', 'Linge de table et linge de cuisine en coton', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('linge de table et linge de cuisine en coton'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '630260', 'Articles d''hotel, de toilette ou de cuisine en fibres synthetiques', 'XX', 'FR', 12.0, 'AD', NULL, false, '2024-01-01', lower('articles d''hotel de toilette ou de cuisine en fibres synthetiques'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '630533', 'Sacs de matieres textiles pour emballage, polyethylene', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('sacs de matieres textiles pour emballage polyethylene'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '630790', 'Articles fins de couture en matieres textiles autres', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('articles fins de couture en matieres textiles autres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 64: Chaussures
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '640291', 'Chaussures à semelles et tiges en caoutchouc/plastique', 'XX', 'FR', 16.9, 'AD', NULL, false, '2024-01-01', lower('chaussures a semelles et tiges en caoutchouc plastique'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '640299', 'Chaussures en caoutchouc/plastique autres', 'XX', 'FR', 16.9, 'AD', NULL, false, '2024-01-01', lower('chaussures en caoutchouc plastique autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '640351', 'Chaussures en cuir, semelle caoutchouc, cheville', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('chaussures en cuir semelle caoutchouc cheville'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '640359', 'Chaussures en cuir, semelle caoutchouc autres', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('chaussures en cuir semelle caoutchouc autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '640391', 'Chaussures en cuir couvrant la cheville', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('chaussures en cuir couvrant la cheville'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '640399', 'Chaussures en cuir autres', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('chaussures en cuir autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '640411', 'Chaussures de sport, tige matieres textiles, semelle caoutchouc', 'XX', 'FR', 16.9, 'AD', NULL, false, '2024-01-01', lower('chaussures de sport tige matieres textiles semelle caoutchouc'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '640520', 'Chaussures a tiges en matieres textiles', 'XX', 'FR', 16.9, 'AD', NULL, false, '2024-01-01', lower('chaussures a tiges en matieres textiles'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '640610', 'Tiges et parties de tiges de chaussures', 'XX', 'FR', 8.0, 'AD', NULL, false, '2024-01-01', lower('tiges et parties de tiges de chaussures'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 28-29: Chemicals
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '283691', 'Carbonates de sodium (soude)', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('carbonates de sodium soude'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '284330', 'Sulfates d''argent', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('sulfates d''argent'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '294110', 'Penicillines et derives', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('penicillines et derives'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '294190', 'Antibiotiques autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('antibiotiques autres'), CURRENT_TIMESTAMP);

-- ============================================================
-- CHAPTER 30: Pharmaceutical products
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, trade_agreement_code, is_prefential, valid_from, search_text, created_at) VALUES
(gen_random_uuid(), '300190', 'Organes animaux pour greffes', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('organes animaux pour greffes'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '300213', 'Immunologiques en formes dosage', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('immunologiques en formes dosage'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '300390', 'Medicaments autres', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('medicaments autres'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '300490', 'Medicaments en doses', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('medicaments en doses'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '300510', 'Pansements adhesifs', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('pansements adhesifs'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '300650', 'Trousse d''urgence', 'XX', 'FR', 0.0, 'AD', NULL, false, '2024-01-01', lower('trousse d''urgence'), CURRENT_TIMESTAMP);

-- ============================================================
-- ANTI-DUMPING DUTIES — Chinese steel products
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_anti_dumping, anti_dumping_duty, trade_agreement_code, is_prefential, valid_from, notes, search_text, created_at) VALUES
(gen_random_uuid(), '720810', 'Lamines laminées à chaud, non revêtues - Chine', 'CN', 'FR', 0.0, 'SD', true, 18.1, NULL, false, '2024-01-01', 'Anti-dumping duty on hot-rolled steel from China per EU Reg 2022/191', lower('lamines laminées a chaud non revetues chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '720917', 'Lamines laminées à froid, epaisseur 0.5-1mm - Chine', 'CN', 'FR', 0.0, 'SD', true, 22.1, NULL, false, '2024-01-01', 'Anti-dumping duty on cold-rolled steel from China per EU Reg 2022/191', lower('lamines laminées a froid epaisseur 0.5-1mm chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '721049', 'Laminés revêtus de zinc - Chine', 'CN', 'FR', 0.0, 'SD', true, 17.6, NULL, false, '2024-01-01', 'Anti-dumping duty on zinc-coated steel from China', lower('lamines revetus de zinc chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '721391', 'Blocs et billettes - Chine', 'CN', 'FR', 0.0, 'SD', true, 18.1, NULL, false, '2024-01-01', 'Anti-dumping duty on steel billets from China', lower('blocs et billettes chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '721933', 'Tôles inoxydable 1-3mm - Chine', 'CN', 'FR', 0.0, 'SD', true, 27.9, NULL, false, '2024-01-01', 'Anti-dumping duty on stainless steel sheets from China', lower('toles inoxydable 1-3mm chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '722011', 'Laminés plats inoxydable, polis - Chine', 'CN', 'FR', 0.0, 'SD', true, 27.9, NULL, false, '2024-01-01', 'Anti-dumping duty on polished stainless steel from China', lower('lamines plats inoxydable polis chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '722240', 'Sections laminées en acier inoxydable - Chine', 'CN', 'FR', 0.0, 'SD', true, 27.9, NULL, false, '2024-01-01', 'Anti-dumping duty on stainless steel sections from China', lower('sections laminées en acier inoxydable chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '722550', 'Laminés plats inoxydable autre - Chine', 'CN', 'FR', 0.0, 'SD', true, 27.9, NULL, false, '2024-01-01', 'Anti-dumping duty on stainless flat products from China', lower('lamines plats inoxydable autre chine'), CURRENT_TIMESTAMP);

-- ============================================================
-- ANTI-DUMPING DUTIES — Chinese solar panels
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_anti_dumping, anti_dumping_duty, trade_agreement_code, is_prefential, valid_from, notes, search_text, created_at) VALUES
(gen_random_uuid(), '850161', 'Alternateurs solaires ≤750VA - Chine', 'CN', 'FR', 0.0, 'SD', true, 36.1, NULL, false, '2024-01-01', 'Anti-dumping duty on Chinese solar panels per EU Reg 2024/1725', lower('alternateurs solaires inferieur 750va chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '850162', 'Alternateurs solaires 750VA-10kVA - Chine', 'CN', 'FR', 0.0, 'SD', true, 36.1, NULL, false, '2024-01-01', 'Anti-dumping duty on Chinese solar panels per EU Reg 2024/1725', lower('alternateurs solaires 750va-10kva chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '854140', 'Cellules photovoltaiques - Chine', 'CN', 'FR', 0.0, 'SD', true, 36.1, NULL, false, '2024-01-01', 'Anti-dumping duty on Chinese PV cells per EU Reg 2024/1725', lower('cellules photovoltaiques chine'), CURRENT_TIMESTAMP);

-- ============================================================
-- ANTI-DUMPING DUTIES — Chinese electric bicycles
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_anti_dumping, anti_dumping_duty, trade_agreement_code, is_prefential, valid_from, notes, search_text, created_at) VALUES
(gen_random_uuid(), '87116010', 'Vélos électriques à pedalage assisté - Chine', 'CN', 'FR', 0.0, 'SD', true, 48.5, NULL, false, '2024-01-01', 'Anti-dumping duty on Chinese e-bikes per EU Reg 2019/1769', lower('velos electriques a pedalage assiste chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '87116090', 'Vélos électriques autres - Chine', 'CN', 'FR', 0.0, 'SD', true, 48.5, NULL, false, '2024-01-01', 'Anti-dumping duty on Chinese e-bikes per EU Reg 2019/1769', lower('velos electriques autres chine'), CURRENT_TIMESTAMP);

-- ============================================================
-- ANTI-DUMPING DUTIES — Chinese aluminum foil
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_anti_dumping, anti_dumping_duty, trade_agreement_code, is_prefential, valid_from, notes, search_text, created_at) VALUES
(gen_random_uuid(), '76071100', 'Feuilles d''aluminium non adhesives ≤0.2mm - Chine', 'CN', 'FR', 0.0, 'SD', true, 28.5, NULL, false, '2024-01-01', 'Anti-dumping duty on Chinese aluminum foil per EU Reg 2018/1282', lower('feuilles d''aluminium non adhesives inferieur 0.2mm chine'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '76071900', 'Feuilles d''aluminium adhesives - Chine', 'CN', 'FR', 0.0, 'SD', true, 28.5, NULL, false, '2024-01-01', 'Anti-dumping duty on Chinese aluminum foil per EU Reg 2018/1282', lower('feuilles d''aluminium adhesives chine'), CURRENT_TIMESTAMP);

-- ============================================================
-- ANTI-DUMPING DUTIES — Indian glass fiber
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_anti_dumping, anti_dumping_duty, trade_agreement_code, is_prefential, valid_from, notes, search_text, created_at) VALUES
(gen_random_uuid(), '701911', 'Fibres de verre courte, non tissees - Inde', 'IN', 'FR', 0.0, 'SD', true, 36.1, NULL, false, '2024-01-01', 'Anti-dumping duty on Indian glass fiber per EU Reg', lower('fibres de verre courte non tissees inde'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '701912', 'Nappes de fibres de verre - Inde', 'IN', 'FR', 0.0, 'SD', true, 36.1, NULL, false, '2024-01-01', 'Anti-dumping duty on Indian glass fiber mats', lower('nappes de fibres de verre inde'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '701931', 'Voiles de fibres de verre - Inde', 'IN', 'FR', 0.0, 'SD', true, 36.1, NULL, false, '2024-01-01', 'Anti-dumping duty on Indian glass fiber veils', lower('voiles de fibres de verre inde'), CURRENT_TIMESTAMP),
(gen_random_uuid(), '701990', 'Articles en fibres de verre autres - Inde', 'IN', 'FR', 0.0, 'SD', true, 36.1, NULL, false, '2024-01-01', 'Anti-dumping duty on Indian glass fiber articles', lower('articles en fibres de verre autres inde'), CURRENT_TIMESTAMP);

-- ============================================================
-- ANTI-DUMPING DUTIES — Chinese electric vehicles
-- ============================================================
INSERT INTO taric_rates (id, hs_code, description, origin_country, destination_country, duty_rate, duty_type, is_anti_dumping, anti_dumping_duty, trade_agreement_code, is_prefential, valid_from, notes, search_text, created_at) VALUES
(gen_random_uuid(), '870380', 'Voitures electriques - Chine', 'CN', 'FR', 0.0, 'SD', true, 35.9, NULL, false, '2024-10-31', 'Anti-dumping duty on Chinese EVs per EU Reg 2024/2754 (provisional)', lower('voitures electriques chine'), CURRENT_TIMESTAMP);
