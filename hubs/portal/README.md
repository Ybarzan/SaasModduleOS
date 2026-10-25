# Praxio (Portail cross-hub)

Tableau de bord d'agrégation cross-hub (compliance-engine, fleet-hub, logistix, finance) : statut consolidé, facturation groupée, relais SSO. Pas de comptes utilisateurs ni JWT — un unique mot de passe admin (Basic Auth) protège l'accès.

## Stack

| Couche | Technologie |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Maven |
| Frontend | React, TypeScript, Vite |
| Base de données | H2 (dev, intégrée) / PostgreSQL (prod) |

## Démarrage rapide

Praxio agrège les 4 autres hubs : ils doivent déjà tourner (chacun leur propre
`docker compose up`, ou en prod leurs vraies URLs publiques) et être
joignables depuis ce conteneur avant de configurer les clés/identifiants
dans les paramètres de Praxio.

### Option A — Docker (tout-en-un)

```bash
cd hubs/portal
cp .env.example .env   # puis renseigner de vraies valeurs + URLs des 4 hubs
docker compose up -d --build
```

- Frontend : http://localhost:5180
- API : http://localhost:8092

### Option B — Dev local (sans Docker)

Backend (H2 mémoire, zéro dépendance externe, port 8092) :

```bash
cd backend
mvn spring-boot:run
```

Frontend (port 5180, à la racine du hub, pas dans un sous-dossier `frontend/`) :

```bash
npm install
npm run dev
```

## Tests

```bash
cd backend && mvn test
npm test
```
