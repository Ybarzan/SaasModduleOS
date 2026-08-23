# Vendre Fleet Hub — stratégie et checklist

Ce document complète `DEPLOY.md` (mise en ligne de la démo) : il couvre le **choix du
canal de vente**, la **tarification** et la **checklist du package** pour une app
SaaS complète (web + mobile) prête à la vente.

## Ce que vous vendez

Fleet Hub est un **SaaS multi-tenant complet** (backend Java/Spring + frontend React +
app native Android/iOS Capacitor) avec :
- tableau de bord KPIs Chauffeur × Camion, carte temps réel, saisie manuelle ;
- **facturation Stripe** (plans, essai 14 j, suspension auto), **RGPD** (export, audit,
  suppression), **gestion des utilisateurs**, **notifications** ;
- **intégrations externes** (tachygraphe/GPS/carburant) self-service avec clés API
  chiffrées, canal de push webhook tenant-scopé ;
- **multi-tenancy** isolé, back-office plateforme, Docker + Caddy HTTPS, CI/CD, 67 tests backend.

C'est un **produit complet** (pas un script). Les canaux adaptés sont donc ceux qui
vendent des apps/SaaS entiers, pas les marketplaces de petits scripts.

## Recommandation de stratégie (par priorité)

| Canal | Pourquoi | Coût | Part revenu |
|---|---|---|---|
| **Sell My Code** (direct ou marketplace) | spécialisé apps complètes/SaaS, vente rapide, prix libres, 100 % du prix sur la marketplace ($49 de mise en ligne) | $49 (marketplace) ou 0 (vente directe) | ~100 % |
| **Flippa** | apps/SaaS entiers, audience acheteurs d'actifs numériques | $29–499 de mise en ligne + ~10 % | ~90 % |
| **AppTrovo / CodeCudos** | SaaS multi-tenant = profil exact, revue 24 h, 90 % de part | 0 | 90 % |
| **CodeCanyon** | grosse audience, mais commissions 37,5–55 %, prix attendus bas ($20–60) | 0 | 45–62 % |
| **Votre site + Stripe / Gumroad** | marges max, mais il faut amener votre propre trafic | ~1 €/mois | ~87–97 % |

> **Suggestion** : visez un canal « apps complètes » (Sell My Code / Flippa / AppTrovo)
> avec votre propre site (Gumroad ou Stripe) en parallèle. CodeCanyon peut servir de
> canal secondaire si vous acceptez une commission élevée et un prix de listing modeste
> (version « démo/white-label »).

## Fourchettes de prix

Le prix dépend du canal et du package. Ordres de grandeur 2026 (sources : marketplaces) :

| Package | Prix indicatif |
|---|---|
| **Licence unique** (1 acheteur, code source complet + docs + install incluse) | **2 500 – 6 000 €** |
| **SaaS plateforme complète** (web + iOS + Android + back-office) | **3 000 – 10 000 €** |
| **Licence étendue / white-label multi-domaine** | **2× la licence unique** |
| **Vente directe à un repreneur** (via Sell My Code/Acquire) | négocié, souvent 5 000 – 25 000 € selon preuve de marche |

Points qui **augmentent** la valeur : démo en ligne fonctionnelle, mobile (iOS+Android),
multi-tenant + facturation, documentation complète, tests, historique git propre.

## Checklist du package de vente

- [ ] **Démo en ligne** (DEPLOY.md) : URL publique HTTPS, comptes démo, données réalistes
- [ ] **Captures d'écran** : desktop + mobile. Génération auto :
      `cd frontend && node scripts/mobile-shots.mjs` (+ captures Android via émulateur)
- [ ] **Vidéo de démo** 2–3 min (login → dashboard → drill-down → carte → saisie →
      inscription d'une société → back-office)
- [ ] **README acheteur** : stack, démarrage local, Docker prod, identifiants, API, tests
- [ ] **DEPLOY.md** pour l'acheteur (déjà fourni)
- [ ] **INTEGRATION.md** : brancher les vraies données (déjà fourni)
- [ ] **Licence / CGV** : modèle (unique / étendue / multi-domaine), garantie, support 30–90 j
- [ ] **Archive du code** : zip complet sans `.env`, sans secrets, sans `target/`, `node_modules/`,
      `build/`, `dist/`
- [ ] **Version taguée** : `git tag v1.0.0 && git push --tags`
- [ ] **README GitHub propre** (déjà bon) + description du dépôt public (vitrine)

## Consignes avant mise en vente

- **Zéro secret** dans le dépôt : vérifier `grep -r "sk_live\|whsec_\|PRIVATE KEY" .`
- **Aucune donnée réelle** dans la démo (le seeder génère des données fictives, parfait).
- **Licences** : le code est à vous (aucun fragment tiers restrictif). Les bibliothèques
  utilisées (Spring, React, Capacitor, etc.) sont open-source compatibles avec la vente
  de code source ; documenter la liste dans le README acheteur.
- **Support** : décider d'une période incluse (ex. 30–90 j) et d'un tarif d'extension.
- **Garantie/refunds** : respecter les règles du canal (ex. 14–30 j selon plateforme).

## Étapes d'action (dans l'ordre)

1. Acheter VPS + domaine (~5 €/mois + ~10 €/an) — voir DEPLOY.md.
2. Déployer la démo (`deploy.sh`), vérifier, générer captures + vidéo.
3. Vérifier la checklist ci-dessus, taguer `v1.0.0`.
4. Créer les comptes sur 1–2 canaux, préparer les listings (nom, description, captures,
   vidéo, prix).
5. Mettre à jour ce document avec les URLs des listings quand elles existent.
