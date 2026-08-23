# Plan d'action technique — Fleet Hub

objectif : corriger les manques critiques et la dette technique pour une mise en production solide.

---

## Phase 1 — Critique (bloquant prod) — Semaine 1

### 1.1 Activer Sentry (erreur tracking)

**Fichiers** : `backend/src/main/resources/application-prod.yml`, `frontend/src/main.jsx`
**Effort** : ~1h

- Créer un compte gratuit sur [sentry.io](https://sentry.io) (plan free, 5K events/mois)
- Backend : ajouter dans `application-prod.yml` :
  ```yaml
  sentry:
    dsn: ${SENTRY_DSN_BACKEND}
    environment: prod
    traces-sample-rate: 0.1
  ```
- Ajouter `SENTRY_DSN_BACKEND` et `SENTRY_DSN_FRONTEND` dans `.env.example`
- Frontend : le DSN est déjà passé via `VITE_SENTRY_DSN` — vérifier qu'il est bien transmis
- Tester en envoyant une erreur volontaire

### 1.2 Activer les backups hors site

**Fichiers** : `backup/backup.sh`, `.env.example`
**Effort** : ~2h (config) + test

- Choisir un stockage : Backblaze B2 ( gratuit jusqu'à 10 Go) ou Scaleway Object Storage
- Installer rclone sur le VPS si pas déjà fait : `curl https://rclone.org/install.sh | sudo bash`
- Configurer rclone : `rclone config` → créer un remote nommé `fleet-backup`
- Renseigner dans `.env` :
  ```
  RCLONE_REMOTE=fleet-backup:fleet-hub-backups
  RCLONE_CONFIG_BASE64=<base64 du fichier rclone.conf>
  ```
- Tester une restauration complète :
  ```bash
  backup/backup.sh  # vérifier que le dump apparaît dans le remote
  gunzip < backups/latest.sql.gz | psql -h localhost -U fleethub fleethub  # test restauration
  ```
- Activer `BACKUP_PING_URL` (healthchecks.io, plan gratuit) pour les alertes

### 1.3 Emails transactionnels

**Fichiers** : `backend/src/main/resources/application-prod.yml`, `.env.example`
**Effort** : ~3h

- Créer un compte Brevo (anciennement Sendinblue) — 300 emails/jour gratuits
- Renseigner dans `.env` :
  ```
  MAIL_ENABLED=true
  MAIL_HOST=smtp-relay.brevo.com
  MAIL_PORT=587
  MAIL_USERNAME=<votre@email.com>
  MAIL_PASSWORD=<clé API Brevo>
  MAIL_FROM=noreply@fleet-hub.fr
  ```
- Tester l'envoi d'une invitation réelle
- Vérifier que le mot de passe oublié envoie bien l'email

### 1.4 Revocation JWT (logout distant)

**Fichiers** : à créer — `JwtTokenRepository.java`, modifier `SecurityConfig.java`, `AuthController.java`
**Effort** : ~1 jour

- Créer une table `revoked_tokens` (token_id, expires_at) via Flyway `V2__revoke_tokens.sql`
- Implémenter `JwtTokenRepository` avec :
  - `revoke(tokenId, expiresAt)` → INSERT
  - `isRevoked(tokenId)` → SELECT + nettoyage des tokens expirés
  - `cleanup()` → DELETE WHERE expires_at < NOW() (appelé via `@Scheduled`)
- Modifier `JwtAuthenticationFilter` : après validation du token, vérifier `isRevoked`
- Modifier `POST /auth/logout` : appeler `revoke(tokenId, expiresAt)`
- Ajouter un endpoint `POST /admin/revoke-all/{userId}` pour le SaaS admin
- Tests d'intégration : 4 tests (revoke, isRevoked, cleanup, logout réel)

---

## Phase 2 — Dette technique (important) — Semaine 2-3

### 2.1 Index DB manquants

**Fichiers** : `backend/src/main/resources/db/migration/V2__indexes.sql` (ou ajuster le numéro)
**Effort** : ~2h

Créer une migration Flyway avec les index manquants :

```sql
-- Index sur company_id (filtre multi-tenant sur chaque requête)
CREATE INDEX idx_vehicle_company ON vehicle(company_id);
CREATE INDEX idx_driver_company ON driver(company_id);
CREATE INDEX idx_assignment_company ON assignment(company_id);
CREATE INDEX idx_trip_company_date ON trip(company_id, date);
CREATE INDEX idx_event_company_time ON event(company_id, event_time);
CREATE INDEX idx_tacho_company_period ON tachograph(company_id, period_start);
CREATE INDEX idx_fuel_company_date ON fuel_entry(company_id, entry_date);
CREATE INDEX idx_cost_company_month ON cost_record(company_id, billing_month);
CREATE INDEX idx_maintenance_company_date ON maintenance(company_id, maintenance_date);
CREATE INDEX idx_user_company ON app_user(company_id);

-- Index composites pour les KPI (requêtes fréquentes)
CREATE INDEX idx_trip_driver_date ON trip(driver_id, date);
CREATE INDEX idx_trip_vehicle_date ON trip(vehicle_id, date);
CREATE INDEX idx_event_driver_time ON event(driver_id, event_time);
```

### 2.2 Cache HTTP sur les assets statiques

**Fichiers** : `frontend/nginx.conf`
**Effort** : ~30min

Ajouter dans `nginx.conf` :

```nginx
# Assets versionnés (Vite les fingerprint)
location ~* \.(js|css|woff2|woff|ttf|webp|png|jpg|svg|ico)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}

# HTML — pas de cache (toujours revalider pour le code-splitting)
location ~* \.html$ {
    add_header Cache-Control "no-cache, no-store, must-revalidate";
}
```

### 2.3 Cache Redis pour les KPI

**Fichiers** : `docker-compose.yml` (ajouter service redis), `pom.xml`, `application-prod.yml`
**Effort** : ~1 jour

- Ajouter Redis au `docker-compose.yml` :
  ```yaml
  redis:
    image: redis:7-alpine
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 30s
      timeout: 5s
      retries: 3
    volumes:
      - redis_data:/data
  ```
- Ajouter dans `pom.xml` : `spring-boot-starter-data-redis`
- `application-prod.yml` :
  ```yaml
  spring:
    data:
      redis:
        host: redis
        port: 6379
        timeout: 2s
  ```
- Créer `KpiCacheService.java` :
  ```java
  @Service
  @ConditionalOnProperty("spring.data.redis.host")
  public class KpiCacheService {
      private final StringRedisTemplate redis;
      private final Duration TTL = Duration.ofMinutes(5);

      public Optional<String> get(String key) { ... }
      public void put(String key, String value) { ... }
      public void invalidate(Long companyId) { ... }  // invalidate au flush
  }
  ```
- Modifier `KpiService` : interroger le cache avant le calcul, stocker le résultat
- Invalider le cache lors de la création/suppression de trips/events

### 2.4 Limite de taille de body

**Fichiers** : `backend/src/main/resources/application-prod.yml`
**Effort** : ~15min

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
server:
  tomcat:
    max-http-form-content-size: 2MB
```

### 2.5 E2E dans le CI

**Fichiers** : `.github/workflows/ci.yml`
**Effort** : ~1 jour

Ajouter un job `e2e` :

```yaml
e2e:
  needs: [backend, frontend]
  runs-on: ubuntu-latest
  services:
    postgres:
      image: postgres:16-alpine
      env:
        POSTGRES_DB: fleethub
        POSTGRES_USER: fleethub
        POSTGRES_PASSWORD: test
      ports: ['5432:5432']
      options: >-
        --health-cmd pg_isready
        --health-interval 10s
        --health-timeout 5s
        --health-retries 5
  steps:
    - uses: actions/checkout@v4
    - name: Build & start backend
      run: |
        cd backend
        ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &
        # attendre que le backend soit prêt
        timeout 120 bash -c 'until curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; do sleep 2; done'
    - name: Build frontend
      working-directory: frontend
      run: npm ci && npm run build
    - name: Install Playwright
      run: npx playwright install --with-deps chromium
      working-directory: frontend
    - name: Run E2E tests
      working-directory: frontend
      run: npx playwright test
      env:
        CI: true
```

### 2.6 Scan de vulnérabilités dans le CI

**Fichiers** : `.github/workflows/ci.yml`
**Effort** : ~1h

Ajouter à chaque job existant :

```yaml
    - name: Trivy vulnerability scan (frontend)
      uses: aquasecurity/trivy-action@master
      with:
        scan-type: 'fs'
        scan-ref: 'frontend/'
        severity: 'CRITICAL,HIGH'
        exit-code: '1'  # fail si vuln critique
```

---

## Phase 3 — Sécurité renforcée — Semaine 4

### 3.1 2FA (TOTP)

**Fichiers** : `pom.xml`, `AuthController.java`, `UserController.java`, pages frontend
**Effort** : ~2 jours

- Ajouter `dev.samstevens.totp:totp` dans `pom.xml`
- Nouvelle table Flyway : `user_mfa (user_id, secret, enabled)`
- Backend :
  - `POST /auth/mfa/setup` → générer secret, retourner QR code (URI otpauth://)
  - `POST /auth/mfa/verify` → valider le code TOTP, activer MFA
  - Modifier `POST /auth/login` : si MFA activé → retourner `mfa_required: true` + token temporaire (5 min)
  - `POST /auth/mfa/login` → valider le TOTP, émettre le vrai JWT
- Frontend :
  - Page `/mfa-setup` : afficher QR code + champ de code de vérification
  - Page `/mfa-login` : champ de code TOTP
  - Flag dans le profil utilisateur pour activer/désactiver
- Tests : 6 tests (setup, verify, login with MFA, invalid code, disable)

### 3.2 Rate limiting par route

**Fichiers** : créer `RateLimitFilter.java`, modifier `SecurityConfig.java`
**Effort** : ~4h

- Utiliser Guava `RateLimiter` ou buckets4j pour le rate limiting
- Routes prioritaires :
  - `POST /api/auth/login` : 5/min/IP
  - `POST /api/auth/register` : 3/min/IP
  - `POST /api/auth/forgot-password` : 3/min/email
  - `GET /api/dashboard/**` : 60/min/tenant
  - Autres API : 120/min/tenant
- Headers `X-RateLimit-Remaining`, `Retry-After` en réponse

### 3.3 IP allowlisting pour /admin

**Fichiers** : créer `AdminIpFilter.java`, modifier `SecurityConfig.java`
**Effort** : ~2h

- Variable d'env `ADMIN_ALLOWED_IPS=1.2.3.4,5.6.7.8`
- Filtre qui vérifie `X-Forwarded-For` (derrière Caddy) contre la liste
- Retourner 403 si IP non autorisée sur `/api/admin/**`
- Logger les tentatives bloquées

---

## Phase 4 — Monitoring & Observabilité — Semaine 4-5

### 4.1 Uptime monitoring

- Créer un compte UptimeRobot ( gratuit, 50 monitors)
- Ajouter un monitor HTTP sur `https://fleet-hub.fr/actuator/health`
- Alerte email + webhook Slack/Discord si downtime > 5 min

### 4.2 Logs centralisés

- Option A (gratuit) : Grafana Cloud (10 Go logs gratuits) avec Loki
  - Ajouter `loki` logback appender dans `logback-spring.xml`
- Option B (simple) : garder les logs JSON dans Docker + `docker logs --since 1h` pour le debug

### 4.3 Dashboard Grafana (optionnel)

- Ajouter `grafana` + `prometheus` au `docker-compose.yml`
- Spring Boot Actuator + Micrometer expose déjà `/actuator/prometheus`
- Dashboard Spring Boot JVM (pré-configuré dans Grafana)

---

## Séquencement résumé

```
Semaine 1 : Sentry + Backups off-site + Emails + JWT revocation
Semaine 2 : Index DB + Cache HTTP + Redis KPI + Body limits
Semaine 3 : E2E en CI + Vuln scan + 2FA
Semaine 4 : Rate limiting par route + IP allowlisting + Monitoring
Semaine 5 : Logs centralisés + Dashboard Grafana (optionnel)
```

---

## Estimation totale

| Phase | Effort | Priorité |
|-------|--------|----------|
| Phase 1 — Critique | ~2 jours | 🔴 Immédiat |
| Phase 2 — Dette technique | ~3 jours | 🟡 Semaine 2-3 |
| Phase 3 — Sécurité | ~3 jours | 🟡 Semaine 4 |
| Phase 4 — Observabilité | ~2 jours | 🟢 Semaine 4-5 |
| **Total** | **~10 jours** | |

Ce plan est auto-portant : chaque tâche a des fichiers cibles, du code concret, et des tests à écrire. Prêt à commencer ?
