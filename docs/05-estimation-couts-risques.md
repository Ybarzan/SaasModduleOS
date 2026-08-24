# Estimation d'effort et risques

## Deux lectures de l'effort

Le plan de migration ([03-plan-migration.md](03-plan-migration.md)) est calibré sur la cadence réellement observée sur ce projet (1-2 développeurs en pair-programming avec IA, plusieurs features livrées par jour sur l'historique de commits) : **~21 semaines-calendaires** de Phase 1 à Phase 3 incluse. C'est la lecture honnête pour ce projet précis.

Pour un chiffrage business (levée, budget, comparaison avec un cabinet externe), la conversion en semaines-personnes classiques est utile mais ne doit pas remplacer la première lecture :

| Phase | Effort (semaines-personnes, équipe classique 2-3 devs) | Effort réel observé (cadence actuelle) |
|---|---|---|
| Phase 1 — Visibilité | 10-14 semaines-personnes | 6 semaines-calendaires |
| Phase 2 — ETA scoring | 8-10 semaines-personnes | 5 semaines-calendaires |
| Phase 3 — Orchestrateur | 20-28 semaines-personnes | 10-13 semaines-calendaires |
| **Total** | **38-52 semaines-personnes** | **21-24 semaines-calendaires** |

L'écart entre les deux colonnes n'est pas une erreur d'arrondi — il reflète un rythme de développement déjà 2 à 3x plus rapide que la norme du secteur sur ce projet (voir l'historique : 30/31 fonctionnalités du roadmap douanier livrées, 59 migrations, ~2000 tests backend en quelques semaines). **Le risque principal n'est donc pas la vélocité, il est ailleurs** — voir ci-dessous.

## Composition d'équipe recommandée si renfort

Si l'objectif est d'accélérer au-delà de la cadence solo actuelle (ex. pour un lancement commercial à date fixe) :
- **1 développeur backend supplémentaire** — prioritairement sur la Phase 3 (moteur de règles + exécuteur d'actions), la plus lourde et la plus risquée en conception
- **Pas de data scientist dédié à temps plein** — l'infrastructure ML existe déjà (`EtaMlClient`, microservice Python externe) ; un renfort ponctuel (freelance/mission courte) suffit pour la Phase 2, calibrer les poids de fiabilité par source
- **Un profil produit/UX**, même partiel, dès la Phase 3 J9-J10 (interface no-code) — c'est le composant où une mauvaise UX de validation des règles générées par IA peut saborder la confiance utilisateur plus vite qu'un bug backend

---

## Risques, par ordre d'impact réel

### R1 — Confiance utilisateur envers l'autonomie (risque produit, pas technique)
Le premier incident où l'orchestrateur déclenche une action indésirable (mauvaise commande fournisseur, notification erronée à un client) peut détruire la confiance construite sur des mois — bien plus vite qu'elle ne se reconstruit.
**Mitigation** : démarrer chaque nouvelle catégorie d'action en mode « suggestion à valider » (l'action est préparée mais nécessite un clic humain) pendant un palier probatoire, avant de basculer en exécution autonome — décision par client, pas globale. Le cadre de gouvernance (budget max, approbation obligatoire au-delà d'un seuil) doit être bloquant en test réel, pas seulement déclaratif dans un formulaire.

### R2 — Dette de simulation héritée
Plusieurs adaptateurs transporteurs basculent silencieusement en données simulées si la clé API n'est pas configurée (`DHLAdapter.simulateBooking`). Positionner le produit comme « source unique de vérité » sans avoir corrigé ce point expose à un décalage entre la promesse marketing et la réalité technique pour les clients qui n'ont pas toutes leurs clés API configurées.
**Mitigation** : le flag `dataSource: LIVE|SIMULATED` de la Phase 1 n'est pas optionnel — c'est un prérequis de crédibilité avant tout discours commercial sur la Couche 1.

### R3 — Volume de données insuffisant pour la pondération par fiabilité (Phase 2)
La table `eta_source_reliability` a besoin d'un historique réel (4-8 semaines minimum) avant que ses poids soient statistiquement significatifs. Un client qui démarre à zéro n'a pas immédiatement un scoring meilleur que l'existant.
**Mitigation** : poids par défaut basés sur des benchmarks sectoriels publics au démarrage, avec bascule progressive vers les poids propres au client au fur et à mesure que les données s'accumulent — communiquer cette montée en fiabilité comme un argument produit plutôt que la cacher.

### R4 — Complexité de gestion d'échec partiel dans l'exécuteur d'actions
Une action coordonnée touche plusieurs systèmes externes (ERP + notification + portail client) — un échec partiel (l'ERP répond, la notification échoue) mal géré crée des incohérences silencieuses plus dangereuses qu'un échec total visible.
**Mitigation** : pattern de compensation par étape (saga) dès le premier cas d'usage de la Phase 3, journal d'audit complet par action, alerting explicite sur tout échec partiel — pas de « au pire on corrigera plus tard », ce composant est celui qui touche le plus directement l'argent et la relation client.

**Résolution, 2026-08-24** : le premier cas d'usage construit (`OrchestrationExecutor`, `SUGGEST_ERP_ORDER_ADJUSTMENT`) ne fait qu'**un seul** appel externe (`ErpProvider.exportOrders`) — pas de coordination multi-système, donc rien à compenser en cas d'échec partiel puisqu'il n'y a pas d'état intermédiaire. Le journal d'audit complet est fait (`executionResult` distinct de `decisionNote`, V66) ; le pattern de saga proprement dit reste délibérément non construit, reporté au jour où un deuxième type d'action coordonnera réellement plusieurs systèmes (ex. ajustement ERP + notification client + réallocation entrepôt) — construire une infrastructure de compensation sans un second cas d'usage réel pour la valider aurait été de la spéculation, pas de la mitigation.

### R5 — Dépendance à un LLM pour la configuration des règles
Une mauvaise traduction langage naturel → règle structurée, non détectée par l'utilisateur lors de la validation, peut activer une règle qui ne fait pas ce que l'utilisateur croit avoir demandé.
**Mitigation** : l'écran de validation doit reformuler la règle en français clair et structuré (pas juste afficher un JSON), montrer un exemple concret simulé sur une expédition récente avant activation (« si cette règle avait existé, voici ce qu'elle aurait fait sur vos 5 dernières expéditions en retard »).

### R6 — Risque de séquencement classique (sous-estimé si on ignore le contexte du projet)
Dans un projet plus jeune, le risque principal serait technique (dette, absence de tests). Ici, ce risque est **déjà largement mitigé** par l'état actuel du code (CI, SpotBugs, ~2000 tests backend, 678 tests frontend) — il ne doit pas être surpondéré dans la priorisation par rapport à R1-R5, qui sont spécifiques à cette refonte.

---

## Ce qui n'est pas un risque de cette refonte

- **La stabilité de l'existant** : la migration est conçue en double écriture (Phase 1) puis bascule progressive, précisément pour ne jamais mettre en péril les fonctionnalités déjà vendues aux clients actuels.
- **Le blocage Stripe** ([01-audit-existant.md](01-audit-existant.md)) : réel, mais indépendant de cette refonte technique — à traiter en parallèle sans le mélanger au chiffrage ci-dessus.
