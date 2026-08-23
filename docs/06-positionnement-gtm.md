# Positionnement et stratégie go-to-market

## Le pitch actuel vs le nouveau pitch

**Aujourd'hui** : « IncoKalk calcule vos coûts Incoterms et gère vos déclarations douanières. »
C'est vrai, complet, et ça sonne comme un outil de calcul — pas comme une plateforme stratégique.

**Nouveau pitch** :
> *« Vos systèmes savent déjà qu'une expédition va être en retard. IncoKalk agit avant que ça devienne votre problème — réajuste la commande fournisseur, réalloue le stock, prévient le client, sans que vous ayez à ouvrir cinq écrans différents. »*

Version courte pour une accroche de page d'accueil / pitch en 10 secondes :
> **« Fermer l'écart entre savoir et faire — pour le commerce international. »**

---

## Nom et positionnement de marque

**Recommandation : ne pas remplacer la marque IncoKalk, l'englober.** IncoKalk a une réalité concrète (30/31 fonctionnalités douanières livrées, des clients qui la connaissent déjà pour son expertise Incoterms/TARIC/DGDDI). La jeter pour repartir de zéro sur le nom perdrait cette crédibilité acquise sans raison technique — ça contredirait d'ailleurs le principe même de la refonte (« cohabiter, pas remplacer »), appliqué à sa propre marque.

**Structure recommandée** : un nom de plateforme au-dessus, avec IncoKalk comme moteur de conformité nommé à l'intérieur — à la manière dont une plateforme cloud garde ses briques historiques nommées en interne.

> ⚠️ Le nom « Moddule OS » cité en référence dans la demande initiale semble être la marque d'un produit/acteur existant. L'adopter tel quel pour votre propre plateforme exposerait à un risque de confusion de marque, pas seulement de style — les 3 propositions ci-dessous s'en inspirent conceptuellement sans le reprendre.

### 3 propositions de nom, à valider par une recherche de disponibilité (marque + domaine) avant tout usage public

| Nom | Rationale | Ton |
|---|---|---|
| **Praxio** (recommandé) | Du grec *praxis* — l'action guidée par la connaissance, opposée à la théorie pure. Porte littéralement la promesse « savoir → faire » dans l'étymologie, sans avoir besoin de l'expliquer. Se décline bien : Praxio Visibility, Praxio ETA, Praxio Orchestrate | Sobre, B2B, international |
| **Konexo** | Évoque la connexion entre systèmes (couche 1) sans être générique comme « Connect » | Plus neutre, moins différenciant |
| **Fluenta** | Évoque le flux (supply chain) et la fluidité de l'exécution automatisée | Plus doux, moins « tech pointue » |

**Architecture de marque proposée avec Praxio** :
- **Praxio** — nom de la plateforme (les 3 couches)
- **IncoKalk Compliance Engine, by Praxio** — le moteur douanier/Incoterms existant, renommé en sous-marque plutôt qu'effacé — capitalise sur la reconnaissance déjà acquise
- Slogan : *« Praxio. De la visibilité à l'action, sans changer vos outils. »*

---

## Segmentation tarifaire alignée sur les 3 couches (cohérente avec la modularité de l'architecture)

| Palier | Contenu | Cible |
|---|---|---|
| **Praxio Visibility** | Couche 1 seule — ingestion/normalisation multi-source, source unique de vérité | Remplace l'offre actuelle FREE/PRO — élargit l'audience à des entreprises qui n'ont pas encore besoin d'automatisation |
| **Praxio Predict** | + Couche 2 — ETA avec score de confiance multi-source | Équivalent de l'offre actuelle enrichie — argument de vente : « sachez avant vos clients qu'il y a un problème » |
| **Praxio Orchestrate** | + Couche 3 — orchestration autonome, no-code | Nouvelle offre premium — cible ETI avec volume suffisant pour que l'automatisation ait un ROI mesurable (pas les micro-entreprises) |

Ce palier explicite **résout aussi une tension actuelle** : IncoKalk cible aujourd'hui les PME/micro-entreprises pour la conformité douanière (marché français), mais l'orchestration autonome a plus de valeur perçue pour des entreprises avec un volume d'expéditions suffisant. La segmentation par couche permet de garder la base PME existante sur Visibility/Predict tout en ouvrant un mouvement commercial distinct vers des comptes plus gros sur Orchestrate — sans fragmenter le produit.

---

## Angle concurrentiel

La plupart des acteurs de « visibilité supply chain » (Flexport, project44, FourKites, et les concurrents déjà benchmarkés dans `ROADMAP.md` — CargoWise, GoFreight, Wove) vendent soit de la visibilité pure, soit une plateforme de gestion transport généraliste sans profondeur douanière. **L'angle différenciant réel de Praxio n'est pas l'orchestration en soi — c'est de partir d'un socle de conformité douanière française/EU déjà profond** (TARIC réel, régimes préférentiels, DAU/DEB/ICS2) et d'y ajouter l'orchestration, plutôt que l'inverse. Un concurrent généraliste qui ajoute de la douane française repart de zéro sur un sujet réglementaire dense ; Praxio ajoute de l'orchestration sur un sujet déjà résolu.

**Message concurrentiel** : *« Les plateformes de visibilité vous disent que ça va être en retard. Praxio a déjà réajusté votre commande pendant que vous lisiez la notification — et sait calculer vos droits de douane sur le nouveau plan. »*

---

## Mouvement go-to-market recommandé

1. **Ne pas annoncer Praxio avant que la Phase 1 soit en production** — le risque de sur-promettre une orchestration qui n'existe pas encore sur un marché qui a déjà vu beaucoup de promesses IA non tenues est réel. Positionner d'abord le repositionnement de marque (Visibility/Predict) une fois le flag LIVE/SIMULATED en place, comme une preuve de sérieux.
2. **Pilote Orchestrate en mode fermé** avec 2-3 clients existants volontaires (idéalement des clients déjà utilisateurs des modules transporteur + ERP, pour lesquels le cas d'usage « ajustement commande fournisseur » est immédiatement pertinent), avant toute annonce publique de la Couche 3.
3. **Contenu de preuve plutôt que promesse** : un cas client chiffré (« retard détecté J-5, commande de réassort ajustée automatiquement, rupture évitée ») pèse plus lourd sur ce marché qu'un pitch deck sur l'IA autonome — cohérent avec le calculateur ROI et les pages de preuve déjà construites dans le produit actuel (`docs/internal` mentionne un calculateur ROI HS déjà en place, à réutiliser comme gabarit).
4. **Ne pas migrer les clients existants de force vers un nouveau nom** — double affichage (« IncoKalk, maintenant Praxio ») pendant une période de transition, cohérent avec le principe de cohabitation appliqué au branding autant qu'à l'architecture.
