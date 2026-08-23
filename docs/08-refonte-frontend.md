# Refonte frontend — scope réaliste

## Pourquoi ce document existe

La demande initiale est un frontend « intuitif et évolué » par rapport à l'existant. C'est un objectif légitime, mais la surface réelle est grande : **compliance-engine a ~88 pages fonctionnelles** (audit frontend antérieur, couverture de tests à 100% sur cette base — voir mémoire projet) et **fleet-hub en a 21**. Reconstruire ~109 pages avec un nouveau design en une seule fois n'est ni réaliste ni souhaitable dans un seul chantier — le risque de régression fonctionnelle sur un produit déjà vendu à des clients réels dépasse la valeur d'un lifting visuel big-bang. Ce document découpe le travail en étapes livrables séparément.

## Constat sur l'existant

- **compliance-engine** a une identité visuelle déjà posée et cohérente : thème marocain (palette zellige/sable/olive/medina/terracotta), Tailwind CSS, composants custom. Un audit antérieur notait cette identité comme un point fort (7/10) mais relevait aussi des lacunes d'exécution : zéro lazy loading historique (en cours de résorption), pages monolithiques 800+ lignes, accessibilité faible (pas de focus traps, aria manquant).
- **fleet-hub** a sa propre base React 18 + Vite + Recharts + Leaflet, sans lien de design avec compliance-engine — les deux produits ont aujourd'hui des identités visuelles indépendantes.
- « Évolué » doit donc répondre à deux besoins distincts : (a) élever la qualité d'exécution de l'identité existante de compliance-engine plutôt que la jeter, et (b) donner aux deux hubs une identité visuelle **partagée** pour qu'ils se sentent comme une seule plateforme (« Praxio ») plutôt que deux apps recollées.

## Approche recommandée : design system d'abord, migration progressive ensuite

### Étape 0 — Design system unifié (2-3 semaines)
Un système de design partagé (tokens couleur/typo/espacement, composants de base — bouton, carte, tableau, modal, état vide) qui **évolue** l'identité zellige/terracotta existante plutôt que de la remplacer par quelque chose de générique. Livrable concret : une librairie de composants React partagée entre les deux hubs (`packages/ui-shared` si le monorepo passe en workspace npm), + une maquette du nouveau shell applicatif (navigation, dashboard d'accueil) validée avant tout code.

C'est l'étape où faire appel à un outil de design dédié (maquettes comparatives, direction artistique) a le plus de valeur — recommandé de la traiter comme un livrable à part, pas noyée dans ce document stratégique.

### Étape 1 — Nouveau shell + pages à plus fort impact (3-4 semaines)
Appliquer le nouveau design system à la coquille applicative (navigation unifiée entre compliance-engine et fleet-hub, dashboard d'accueil, palette de commandes déjà existante côté compliance-engine à étendre aux deux hubs) et aux 5-8 pages les plus visibles (dashboard principal, tracking, ETA, création d'expédition côté compliance-engine ; dashboard flotte, carte GPS côté fleet-hub). C'est ce qui donne la sensation immédiate d'un produit « évolué » à un client qui se connecte, sans toucher aux ~100 pages restantes.

**Coquille compliance-engine (nav + header) faite, 2026-08-23** : tokens couleur/police (commit `544562f`) puis détails structurels — coins carrés, marqueur `>` sur l'item actif, préfixe `::` sur le titre de page, wordmark en contraste de poids (commit `d61d04e`). Reste : le dashboard principal et les 4-7 autres pages à fort impact listées ci-dessus, plus l'équivalent côté fleet-hub (rien fait pour l'instant sur ce hub).

### Étape 2 — Migration progressive du reste (continue, priorisée par usage réel)
Le reste des pages migre au fil de l'eau, priorisé par fréquence d'usage réelle (à instrumenter si ce n'est pas déjà mesuré) plutôt que dans un ordre arbitraire — une page de configuration ouverte une fois par mois n'a pas la même urgence qu'une page de tracking ouverte quotidiennement. Chaque page migrée est une passe (nouveau design + extraction des composants dupliqués déjà identifiés dans un audit antérieur : `STATUS_CONFIG`, `COUNTRIES`, `formatMAD`) — pas une réécriture fonctionnelle : le comportement ne change pas, seulement l'habillage et la structure.

## Ce que ce document ne fait pas

- Il ne propose pas de maquettes ou de direction artistique concrète — c'est le contenu de l'Étape 0, à produire comme livrable dédié (outil de design, itération visuelle) plutôt que décrit en prose ici.
- Il ne fixe pas de délai ferme pour l'Étape 2 — volontairement, puisque son rythme dépend de la priorisation par usage réel, pas d'un calendrier arbitraire.

## Lien avec le plan de migration technique

L'Étape 0 (design system) peut démarrer en parallèle de la Phase 1 technique ([03-plan-migration.md](03-plan-migration.md)) — ce sont des chantiers indépendants (l'un est visibilité des données, l'autre est présentation). L'Étape 1 (shell + pages à fort impact) gagne à intégrer les nouveaux éléments de la Couche 1 (badge `LIVE`/`SIMULATED`) et de la Couche 2 (détail du score de confiance par source) dès leur sortie de Phase 1/2, plutôt que de redessiner une page puis devoir la retoucher pour ces nouveaux éléments quelques semaines plus tard.
