# Système de design — Praxio

> Direction visuelle proposée pour l'Étape 0 de la refonte frontend ([08-refonte-frontend.md](08-refonte-frontend.md)). Fait évoluer l'identité "zellige/sable/olive/medina/terracotta" déjà en place côté compliance-engine plutôt que de la remplacer, et l'étend à fleet-hub pour que les deux hubs se lisent comme une seule plateforme.

## Thèse visuelle

**Praxio a l'air de savoir exactement ce qui se passe — et de s'en occuper déjà.** L'assurance tranquille d'un expert douanier chevronné : chaleureuse et précise dans le détail (héritage zellige, registre du document officiel — manifeste, tampon douanier, connaissement), nette et sans friction dans l'exécution (contrôle temps réel, action automatisée visible comme telle).

Angle de différenciation délibéré : les plateformes de visibilité supply chain (FourKites, project44, Flexport) convergent toutes vers le même bleu corporate froid et générique. Praxio part d'un terrain différent — la chaleur terracotta/sable déjà associée au produit, une teinte sarcelle (zellige) plutôt que le bleu SaaS par défaut, et un vocabulaire visuel emprunté au document officiel (tampon, manifeste, registre) plutôt qu'au tableau de bord tech générique.

## Palette

| Token | Hex | Usage |
|---|---|---|
| `--ink` (medina au crépuscule) | `#211A15` | Fond mode sombre, texte principal en mode clair — jamais un noir pur |
| `--sable` | `#E6E0D3` | Fond mode clair — pierre chaude, pas la crème jaune par défaut |
| `--terracotta` | `#BF4E2E` | Accent principal — action, marque. Utilisé avec parcimonie, jamais en fond dominant |
| `--zellige` (sarcelle) | `#1E6B6E` | Accent secondaire — liens, confiance haute, données. Ce qui distingue Praxio du bleu SaaS générique |
| `--olive` | `#6E7A4A` | État stable / action orchestrée avec succès |
| `--ochre` | `#C08A2E` | Sémantique avertissement |
| `--rust` | `#A6311F` | Sémantique critique — volontairement plus saturé/rouge que `--terracotta` pour rester distinguable |

## Typographie (v0.2 — révisée après retour utilisateur : "simpliste cybernétique")

**Une seule famille : JetBrains Mono.** Pas de duo serif/sans — la hiérarchie vient du poids (200 pour les grands nombres, 400 pour le corps, 700 en majuscules pour les titres) et de l'espacement, pas d'un changement de caractère. Ce choix sert directement le positionnement Couche 3 : un système d'orchestration autonome a plus de sens visuel en registre terminal/HUD ("le système surveille et agit") qu'en registre document officiel — la première version (Fraunces + IBM Plex Sans, référence "manifeste douanier") a été abandonnée pour ça.

- **Chiffres/statistiques** — poids 200 (ultra-léger), grande taille — ETA, score de confiance, coûts. Toujours en `tabular-nums`.
- **Titres** — poids 700, majuscules, espacement de lettres — labels de section, titres de carte.
- **Corps de texte** — poids 400, interligne 1.6-1.7 (le monospace a besoin de plus d'air qu'un sans-serif en paragraphe).
- **Codes, identifiants, données tabulaires** — poids 500, `tabular-nums` — déjà natif au monospace, pas de rupture de registre avec le reste.

Conventions typographiques empruntées au terminal, utilisées avec sens (pas en décoration) : `::` comme séparateur, `>` comme marqueur d'item actif/de commande, curseur clignotant sur le wordmark (respecte `prefers-reduced-motion`), tags bordés `[LIVE]`/`[SIMULÉ]`/`[AGI]` plutôt que des pastilles colorées pleines.

## Layout

Composition "terminal de contrôle" : cartes à coins carrés (zéro `border-radius`) avec des coins-crochets fins façon réticule HUD (au lieu d'une bordure double façon tampon), rail de navigation traité comme une console (règles fines, marqueur `>` sur l'item actif), grille de fond basse opacité utilisée uniquement sur les zones d'accueil/état vide — lisible à la fois comme trame technique et comme écho abstrait du zellige, jamais sur un écran dense en données où elle ajouterait du bruit plutôt que du sens.

## Ce que ce n'est pas

Explicitement pas la dérive "IA générique" : pas de crème + serif + terracotta seul (le piège le plus commun, c'est d'ailleurs la direction v0.1 initialement proposée puis écartée), pas de `rounded-lg` partout, pas de pastilles colorées pleines comme seul vecteur de statut. La teinte sarcelle et le registre terminal (une seule famille mono, tags bordés, marqueurs `>`/`::`) sont ce qui sort Praxio du générique.

## Aperçu

Maquette complète (palette, spécimens typographiques, composants, aperçu de la nouvelle coquille applicative unifiant compliance-engine et fleet-hub) : https://claude.ai/code/artifact/3bd4f46d-4895-434c-ab3d-476bd56b284b
