# Système de design — Praxio

> Révision v0.3 (2026-10-25) : remplace la direction v0.2 ("zellige/sable/olive/medina/terracotta", registre terminal/HUD) décrite dans l'historique de [08-refonte-frontend.md](08-refonte-frontend.md). Retour utilisateur direct sur v0.2 : palette jugée agressive/étouffée, registre trop chargé cognitivement. Déployée sur les 5 hubs (portal, logistix, finance, compliance-engine, fleet-hub) qui se lisent maintenant comme une seule plateforme.

## Thèse visuelle

**Réduire la charge cognitive plutôt que signaler une identité.** Une palette neutre (camel très clair en mode clair, pensé pour ne pas agresser les yeux — pas un gris froid ; neutre sombre en mode sombre) et un seul accent (indigo) utilisé avec parcimonie. Les couleurs sémantiques (succès/alerte/danger) ne servent qu'à porter du statut, jamais en décoration — c'est la règle qui distingue le plus ce système de la dérive "IA générique" bariolée. Hiérarchie typographique claire (taille/poids/contraste guident l'œil), espace blanc généreux, ombres quasi invisibles. Proche de la famille Linear/Stripe/Vercel — référence actuelle en clarté d'interface SaaS.

## Palette

Deux thèmes (bascule clair/sombre sur chaque hub), tokens exprimés en triplets RGB (`--c-bg: 250 246 238` etc. — permet `rgb(var(--c-bg) / <alpha>)` côté Tailwind) :

| Token | Clair | Sombre | Usage |
|---|---|---|---|
| `bg` | `#FAF6EE` (camel très clair) | `#0A0A0B` | Fond de page |
| `surface` | `#FFFFFF` | `#18181B` | Cartes, panneaux |
| `surface-2` | `#F3EDE1` | `#1F1F23` | Survol, fond secondaire |
| `ink` | `#221C16` | `#FAFAFA` | Texte principal |
| `ink-soft` | `#5D5244` | `#A1A1AA` | Texte secondaire/méta |
| `line` | `#E7DECB` | `#27272A` | Bordures |
| `accent` | `#4F46E5` (indigo) | `#818CF8` | Action, lien, état actif — **le seul accent, utilisé avec parcimonie** |
| `accent-2` | `#0D9488` (teal) | `#2DD4BF` | Secondaire, usages ponctuels (ex. dégradé de logo) |
| `success` | `#16A34A` | `#4ADE80` | Statut uniquement |
| `warning` | `#D97706` | `#FBBF24` | Statut uniquement |
| `danger` | `#DC2626` | `#F87171` | Statut uniquement |

Contraste vérifié WCAG AA sur toutes les paires texte/fond ci-dessus.

## Typographie

**Une seule famille : Inter.** Hiérarchie par taille/poids, pas par changement de registre :
- Titre de page — 28-32px, poids 600
- Titre de section — 18-20px, poids 600
- Corps — 14-16px, poids 400
- Labels/méta — 12-13px, poids 500

Le monospace (JetBrains Mono, conservé) reste réservé à l'affichage de codes/identifiants littéraux (SKU, EORI, clé API) — jamais comme police d'interface généraliste.

## Layout et composants

- **Coins doux** (`rounded-lg`/`rounded-xl`, ou variables `--radius*` à 8-16px côté fleet-hub) — jamais de coins carrés systématiques.
- **Cartes** : fond `surface`, bordure `line` 1px, ombre quasi invisible, léger changement de bordure au survol (pas de lift + ombre lourde).
- **Statut** : badge pilule avec pastille colorée + libellé texte (jamais la couleur seule comme vecteur d'information).
- **Boutons** : `btn-primary` (fond accent plein) / `btn-secondary` (contour) — texte normal, pas de majuscules forcées.
- Pas de décoration "terminal" : ni coins-crochets HUD, ni préfixe `::`/`>`, ni curseur clignotant, ni grille de fond.

## Ce que ce n'est pas

Pas la dérive "IA générique" bariolée (plusieurs couleurs vives sans hiérarchie), mais pas non plus le registre terminal/HUD de la v0.2 (coins carrés, tags bordés, marqueurs `>`/`::`) — ce dernier a été explicitement abandonné sur retour utilisateur direct : jugé agressif pour les yeux et trop chargé cognitivement pour un usage quotidien.

## Implémentation

Les tokens vivent dans `src/index.css` (`:root`/`.dark`) + `tailwind.config.js` de chaque hub Tailwind (portal, logistix, finance, compliance-engine) ; fleet-hub (sans Tailwind) porte les mêmes valeurs dans `src/styles.css`/`src/theme.css` via ses propres variables CSS (`--bg`, `--card`, `--primary`, `--radius`...). Garder ces fichiers synchronisés à chaque évolution de palette.

## Aperçu

Maquette de la direction (palette, tableau de bord type, bascule clair/sombre) : https://claude.ai/artifact/QXVtg1hzVGBcGH3yuGTVTW
