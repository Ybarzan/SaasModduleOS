# 🚀 Guide de démarrage — IncoKalk Backend

## Trois profils disponibles

| Profil | Base de données | Quand l'utiliser |
|--------|-----------------|------------------|
| `local` | H2 en mémoire | Itération rapide, coder un nouveau contrôleur sans rien installer |
| `dev` | PostgreSQL + Redis + MinIO (Docker) | Travail réel avec migrations Flyway |
| `prod` | PostgreSQL + Redis + MinIO (Kubernetes) | Déploiement |

---

## Option 1 — Démarrage local instantané (H2) ✅ Plus rapide

Aucun pré-requis : juste Java 21 + Maven.

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

ℹ️ Sous PowerShell, il faut des guillemets autour de `-Dspring-boot.run.profiles=local` sinon le `=` est mal interprété.

**Caractéristiques :**
- H2 en mémoire, schéma auto-généré par Hibernate (`create-drop`)
- Flyway désactivé
- Redis désactivé
- Tous les providers externes (Stripe, Mail, Shippo, TARIC, etc.) en mode mock
- Données perdues à chaque redémarrage

---

## Option 2 — Stack de dev réaliste (Docker + ton IDE)

Spring Boot tourne dans ton IDE, mais Postgres/Redis/MinIO tournent dans Docker avec leurs vraies données.

### Un clic et c'est parti

```powershell
.\scripts\dev-start.ps1
```

Ce script :
1. Vérifie que Docker tourne
2. Crée un `.env.dev` s'il n'existe pas
3. Démarre Postgres + Redis + MinIO + pgAdmin
4. Attend que Postgres soit `healthy`
5. Affiche les commandes à lancer dans 2 terminaux

### Dans un autre terminal : le backend

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### Dans un autre terminal : le frontend

```powershell
cd frontend
npm run dev
```

### Services accessibles

| Service    | URL                       | Credentials                              |
|------------|---------------------------|------------------------------------------|
| Backend    | http://localhost:8080/api | —                                        |
| Swagger UI | http://localhost:8080/api/swagger-ui.html | — |
| Postgres   | localhost:5432            | incokalk / incokalk_dev_2026             |
| pgAdmin    | http://localhost:5050     | admin@incokalk.io / admin                |
| Redis      | localhost:6379            | (sans mot de passe)                      |
| MinIO API  | http://localhost:9000     | minioadmin / minioadmin                  |
| MinIO UI   | http://localhost:9001     | minioadmin / minioadmin                  |

### Arrêter la stack

```powershell
.\scripts\dev-stop.ps1
```

---

## Option 3 — Stack complète Docker (production-like)

Inclut le build des images backend + frontend + nginx. Plus lent, mais isole tout.

```powershell
cd infrastructure/docker
docker compose --env-file .env up -d
```

---

## 🔧 Dépannage

### "Connection to localhost:5432 refused"
Tu utilises `mvn spring-boot:run` (profil par défaut) ou `-Dspring-boot.run.profiles=dev` mais Postgres n'est pas démarré. Soit tu lances `.\scripts\dev-start.ps1`, soit tu passes en `-Dspring-boot.run.profiles=local`.

### "JWT_SECRET environment variable is empty"
Pas de souci en `local` (un faux secret y est défini). En `dev`/`prod`, vérifie que `.env.dev` est chargé par ton terminal :
```powershell
Get-Content .\infrastructure\docker\.env.dev
```

### "Port 8080 already in use"
Un autre process l'utilise :
```powershell
netstat -ano | findstr :8080
# puis:  taskkill /PID <numéro> /F
```

### Le profil `local` démarre mais bloque à l'init
C'est souvent un appel externe (TrackingProvider, MinIO ping). Utilise `dev` avec Docker, c'est plus stable.
