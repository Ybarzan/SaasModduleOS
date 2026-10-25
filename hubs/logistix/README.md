# Logistix

Hub Entrepôt / Réception — gestion des entrepôts, réceptions et écarts, avec intégration cross-hub (Praxio, fleet-hub) via clés API.

## Stack

| Couche | Technologie |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Maven |
| Frontend | React, TypeScript, Vite |
| Base de données | H2 (dev, intégrée) / PostgreSQL (prod) |

## Démarrage rapide

### Option A — Docker (tout-en-un)

```bash
cd hubs/logistix
cp .env.example .env   # puis renseigner de vraies valeurs
docker compose up -d --build
```

- Frontend : http://localhost:5181
- API : http://localhost:8091

### Option B — Dev local (sans Docker)

Backend (H2 mémoire, zéro dépendance externe, port 8091) :

```bash
cd backend
mvn spring-boot:run
```

Frontend (port 5181) :

```bash
cd frontend
npm install
npm run dev
```

## Tests

```bash
cd backend && mvn test
cd frontend && npm test
```
