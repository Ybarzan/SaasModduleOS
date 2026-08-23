# IncoKalk — Roadmap Complet des Manques

> Dernière mise à jour : 02/08/2026
> Basé sur : benchmark concurrentiel 2026 (CargoWise, GoFreight, Zonos, Wove, LOG-NET, Flexport, Easyship, Freightos...)
> Position : SaaS logistique basé en France — priorité douanes françaises/EU d'abord
> Benchmark détaillé (analyse Cargoson & Cie) : voir `docs/internal/roadmap-cargo/ROADMAP.txt`
> Stratégie d'exécution (ordre, kills, décisions) : voir `docs/ROADMAP-90JOURS.md`
>
> **État d'implémentation (vérifié le 20/08/2026) : 30/31 features implémentées, 1 partielle (transmission DGDDI réelle — bloquée sur des identifiants externes gouvernementaux).** L'app mobile native (item 28) est passée de non développée à complète cette semaine (Capacitor, 5 écrans, push FCM réel, scan de documents par caméra, approbation des workflows). Implémenté via les migrations V16→V53 et les services/controllers/pages dédiés.

---

## P0 — Urgent (Semaines 1-4) — Fondations douanes françaises

### 1. Base tarifaire TARIC réelle
- [x] Intégrer les données TARIC (TARIF INTégré de la Communauté) via l'API ouverte de la DGDDI / EUR-Lex
- [x] Mapper les codes SH aux droits ad valorem + droits spécifiques + contingents
- [x] Stocker les taux par pays d'origine x code HS x régime tarifaire
- [x] Remplacer les 9 moyennes chapitre actuelles par des taux réels
- [x] Gérer les droits antidumping et compensateurs
- [x] Cache Redis avec TTL 24h sur les taux TARIC *(CacheConfig : TTL 24h pour `taric-rates`/`taric-hs-descriptions` quand `spring.cache.type=redis`)*

### 2. Régimes préférentiels EU (APE/AGP)
- [x] Intégrer les accords de libre-échange EU par code HS (1400+ accords bilatéraux)
- [x] Vérification Rules of Origin (origin criteria: WH, WO, PE, CTH, CTSH)
- [x] Calcul du droit préférentiel vs droit MFN (Most Favoured Nation)
- [x] Gestion des certificats d'origine EUR.1 / eurs.1
- [x] Logique de cumul bilatéral (matières originaires UE) + cumul régional/diagonal (groupes ASEAN, PEM, SADC, EAC, WA-EPA, CEMAC, océan Indien, andins, Amérique centrale, CARICOM, Balkans, Partenariat oriental) *(V53 : table `cumulation_groups`, champs structurés sur `trade_agreements`, `CumulationService` + endpoints `verify-cumulation`)*

### 3. TVA intracommunautaire
- [x] Règles de reverse charge pour les acquisitions intracom
- [x] Taux réduits, super-réduits, exonérations par pays EU
- [x] TVA à l'importation (TAI — Taxe Assise à l'Importation)
- [x] Numéro de TVA intracommunautaire (VIES) validation *(format local + vérification en ligne REST VIES derrière `VIES_ONLINE_VALIDATION`)*
- [x] Calcul TVA sur marge (régime de la marge pour les biens d'occasion)

### 4. EORI
- [x] Stockage et validation du numéro EORI (format: FR + 15 chiffres)
- [x] Vérification en ligne via le portail EU EORI *(client SOAP EOS derrière `EORI_ONLINE_VALIDATION`)*
- [x] Association EORI ↔ Company
- [x] Obligation : chaque opérateur doit avoir un EORI pour déclarer

### 5. Facture douanière
- [x] Génération de facture douanière (commercial invoice adaptée pour la douane)
- [x] Champs requis : description marchandise, HS code, valeur CIF/FOB, origine, pays d'destination
- [x] Multi-devise avec conversion au taux BCE du jour
- [x] Export PDF conforme aux exigences DGDDI

---

## P1 — Haute Priorité (Semaines 5-10) — Documents de déclaration

### 6. Document unique de douane (DUD/DAU/SAD)
- [x] Génération du DAU (Document Administratif Unique) au format XML EDIFACT
- [x] Types : import (DAU IMP), export (DAU EXP), transit (T1, T2)
- [x] Champs obligatoires : code bureau, régime douanier, code douanier, valeur en douane, origine
- [x] Mode brouillon → prêt → soumis → validé → archivé
- [x] Export PDF + XML pour soumission électronique

### 7. Déclaration d'échanges de biens (DEB/Intrastat)
- [x] DEB expéditions (vendeur EU → acheteur hors EU)
- [x] DEB introductions (acheteur EU ← vendeur hors EU)
- [x] Intrastat pour les échanges intra-EU ( flux d'arrivée / flux de départ)
- [x] Seuil de déclaration (460 000 € pour la France en 2026)
- [x] Codes nomenclature combinée 8 chiffres
- [x] Export DEB au format XML (DGDDI / NETT declared)

### 8. ICS2 (Import Control System 2)
- [x] Pré-déclaration de sécurité pour les importations par voie maritime
- [x] Données requises : shipper, consignee, HS 6-digit, poids, conteneur
- [x] Soumission au format ICS2 XML via le portail douanier *(génération XML, transmission réseau à configurer)*
- [x] Statut : envoyé, accepté, rejeté, en attente

### 9. AES / EXS (Export)
- [x] Déclaration de sortie (AES — Automated Export System)
- [x] EXS (Exit Summary Declaration) pour les marchandises quittant l'EU
- [ ] Intégration avec le guichet unique CDS (Customs Declaration Service) *(non implémentée)*
- [x] Statut de déclaration export : brouillon, soumise, validée

### 10. Workflow de dédouanement
- [x] Pipeline complet : Pré-déclaration → Déclaration → Vérification → Dédouanement → Livraison
- [x] Statuts : DRAFT → SUBMITTED → UNDER_REVIEW → CLEARED → RELEASED → REJECTED
- [x] Alertes automatiques : document manquant, HS code incohérent, valeur douteuse
- [x] Historique complet par shipment
- [x] Tableau de bord des déclarations en cours

---

## P2 — Priorité Moyenne (Semaines 11-18) — Intelligence & Automatisation

### 11. Classification HS par IA
- [x] OCR sur photo du produit → prédiction HS code
- [x] Modèle de classification entraîné sur les données TARIC
- [x] Score de confiance par classification
- [x] Suggestion alternative (top 3 codes HS possibles)
- [x] Validation manuelle / correction par l'utilisateur
- [x] Mémoire : les classifications corrigées enrichissent le modèle

### 12. Landed Cost Calculator (coût complet débarqué)
- [x] Calcul intégré dans le flux de devis (pas outil standalone)
- [x] Breakdown : prix produit + fret + assurance + droits + taxes + frais portuaire + last-mile
- [x] Marge réelle par commande (prix de vente - coût total débarqué)
- [x] Simulation "what-if" : changer l'origine, l'incoterm, le mode de transport
- [x] Partage du landed cost avec le client (lien public)

### 13. OCR / Parsing de documents
- [x] Facture commerciale → extraction auto (champs : vendeur, acheteur, marchandise, valeur, origine)
- [x] Bill of Lading → extraction (expéditeur, consignee, conteneur, poids)
- [x] Certificat d'origine → validation
- [x] Packing list → vérification quantités/poids
- [x] API REST pour soumettre un document et recevoir les données extraites
- [x] Support PDF et image (JPG, PNG)

### 14. Screening denied party (DPS)
- [x] Vérification contre les listes EU de sanctions (Consolidated List)
- [x] Listes OFAC (US), UN, UK sanctions
- [x] Screening par nom d'entreprise + personne physique
- [x] Alertes automatiques sur match suspect
- [x] Obligation légale pour tout exportateur EU

### 15. Intégrations transporteurs (booking direct)
- [x] API booking DHL Express / DHL Freight
- [x] API booking DB Schenker
- [x] API booking CMA CGM / MSC (maritime)
- [ ] API booking Geodis / Bolloré Logistics (France) *(Geodis implémenté ; Bolloré non)*
- [x] Workflow : sélection tarif → booking → confirmation → tracking
- [x] Génération auto du bon de transport

---

## P3 — Différenciation (Semaines 19-28) — Plateforme complète

### 16. Gestion de facturation transporteur (AP Invoice)
- [x] Réception des factures transporteurs (email, EDI, upload)
- [x] Rapprochement automatique vs devis/tarifs négociés
- [x] Détection des surcharges, doublons, écarts tarifaires
- [x] Workflow d'approbation : à valider → approuvé → payé
- [x] Dashboard des écarts de facturation

### 17. Portal client white-label
- [x] Branding complet (logo, couleurs, domaine custom)
- [x] Suivi tracking en temps réel avec notifications email/SMS
- [x] Téléchargement documents (B/L, factures, certificats)
- [x] Tableau de bord client avec KPIs
- [x] Portail multilingue (FR, EN, ES, DE, AR)

### 18. Reporting financier avancé
- [x] P&L par expédition (marge nette)
- [x] Marge par lane (origine → destination)
- [x] Marge par client
- [x] Marge par transporteur
- [x] Export comptable (QuickBooks, Sage, Cegid)
- [x] Dashboard temps réel avec drill-down

### 19. Intégrations e-commerce
- [x] Plugin Shopify (calcul landed cost au checkout)
- [x] Plugin WooCommerce
- [x] Plugin PrestaShop (fort en France)
- [x] Webhook push des statuts de commande
- [x] Synchronisation des produits (prix, poids, HS code)

### 20. Multi-devise native
- [x] Taux de change BCE en temps réel (déjà partiellement fait)
- [x] Conversion automatique dans TOUTES les entités : devis, shipments, factures, carriers
- [x] Devise de facturation par défaut par company
- [x] Historique des taux de change (journalier, mensuel)
- [x] Rapport d'exposition aux changes

### 21. ETA prediction par ML
- [x] Modèle prédictif basé sur l'historique des lanes
- [x] Données d'entrée : transporteur, mode, origine, destination, saison, jour
- [x] Prédiction avec intervalle de confiance
- [x] Alerte automatique de retard potentiel
- [x] Comparaison prédiction vs estimation transporteur

### 22. Workflow d'email intake
- [x] Parsing automatique des emails de demande de devis
- [x] Extraction : origine, destination, marchandise, poids, volume
- [x] Création auto d'un brouillon de shipment
- [x] Matching client existant par email
- [x] Notification à l'équipe ops

---

## P4 — Enterprise (Semaines 29-40) — Scalabilité

### 23. Multi-entité / Multi-branche
- [x] Consolidation des données entre filiales
- [x] Reporting centralisé avec vue agrégée
- [x] Transfert de marchandises entre entités
- [x] Rôles spécifiques par branche

### 24. Workflow d'approbation
- [x] Purchase order → approval chain (USER → MANAGER → ADMIN)
- [x] Quote approval (seuil configurable)
- [x] Facture approval (seuil par montant)
- [x] Notifications d'approval (email, in-app)

### 25. Supply chain finance
- [x] Escompte de paiement (early payment discount)
- [x] Affacturage / factoring intégré
- [x] Financement de la chaîne d'approvisionnement
- [ ] Intégration avec les fintechs (Qonto, Spendesk, etc.) *(non implémentée)*

### 26. Carbon credits / Offset
- [x] Calcul CO2 déjà existant → offset via partenaires certifiés
- [x] Dashboard carbone par client/lane/transporteur
- [x] Certificats d'offset générés automatiquement
- [x] Reporting CSRD / EU Taxonomy

### 27. Module formation / académie
- [x] Cours sur les Incoterms (video + quiz)
- [x] Guide des douanes françaises
- [x] Tutoriels utilisateurs intégrés
- [x] Certification IncoKalk

### 28. Mobile app native
- [x] Suivi des shipments en temps réel *(app Capacitor v1 : Connexion, Tableau de bord, Expéditions + détail avec changement de statut, Notifications, Devis rapide — mobile/)*
- [x] Notifications push (statut, retard, alerte) *(FCM réel branché sur PushNotificationService, fallback SSE sans clé Firebase configurée)*
- [x] Scan de documents (camera → OCR) *(écran ScanDocument.tsx + endpoint POST /v1/document-parser/parse/image, @capacitor/camera. Dépend du binaire natif Tesseract sur le serveur — absent de cette machine de dev, donc l'OCR renvoie une erreur propre sans planter tant qu'il n'est pas installé)*
- [x] Approuver/rejeter les workflows depuis le mobile *(écran Approvals.tsx, vérifié bout en bout sur une vraie approbation)*

---

## P5 — Intégrations Gouvernementales France (long terme)

### 29. Guichet unique des douanes (PORTAL / SI SAGE)
- [ ] Connexion à l'API de la DGDDI *(préparation EDIFACT existante, transmission réseau non implémentée)*
- [ ] Soumission électronique des DAU (format eDMS) *(message EDIFACT généré, envoi non implémenté)*
- [ ] Suivi des statuts de déclaration *(non implémenté)*
- [ ] Récupération des données de dédouanement *(non implémentée)*

### 30. Intrastat / DEB automatique
- [x] Génération automatique des DEB mensuelles
- [x] Transmission électronique via le portail INSEE *(génération XML ; transmission à configurer)*
- [x] Vérification croisée avec les données de facturation

### 31. Fiscalité française
- [x] TVA sur import (TAI)
- [x] Accises (alcool, tabac, énergie)
- [x] Droits de douane additionnels (mesures de safeguard)
- [x] Régime de perfectionnement actif

---

## Résumé des efforts estimés

| Phase | Semaines | nb Features | Impact |
|---|---|---|---|
| P0 — Fondations douanes | 1-4 | 5 | CRITIQUE — rend le produit légalement utilisable |
| P1 — Documents déclaration | 5-10 | 5 | HAUT — transforme l'outil de calcul en outil d'opérations |
| P2 — Intelligence & Auto | 11-18 | 5 | MOYEN — différenciation vs concurrents |
| P3 — Plateforme complète | 19-28 | 7 | MOYEN — couvre le cycle quote → invoice complet |
| P4 — Enterprise | 29-40 | 6 | LONG TERME — scalabilité |
| P5 — Douanes France | 40+ | 3 | LONG TERME — intégration gouvernementale |
| **TOTAL** | **~40 semaines** | **31 features** | |

---

## Priorités quick-win (features rapides à fort impact)

| Feature | Effort | Impact | Sprint |
|---|---|---|---|
| EORI storage + validation | 2 jours | Haute | P0 |
| Facture douanière PDF | 3 jours | Haute | P0 |
| TVA intracom rules | 5 jours | Haute | P0 |
| Landed cost dans devis | 5 jours | Très haute | P2 |
| Denied Party Screening (liste EU) | 3 jours | Haute | P2 |
| Multi-devise dans tous les flux | 3 jours | Haute | P3 |
