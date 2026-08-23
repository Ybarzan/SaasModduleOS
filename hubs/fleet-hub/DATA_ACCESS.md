# Fleet Hub — Process serveur, stockage & accès aux données

> Documentation de référence écrite le 2026-08-17. Elle décrit l'architecture
> technique du projet telle qu'observée dans `docker-compose.yml`,
> `backend/src/main/resources/application.yml`, `V1__baseline.sql` et la
> config Spring Security.

---

## 1. Architecture générale

Le projet repose sur **5 conteneurs Docker** orchestrés par `docker-compose.yml` :

| Service        | Rôle                                                    | Port exposé        |
|----------------|---------------------------------------------------------|--------------------|
| `caddy`        | Reverse-proxy + HTTPS automatique (Let's Encrypt)       | 80, 443            |
| `frontend`     | App React statique servie par Nginx                     | interne            |
| `backend`      | API Spring Boot (Java 21)                               | 8090 (interne)     |
| `db`           | PostgreSQL 16 — toutes les données métier               | interne            |
| `backup`       | Dump `pg_dump` périodique + push rclone optionnel       | -                  |

**Flux d'une requête utilisateur :**

```
Navigateur → Caddy (HTTPS) → Frontend (Nginx, fichiers React)
                           ↘ /api/* → Backend (Spring Boot) → PostgreSQL
```

Caddy termine le TLS, sert le frontend sur `/` et proxifie tout ce qui
commence par `/api/` vers le backend. Les utilisateurs ne touchent jamais
directement la base.

---

## 2. Authentification (process de connexion)

Fichiers de référence : `backend/src/main/java/com/fleethub/config/SecurityConfig.java`
et `.../security/JwtService.java`.

- **Pas de session serveur** : `SessionCreationPolicy.STATELESS` (JWT pur).
- **Mots de passe** hashés en BDD via **BCrypt** (`PasswordEncoder`).
- **Filtres Spring** exécutés à chaque requête, dans cet ordre :
  1. `LoginRateLimitFilter` — anti brute-force (10 tentatives / min / IP par défaut)
  2. `JwtAuthFilter` — décode le token, charge le `UserDetails`
  3. `TenantFilter` — place la `Company` du user dans un `ThreadLocal` (`TenantContext`)
  4. `SubscriptionGuardFilter` — bloque si la société est suspendue / essai expiré (HTTP 402)
- **JWT signé HMAC-SHA** avec `JWT_SECRET` (variable d'env). Claims :
  - `subject` = username
  - `role` = `ADMIN` / `GESTIONNAIRE` / `SAAS_ADMIN`
  - `companyId` = ID de la société (clé de l'**isolation multi-tenant**)
- **Rôles & permissions** (définis dans `SecurityConfig`) :
  - `SAAS_ADMIN` → `/api/admin/**` (back-office plateforme)
  - `ADMIN` → gestion users, intégrations
  - `GESTIONNAIRE` → lecture seule sur KPIs / camions / chauffeurs
  - `permitAll` : `/api/auth/**`, `/api/webhooks/**`, `/actuator/health`, mentions légales

---

## 3. Stockage des données utilisateur

### 3.1 Base de données — PostgreSQL

- **Dev** (profil par défaut) : H2 en mémoire
  (`jdbc:h2:mem:fleethub`, recréée à chaque redémarrage via
  `ddl-auto: create-drop`, pas de Flyway).
- **Prod** (profil `prod`, activé dans `docker-compose.yml`) :

  ```yaml
  url: jdbc:postgresql://db:5432/fleethub
  flyway.enabled: true        # migrations versionnées (V1__baseline.sql, V2__, …)
  hibernate.ddl-auto: validate
  ```

  Les données vivent dans un **volume Docker nommé `pgdata`** : elles
  survivent aux redémarrages du conteneur mais **pas** à la suppression
  du volume.

- **Modèle multi-tenant** (cf. `V1__baseline.sql`) : presque toutes les
  tables ont une colonne `company_id` (FK → `company.id`). C'est le pivot
  d'isolation. Les requêtes JPA sont filtrées via le `companyId` du
  `TenantContext` pour qu'un user d'une société ne voie jamais les
  données d'une autre.

- **Tables principales** (14 entités métier) :
  - `company` (société cliente, plan, statut, facturation Stripe)
  - `app_user` (users avec role + FK company)
  - `driver`, `truck`, `assignment` (couple Chauffeur × Camion)
  - `trip`, `fuel_record`, `cost_record`, `maintenance_record`
  - `driving_event`, `tachograph_day` (données tachygraphe)
  - `notification`, `notification_rule`
  - `integration_config` (clés API fournisseurs **chiffrées AES-GCM** avec
    `INTEGRATION_SECRET_KEY`, voir `config/ApiKeyCrypto.java`)
  - `audit_log` (journal RGPD : qui a fait quoi, quand, IP)

### 3.2 Fichiers uploadés — aucun pour l'instant

Recherche `MultipartFile`, `transferTo`, `uploadDir`, S3, MinIO : **rien**.
L'app ne stocke pas de fichiers utilisateurs. Toutes les données sont
**structurées en BDD**. Si un upload est ajouté, il faudra choisir un
emplacement (volume Docker `./uploads/` ou S3).

### 3.3 Backups

Conteneur `backup` :

- Dump `pg_dump` au démarrage puis toutes les `BACKUP_INTERVAL_HOURS` (défaut 24h)
- Écrit dans `./backups/` (bind mount sur l'hôte — **survit aux conteneurs, pas au VPS**)
- Rotation : `BACKUP_KEEP_DAYS` (défaut 7)
- Copie hors-site optionnelle via **rclone** (`RCLONE_REMOTE` + `RCLONE_CONFIG_BASE64` → ex. S3)
- Ping de supervision optionnel (`BACKUP_PING_URL` → healthchecks.io)

### 3.4 Secrets

Tous dans `.env` (jamais commité, listé dans `.gitignore`) :

- `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_PASSWORD`, `GESTIONNAIRE_PASSWORD`
- `INTEGRATION_SECRET_KEY` (chiffrement AES des clés fournisseurs)
- `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` (facturation)

Ils sont injectés dans les conteneurs comme variables d'environnement
Spring (`${...:?}` ⇒ le conteneur refuse de démarrer si la variable
manque en prod).

---

## 4. Parcours complet d'une action utilisateur (ex. : créer un chauffeur)

1. **React** envoie `POST /api/drivers` avec le JWT dans le header
   `Authorization: Bearer ...`
2. **Caddy** reçoit en HTTPS → proxy vers le backend
3. **LoginRateLimitFilter** → OK
4. **JwtAuthFilter** décode le token, charge l'utilisateur, vérifie le rôle (`ADMIN` requis)
5. **TenantFilter** lit `companyId` du JWT, charge la `Company` en BDD, la met dans `TenantContext`
6. **SubscriptionGuardFilter** vérifie que la société n'est pas gelée
7. **Controller** `DriverController` reçoit la requête
8. **Service** crée l'entité `Driver` en lui attachant automatiquement
   `company_id` = celui du `TenantContext` (impossible de créer un chauffeur
   pour une autre société)
9. **JPA/Hibernate** fait l'`INSERT` PostgreSQL
10. **Flyway** garantit que le schéma est à jour
11. **AuditLog** enregistre l'action en BDD (qui / quand / IP)
12. Réponse JSON renvoyée à React → mise à jour de l'UI

---

## 5. Visualiser les données PostgreSQL

> Tes conteneurs fleet-hub tournent (cf. `docker ps`) :
> - `fleethub-db` (PostgreSQL 16) → BDD `fleethub`, user `fleethub`
> - **Pas de port exposé** par défaut → il faut entrer dans le conteneur
>   ou exposer temporairement.

### 5.1 Méthode 1 — `docker exec` + `psql` (la plus rapide, CLI)

```bash
# Ouvrir un shell dans le conteneur DB
docker exec -it fleethub-db sh

# Puis dans le conteneur :
psql -U fleethub -d fleethub
```

Commandes utiles dans `psql` :

| Commande                                | Effet                                                  |
|-----------------------------------------|--------------------------------------------------------|
| `\dt`                                   | Liste **toutes les tables** (14 tables métier)         |
| `\d driver`                             | Décrit la structure d'une table (colonnes, FK)         |
| `\du`                                   | Liste les utilisateurs DB                              |
| `\x`                                    | Mode affichage étendu                                  |
| `\q`                                    | Quitter psql                                           |
| `SELECT * FROM company;`                | Toutes les sociétés                                    |
| `SELECT COUNT(*) FROM driver;`          | Nombre de chauffeurs                                   |

Exemples concrets pour fleet-hub :

```sql
-- Toutes les sociétés inscrites
SELECT id, name, plan, status, trial_ends_at FROM company;

-- Tous les users avec leur société
SELECT u.username, u.role, c.name AS societe
FROM app_user u
LEFT JOIN company c ON c.id = u.company_id
ORDER BY c.name, u.role;

-- Chauffeurs d'une société précise
SELECT d.first_name, d.last_name, d.license_number, c.name AS societe
FROM driver d
JOIN company c ON c.id = d.company_id
ORDER BY c.name, d.last_name;

-- Coût total au km par société
SELECT c.name, SUM(t.distance_km) AS km, SUM(cr.amount) AS cout
FROM company c
LEFT JOIN trip t ON t.company_id = c.id
LEFT JOIN cost_record cr ON cr.company_id = c.id
GROUP BY c.name;

-- Journal d'audit (RGPD)
SELECT created_at, username, action, detail
FROM audit_log
ORDER BY created_at DESC
LIMIT 50;
```

> Astuce Windows : depuis PowerShell, `docker exec -it fleethub-db sh` fonctionne
> de la même façon.

### 5.2 Méthode 2 — Client graphique (DBeaver / pgAdmin)

Pour une interface visuelle (tables, navigation, filtres, ERD).

**Étape 1 — Exposer le port 5432 temporairement** dans `docker-compose.yml`
(section `db:`) :

```yaml
ports:
  - "5432:5432"   # ⚠️ TEMPORAIRE — retirer après usage
```

Puis :

```bash
docker compose up -d db
```

**Étape 2 — Connexion au client** :

- **DBeaver** (gratuit) : https://dbeaver.io
- **pgAdmin** : https://www.pgadmin.org
- **TablePlus** : https://tableplus.com

Paramètres (valeurs à lire dans `.env`) :

```
Host     : localhost
Port     : 5432
Database : fleethub
User     : fleethub
Password : <DB_PASSWORD du .env>
```

⚠️ **Retirer l'exposition du port après usage** pour éviter un accès non
souhaité depuis Internet.

### 5.3 Méthode 3 — Via l'API backend (sans toucher à la BDD)

Voie applicative : tu te connectes à l'API REST comme un user normal, et
tu lis ce que ton rôle te donne. Le backend filtre déjà par `companyId`
(isolation multi-tenant).

```bash
# 1. Login → récupère un JWT
curl -X POST http://localhost:8880/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<ADMIN_PASSWORD>"}'
# → {"token":"eyJ..."}

# 2. Utilise le token pour interroger
curl http://localhost:8880/api/drivers \
  -H "Authorization: Bearer eyJ..."
```

> Note : le `caddy` du projet mappe 80 → 8880 et 443 → 8443 sur l'hôte
> (cf. `docker ps`). Adapte les URLs.

Swagger UI est disponible sur `http://localhost:8880/swagger-ui.html` (en
profil dev).

Une page **« Mes données »** (rôle ADMIN) exporte **toutes** les données
de la société en JSON (portabilité RGPD).

### 5.4 Méthode 4 — Restaurer un dump pour inspection (backups)

Des dumps existent déjà dans `./backups/`.

```bash
# Créer un conteneur Postgres jetable
docker run --rm -d --name pg-inspect \
  -e POSTGRES_USER=fleethub \
  -e POSTGRES_PASSWORD=inspect \
  -e POSTGRES_DB=fleethub \
  -p 5433:5432 \
  postgres:16-alpine

# Attendre qu'il soit prêt
until docker exec pg-inspect pg_isready -U fleethub; do sleep 1; done

# Restaurer un dump
gunzip -c ./backups/fleethub-YYYYMMDD-HHMMSS.sql.gz \
  | docker exec -i pg-inspect psql -U fleethub -d fleethub

# Se connecter à localhost:5433 avec DBeaver

# Nettoyer
docker rm -f pg-inspect
```

### 5.5 Quelle méthode choisir ?

| Besoin                                                | Méthode                  |
|-------------------------------------------------------|--------------------------|
| Vérification rapide d'une valeur                      | 1 — `psql` via `docker exec` |
| Exploration visuelle, export CSV, schéma ER           | 2 — DBeaver / pgAdmin    |
| Données métier via UI                                 | 3 — API + Swagger ou page « Mes données » |
| Analyser un ancien backup hors-ligne                  | 4 — Dump dans conteneur jetable |
| Contrôle RGPD / portabilité                           | 3 — endpoint `/api/rgpd/export` |

---

## 6. Bonnes pratiques de sécurité

- **Ne jamais** laisser `ports: "5432:5432"` dans `docker-compose.yml` en
  permanence. Limite l'accès réseau au conteneur DB.
- **Ne jamais** committer le mot de passe `DB_PASSWORD` (déjà OK, `.env`
  est dans `.gitignore`).
- **Ne pas** faire de `DROP` / `UPDATE` / `DELETE` directs en prod →
  passer par l'API ou un script de migration versionné (Flyway `V2__...`).
- **Sauvegarder** avant toute modif manuelle :

  ```bash
  docker exec fleethub-db pg_dump -U fleethub fleethub > backup_$(date +%Y%m%d).sql
  ```

---

## 7. Résumé en une phrase

> Le backend Spring Boot stateless sert une API JWT-only, isole les
> données par société via un `ThreadLocal TenantContext` + `company_id`
> systématique, persiste tout en PostgreSQL via JPA + Flyway, est
> sauvegardé par un conteneur dédié qui pousse les dumps sur S3, et
> n'héberge aucun fichier utilisateur (tout est en BDD structurée).
