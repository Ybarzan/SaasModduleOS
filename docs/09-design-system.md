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

## Typographie

Trois rôles, pas deux — le rôle "chiffre" est traité à part parce que dans ce produit, **le chiffre est le contenu héros** (ETA en jours, score de confiance en %, coût débarqué) :

- **Chiffres/statistiques** — *Fraunces* (axe optical size poussé haut), réservée exclusivement à l'affichage de grands nombres (ETA, confiance, coûts). Pas utilisée en titre de page — c'est un choix de contenu, pas un choix de style héroïque générique.
- **Titres, navigation, labels** — *IBM Plex Sans*, registre technique/document plutôt que la neutralité par défaut d'un Inter/Roboto.
- **Corps de texte et tableaux** — *IBM Plex Sans* (poids régulier, tailles resserrées pour la densité de données).
- **Codes, identifiants, données tabulaires** (codes HS, numéros de suivi, montants) — *IBM Plex Mono*, `font-variant-numeric: tabular-nums` — référence directe au registre du document officiel (manifeste, connaissement).

## Layout

Composition "manifeste de contrôle" : cartes aux proportions plus larges que hautes avec une double bordure fine évoquant un tampon/sceau officiel (pas de card bubbly à coins très arrondis), rail de navigation traité comme un registre (règles fines, numérotation en IBM Plex Mono), motif géométrique zellige abstrait utilisé uniquement en texture basse opacité sur les zones d'accueil/état vide — jamais sur un écran dense en données, où il ajouterait du bruit plutôt que du sens.

## Ce que ce n'est pas

Explicitement pas la dérive "IA générique" : pas de crème + serif + terracotta seul (le piège le plus commun) — la teinte sarcelle et le traitement "document officiel" (IBM Plex Mono tabulaire, doubles bordures façon tampon) sont les deux éléments qui sortent Praxio de ce registre. Pas de dégradé violet-bleu, pas de grille d'icônes à 3 colonnes, pas de `rounded-lg` partout.

## Aperçu

Maquette complète (palette, spécimens typographiques, composants, aperçu de la nouvelle coquille applicative unifiant compliance-engine et fleet-hub) : https://claude.ai/code/artifact/3bd4f46d-4895-434c-ab3d-476bd56b284b
