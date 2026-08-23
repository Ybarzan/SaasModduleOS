# IncoKalk — Feuille de route d'audit 90 jours (intégrée)

> Créé le : 04/08/2026
> Source : audit en 3 casquettes (Dev / Supply Chain / Marketing) + recroisement avec le diagnostic existant.
> Canonique : `IncoKalk/ROADMAP.md` (manques produit) — ce document ajoute la **stratégie d'exécution** (ordre, kills, décisions).

---

## 1. Verdict synthétique

| Casquette | Verdict | Priorité |
|---|---|---|
| **Dev** | Code globalement sain, tests solides (933 backend / 112 frontend). 3 P0 sécurité signalées → 2 faux positifs, 1 vrai (corrigé le 04/08). | Correctifs vite |
| **Supply Chain** | 28/31 features présentes. Ce qui manque réellement = la **donnée** (TARIC réel, VIES en ligne, EORI en ligne) et l'**app mobile**. | Concurrence |
| **Marketing** | Bien positionné sur 6 axes (26 mentionnés). Points faibles : aucun pricing transparent, docs/offboarding peu finis, aucune preuve sociale. | Différenciation |

---

## 2. P0 Sécurité (traités le 04/08/2026)

| # | Alerte | Diagnostic | Action |
|---|---|---|---|
| 1 | `bdkey.env` toujours sur disque | **Faux positif** — non tracké (`.gitignore` couvre `*key.env`) | Rien à faire |
| 2 | `infrastructure/docker/.env` avec `JWT_SECRET` en clair tracké | **Faux positif** — non tracké (`.gitignore` couvre `.env`) | Rotation locale effectuée quand même |
| 3 | `pom.xml` hardcodait `JWT_SECRET` + `API_KEY_SALT` | **Vrai** — ligne 263-264 `<environmentVariables>` | Retiré du pom ; Stripe vars → `${env.X}` |

Renforcement ajouté :
- `SecurityPropertiesValidator` rejette désormais les secrets dev connus hors profil `dev` (quick win #7).
- `Dockerfile` frontend : `USER nginx` non-root (quick win #9).
- Secrets locaux rotés (`JWT_SECRET` 64 chars base64, `API_KEY_SALT` 7 chiffres) dans `infrastructure/docker/.env` (fichier non tracké).

---

## 3. Quick wins repo

| # | Action | Effort | Statut |
|---|---|---|---|
| 1 | Supprimer `IncoKalk-Dashboard/` (dossier vide) | 15 min | ✅ 04/08 |
| 2 | Fusionner `backoffice/` + `RoadMapCARGO/` dans `IncoKalk/docs/internal/` | 30 min | ✅ 04/08 |
| 3 | Supprimer hooks `.github/java-upgrade/` + `.github/modernize/` (non trackés) | 15 min | ✅ 04/08 |
| 4 | Nettoyer `*.log` racine + durcir `.gitignore` (docker `.env`) | 5 min | ✅ 04/08 |
| 5 | Supprimer les HTML mocks `backoffice/*.html` | 5 min | ✅ 04/08 |
| 6 | Fail-fast `JWT_SECRET` dans `application-prod.yml` | 10 min | ✅ (via Validator) |
| 7 | Refuser les secrets dev connus en prod | 1 h | ✅ 04/08 |
| 8 | `USER nginx` dans le Dockerfile frontend | 1 h | ✅ 04/08 |
| 9 | Générer `CONTRIBUTING.md` + schéma mermaid | 2-3 h | ⏳ à faire |
| 10 | Centraliser les strings FR dans `i18n/fr.json` | 4-6 h | ⏳ plus tard |

---

## 4. Roadmap 90 jours

### Phase 1 — Semaine 1 : Récupération produit (sécurité du travail)
1. ✅ Commit + PR des **76 fichiers non commités** (3 features : Fintech V50, Cargo Insurance V51, Groupage V52).
2. ⏳ Nettoyage quick wins (cf. §3).
3. ⏳ Mise à jour `PROGRESS.md` + ce document en tant que référence unique.

### Phase 2 — Semaines 2-6 : Fins de fondations (Supply Chain)
| Feature | Effort | Note |
|---|---|---|
| TARIC sync réelle (API DGDDI/EUR-Lex) + cache Redis TTL 24h | 2-3 sem | ✅ 04/08 : CacheConfig Redis TTL 24h pour `taric-rates`/`taric-hs-descriptions` |
| Validation **VIES en ligne** (check EU) + cache TTL | 2-3 j | ✅ 04/08 : `ViesClient` REST (ec.europa.eu/vies/rest-api) + cache `vies-check` TTL 24h, flag `VIES_ONLINE_VALIDATION` |
| Vérification **EORI en ligne** (portail EU) | 2-3 j | ✅ 04/08 : `EoriOnlineService` SOAP (EOS validation) + cache `eori-check`, flag `EORI_ONLINE_VALIDATION` |
| Emails : reset password + invitation d'équipe | 3-5 j | ✅ 04/08 : `EmailService` (HTML brandé, lien reset 1h, identifiants temporaires), URLs via `FRONTEND_URL` |
| Cumul préférentiel (bilatéral + régional ASEAN...) | 2-3 sem | ✅ 09/08 : `CumulationService` (bilatéral + diagonal) + `cumulation_groups` (V53) + endpoints `verify-cumulation` |
| App mobile native (React Native/Expo) — décision | 3-5 sem | ⏳ NO-GO → PWA (voir §6) |

### Phase 3 — Semaines 7-9 : Différenciation (Marketing)
| Feature | Effort |
|---|---|
| Page pricing public (mise en avant du calculateur de landed cost) | 1-2 sem |
| Landing SEO par niche (douane FR, incoterms, entrepôt) | 2 sem |
| Proof points : témoignages, cas clients, démo vidéo 2 min | 1-2 sem |
| Offboarding UX + export de données | 1 sem |

### Phase 4 — Semaines 10-13 : Robustesse & scale
| Feature | Effort | Note |
|---|---|---|
| Couverture JaCoCo cible ≥ 60% branches (hardening) | continu | ✅ 11/08 : 36,6% → 60,5% des branches (2026 → 3353/5544), 1833 tests backend (0 failure). ~750 nouveaux tests unitaires/MockMvc sur les classes à 0% (adaptateurs ERP/transporteur/tracking, contrôleurs douane/warehouse/shared, services compliance/ML). 3 bugs latents (NPE) découverts au passage, documentés mais non corrigés (hors périmètre) : `QuoteService.parseMode`, `PushNotificationService.emitToUser`, `SharedLinkController.createLink`. |
| Rate limiting + quotas API | 1 sem | ✅ 11/08 : le rate limiting Bucket4j existait déjà mais en mémoire locale par instance (inopérant à plusieurs instances) ; `RateLimitRedisConfig` + `ProxyManager<byte[]>` Lettuce-based le rend distribué en profil prod (Redis), avec repli transparent sur la map locale en dev/local/test. |
| Obsolescence `@Scheduled` / jobs → queue Redis | 1-2 sem | ✅ 11/08 (scope ajusté) : le vrai risque identifié était l'exécution en double des 7 jobs `@Scheduled` dès que le backend tourne sur plusieurs instances, pas l'absence de queue. `DistributedJobLock` (verrou à bail Redis SET NX PX) empêche les doublons ; pas de queue de tâches à proprement parler (aucun cas d'usage ne le justifiait — les jobs restent des exécutions périodiques, pas des files de travaux individuels). |
| Docs produit publiques (help center) | 2 sem | ⏳ à faire |

---

## 5. Liste "kill" (on arrête de maintenir)
- ✂️ 3 roadmaps parallèles (`ROADMAP.md` canonique + `docs/internal/` archive). **Une seule référence.**
- ✂️ HTML mocks dans `backoffice/` (la vraie UI est dans `frontend/`).
- ✂️ Artefacts de session outil (`.github/java-upgrade`, `.github/modernize`).
- ✂️ Dossiers vides / logs racine.

## 6. Décisions GO/NO-GO à trancher
1. **TARIC réelle** : GO — fondation du produit, sans elle le droit de douane reste estimatif.
2. **Mobile native** : NO-GO pour le moment → **PWA d'abord** (le PWA installable existe déjà), réévaluer à 6 mois si demande.
3. **Page pricing public** : GO avec les 2 premiers profils tarifaires (Dev/Équipe) + câblage Stripe check-out.
4. **Emails transactionnels** : GO dès la Phase 2 — blocant pour l'onboarding B2B.

## 7. Croisement avec le diagnostic initial (04/08)
- Diagnostiqué avant l'audit : 76 fichiers non commités, features partielles, TODOs emails/Stripe/VIES/EORI, i18n FR-only → **confirmé et priorisé** par l'audit.
- L'audit a **corrigé** le diagnostic : bdkey.env et docker `.env` ne sont pas des fuites réelles (gitignore OK).
- Le reste du diagnostic (split des pages monolithiques `DeclarationsPage.tsx` 1833 lignes, i18n, dette) reste valide mais passe **après** les Phases 2-4.

## 8. Principes d'exécution
- 1 PR par feature, tests avant merge (933 backend / 112 frontend restent verts).
- La **donnée réelle** (TARIC/VIES/EORI) prime sur le chrome (docs, animation, i18n).
- Les quick wins se font dans le même commit que le grand commit de sauvegarde pour éviter 2 grosses PRs.  🎯 Audit 3-en-1 — Qzrbzr / IncoKalk

▎ Casquettes actives : Dev Senior Full-Stack · Ingénieur Supply Chain · Directeur Marketing
▎ Date : 04/08/2026 · Sources : lecture intégrale README, PROGRESS, AuditInkocalk, Flextension, ROADMAP, Marketing + 38 889 LOC frontend + 508 .java + 113 .ts/.tsx.

---
0. Diagnostic en une phrase

▎ Vision produit brillante et stack technique moderne, mais un projet « qui a 6 mois de retard sur sa propre roadmap », un repo en désordre physique, une dette de duplication massive, et zéro actif marketing. Pour devenir le meilleur SaaS du secteur, il faut (1) fermer la dette P0 sécurité/tests, (2) trancher un positionnement offensif et (3) bazarder 50% du scope annoncé pour le finir vraiment.

---
1. 👨‍💻 Casquette DEV SENIOR — Verdict & Plan d'attaque

1.1 État du repo (constat physique)

Élément: Structure
Constat: 3 dossiers « projet » à la racine : IncoKalk/, IncoKalk-Dashboard/ (vide, 0 octet),
RoadMapCARGO/ (stratégie) + un backoffice/ (artefacts HTML)
Verdict: 🟠 Fragmentation critique. Le repo se comporte comme 3 sous-projets non-réconciliés.
────────────────────────────────────────
Élément: Dossiers vides
Constat: IncoKalk-Dashboard/ est tockoffice/ contient 2 fichiers HTML
