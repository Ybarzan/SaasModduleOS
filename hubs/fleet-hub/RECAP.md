# Fleet Hub - Recap des modifications

## Stack technique
- **Backend:** Java 21 / Spring Boot 3.4.5, PostgreSQL 16 (prod), H2 (dev), Redis 7 (prod cache)
- **Frontend:** React 18 / Vite 5, Capacitor 8 (mobile), Docker Compose + Caddy
- **Dev credentials:** `admin`/`admin` (ADMIN), `saasadmin`/`admin` (SAAS_ADMIN), `gestionnaire`/`gestion` (GESTIONNAIRE)

---

## Phase 1 - Securite critique

| Modification | Details |
|---|---|
| `.env` non commis | Verifie que `.env` est bien dans `.gitignore` et absent de l'historique git |
| Credentials retires | Suppression des identifiants de test hardcodes dans `Login.jsx` |
| XSS corrige | Utilisation de DOMPurify dans `Legal.jsx` pour le contenu HTML |
| CSP + security headers | Ajoutes dans `Caddyfile` + `nginx.conf` |
| Fallback secrets | Reversion vers des secrets sure pour le dev (pas de defaults en prod) |

## Phase 2 - Securite renforcee

| Modification | Details |
|---|---|
| JWT HttpOnly cookie | Le token est maintenant envoye via cookie HttpOnly (web) + header Authorization (Capacitor/native) |
| AuthController | Gestion des cookies sur login/register/logout |
| JwtAuthFilter | Lecture depuis cookie OU header pour compatibilite web + mobile |
| CORS restreint | Headers autorises limites a `Authorization` + `Content-Type` |
| Rate limiting global | Limitation de requetes sur tous les endpoints `POST /api/**` |
| Memory leak rate limiter | Nettoyage automatique toutes les 60s |

## Phase 3 - Qualite du code

| Modification | Details |
|---|---|
| ESLint + Prettier | Configuration dans `eslint.config.js` + `.prettierrc` |
| Scripts npm | `lint`, `lint:fix`, `format`, `format:check`, `e2e` ajoutes |
| CI pipeline | Etape ESLint ajoutee au pipeline CI |
| Erreurs silencieuses | `catch(() => {})` remplaces par `console.error` dans Dashboard, DriverDetail, Tachographie |

## Phase 4 - Ameliorations

| Modification | Details |
|---|---|
| JWT Token refresh | Endpoint `/auth/refresh` + timer 20min + retry automatique sur 401 |
| Swagger | Annotations sur 10 controlleurs (65 endpoints) |
| Validation DTO | `@Size(max)` sur tous les champs string des DTOs |
| URL externe | URL `api.fleethub.fr` hardcodee externalisee via variable d'environnement |
| Log level | Configurable via variable `APP_LOG_LEVEL` |

## Bug fixes

| Bug | Correction |
|---|---|
| `req.getModel()` | Corrige vers `req.model()` (accessor Java record) dans TruckController |
| Boucle 401 interceptor | Skip du retry sur les endpoints auth + refresh |
| Cookie `Secure` | Flag conditionnel uniquement en profile prod (`environment.matchesProfiles("prod")`) |

---

## Phase 5 - UI/UX Premium (CSS)

### Fichier `premium.css` - Effets animees

| Element | Effet |
|---|---|
| Sidebar | Glassmorphism backdrop-blur, gradient anime en haut/bas, logo avec pulse breathing |
| Nav links | Hover liquid scaleX depuis la gauche, translation 4px, etat actif avec glow + breathing pulse |
| Boutons | Ripple au clic (cercle qui s'expand), gradient anime en boucle, hover lift + glow, scale a l'activation |
| Page login | Orbs flottantes derriere la carte, gradient anime en haut, glassmorphism renforce, texte gradient shimmer |
| Stat cards | Entree fadeInUp espacee, shimmer au survol, rotation+scale de l'icone, ombres colorees specifiques |
| Cards | Hover lift 3px, glow bordure, barre d'accentuation qui s'etend au survol |
| Gauges | Remplissage anime du stroke au montage, filtre glow |
| Tables | Survol de ligne + scale subtil, feedback au clic |
| Tabs/selector | Gradient anime sur l'actif, hover lift |
| Inputs | Glow ring au focus + lift leger |
| Contenu page | Entrees fadeInUp escalees (0.05s de delai par enfant) |
| Scrollbar | Thumb avec gradient couleur primaire |
| Selection | Surbrillance coloree |
| Focus-visible | Glow ring pour navigation clavier |

Tout en CSS pur via `!important` - zero modification du fichier `styles.css` existant.

---

## Phase 6 - Mode sombre / Mode clair

### Fichiers crees

| Fichier | Role |
|---|---|
| `context/ThemeContext.jsx` | Context React avec `theme`, `toggleTheme`, `isDark`. Persistance `localStorage('fh_theme')`. Detection `prefers-color-scheme` au premier chargement |
| `theme.css` | Variables CSS light mode via `[data-theme="light"]` + overrides pour tous les composants |

### Fichiers modifies

| Fichier | Modification |
|---|---|
| `main.jsx` | `ThemeProvider` enveloppe l'app, `StatusBarSync` adapte la status bar Capacitor, import `theme.css` |
| `Layout.jsx` | Bouton toggle thème dans sidebar footer (icone soleil/lune + texte) et mobile topbar (icone seule) |
| `premium.css` | Overrides light mode : shadows reduites, glassmorphism adapte, animations desactivees quand trop visibles |

### Fonctionnement

| Comportement | Detail |
|---|---|
| Toggle | Bouton soleil/lune dans sidebar footer + mobile topbar |
| Persistance | `localStorage('fh_theme')` - le choix survit aux rechargements |
| Auto-detection | Premier visit suit `prefers-color-scheme` du systeme |
| Transitions | Tous les changements de couleur utilisent `transition: 0.25s` pour un fondu fluide |
| Mobile | Toggle accessible dans le topbar mobile |
| Status bar Capacitor | Adapte automatiquement : dark=#05070d / light=#f4f6f9 |

### Palette light mode

| Variable | Valeur |
|---|---|
| `--bg` | `#f4f6f9` |
| `--bg-soft` | `#e9edf2` |
| `--card` | `#ffffff` |
| `--text` | `#1a2332` |
| `--text-soft` | `#4a5568` |
| `--muted` | `#8896a8` |
| `--primary` | `#3a7ca5` |
| `--border` | `#d1d9e4` |

---

## Phase 7 - Production readiness

### Sentry (erreur tracking)

| Composant | Etat | Details |
|---|---|---|
| Backend | Deja en place | `sentry-spring-boot-starter` v8.48 dans `pom.xml`, config `sentry.dsn` dans `application.yml` |
| Frontend | Deja en place | `@sentry/react` v9.0, `Sentry.ErrorBoundary` dans `main.jsx`, filtrage des erreurs reseau/ChunkLoadError |
| Build arg | Ajoute | `SENTRY_DSN_FRONTEND` injecte via `docker-compose.yml` (build args) + `frontend/Dockerfile` (ARG/ENV) |
| Documentation | Ajoute | `SENTRY_DSN_FRONTEND` documente dans `.env.example` avec `SENTRY_DSN` existant |
| Activation | Reste a faire | Creer un compte sentry.io (gratuit), renseigner les DSN dans `.env` |

### Sauvegardes hors site

| Composant | Etat | Details |
|---|---|---|
| Script backup | Deja en place | `backup/backup.sh` gere rclone (install auto, config base64) + healthchecks.io ping |
| Docker compose | Deja en place | Variables `RCLONE_REMOTE`, `RCLONE_CONFIG_BASE64`, `BACKUP_PING_URL` passees au conteneur |
| Documentation | Deja en place | Variables documentees dans `.env.example` avec exemples concrets |
| Activation | Reste a faire | Choisir un stockage (Backblaze B2 gratuit 10 Go), configurer rclone, renseigner `.env` |

### Emails transactionnels

| Composant | Etat | Details |
|---|---|---|
| Backend | Deja en place | `application.yml` configure SMTP via `MAIL_*`, `EmailNotifier` + `SmtpEmailService` + `LoggingEmailService` |
| Docker compose | Deja en place | Variables `MAIL_*` passees au conteneur |
| Templates | Deja en place | Templates HTML (bienvenue, invitation, mot de passe oublie, rappel essai, echec paiement) |
| Activation | Reste a faire | Creer un compte Brevo (300/jour gratuits), renseigner `MAIL_ENABLED=true` + `MAIL_*` dans `.env` |

### Revocation JWT

| Fichier | Action | Description |
|---|---|---|
| `V2__jwt_revocation.sql` | Cree | Migration Flyway : table `revoked_token` (token_id, expires_at, revoked_at) + index |
| `RevokedToken.java` | Cree | Entite JPA avec contrainte `UNIQUE` sur token_id |
| `RevokedTokenRepository.java` | Cree | Repository Spring Data : `existsByTokenId`, `deleteExpired` (nettoyage auto) |
| `TokenRevocationService.java` | Cree | Service : `revoke(token)`, `isRevoked(tokenId)`, `cleanupExpiredTokens()` (toutes les 6h via `@Scheduled`) |
| `JwtService.java` | Modifie | Ajout `jti` (UUID) dans chaque token genere + `extractTokenId()` + `extractAllClaims()` public |
| `JwtAuthFilter.java` | Modifie | Verifie `isRevoked(tokenId)` avant d'authentifier l'utilisateur |
| `AuthController.java` | Modifie | `POST /logout` lit cookie + Bearer header, appelle `revoke(token)`. `/refresh` revoque l'ancien token avant d'en generer un nouveau |
| `JwtServiceTest.java` | Modifie | +2 tests : `extractTokenId`, `extractAllClaims` |
| `TokenRevocationServiceTest.java` | Cree | 5 tests unitaires (mockes) : revoke, skip double, isRevoked true/false/null |
| `AuthControllerTest.java` | Modifie | +1 test integration : `logout_revokesToken` (login -> use token -> logout -> token rejected 401) |

---

## Phase 8 - Securite avancee (19 Aout 2026)

### Index DB manquants

| Fichier | Details |
|---|---|
| `V3__indexes.sql` | 23 index : 12 sur `company_id` (toutes les tables metier) + 11 composites KPI (trip, event, maintenance, fuel, cost) |

### Cache HTTP assets statiques

| Fichier | Modification |
|---|---|
| `frontend/nginx.conf` | `Cache-Control: public, max-age=31536000, immutable` sur tous les fichiers statiques (JS, CSS, images, fonts) |
| `caddy/Caddyfile` | Matcher `@static` + header identique, meme regle via `path_regexp` |

### Limites body request

| Fichier | Modification |
|---|---|
| `application.yml` | `spring.servlet.multipart.max-file-size: 10MB` + `max-request-size: 10MB` (protection upload DOS) |

### GlobalExceptionHandler

| Fichier | Modification |
|---|---|
| `GlobalExceptionHandler.java` | Ajout `@ExceptionHandler(Exception.class)` catch-all retourne 500 + stacktrace loggee (evite les reponses blanches) |

### EmailNotifier bug fix

| Fichier | Modification |
|---|---|
| `EmailNotifier.java` | Correction logique inversée : quand `enabled=false`, appelait `send()` au lieu de logger et retourner |

### TruckController - @Transactional scope

| Fichier | Modification |
|---|---|
| `TruckController.java` | `@Transactional` retire du niveau classe (evite transaction sur GET), ajoute sur POST/PUT/DELETE uniquement |

### CI/CD Pipeline GitHub Actions

| Fichier | Details |
|---|---|
| `.github/workflows/ci.yml` | Pipeline complet : lint frontend → tests frontend → compile backend → tests backend → Docker build → scan Trivy |

### Rate limiting par route

| Fichier | Modification |
|---|---|
| `LoginRateLimitFilter.java` | Refonte avec `TreeMap` pour matching par prefixe : auth=5/min, webhooks=exclus (0), default=60/min |

### Revoke-all admin endpoint

| Fichier | Details |
|---|---|
| `V4__user_token_cutoff.sql` | Migration : table `user_token_cutoff` (user_id, cutoff_at) |
| `UserTokenCutoff.java` | Entite JPA pour le cutoff |
| `UserTokenCutoffRepository.java` | Repository Spring Data |
| `TokenRevocationService.java` | Methode `revokeAllForUser(userId)` + `isTokenCutoff(tokenId, userId, issuedAt)` |
| `AdminUserController.java` | `POST /api/admin/users/{id}/revoke-all` |
| `JwtAuthFilter.java` | Verifie le cutoff timestamp avant d'authentifier |
| `JwtService.java` | `extractIssuedAt()` ajoute |

### IP allowlisting admin

| Fichier | Details |
|---|---|
| `AdminIpFilter.java` | Filtre pour `/api/admin/**` : verifie IP source contre `APP_ADMIN_ALLOWED_IPS` |
| `SecurityConfig.java` | Filtre ajoute dans la chaine de securite |

---

## Phase 9 - 2FA/TOTP (19 Aout 2026)

### Backend

| Fichier | Details |
|---|---|
| `V5__2fa_totp.sql` | Migration : colonnes `totp_secret`, `totp_enabled` sur `app_user` |
| `TwoFactorService.java` | TOTP RFC 6238 (pure Java, Base32 inline) : `generateSecret()`, `getUri()`, `verifyCode()` |
| `TwoFactorController.java` | `POST /api/2fa/setup`, `POST /api/2fa/enable`, `POST /api/2fa/disable` |
| `LoginRequest.java` | Champ `totpCode` ajoute |
| `AuthResponse.java` | Champ `totpRequired` ajoute |
| `AuthService.java` | Flux 2FA : si `totpEnabled` et pas de code → retourne `totpRequired=true` |
| `AuthController.java` | Gestion login 2FA (pas de cookie quand `totpRequired`) |
| `AppUser.java` | Champs `totpSecret`, `totpEnabled` ajoutes |

### Frontend

| Fichier | Details |
|---|---|
| `Login.jsx` | Champ TOTP conditionnel affiche quand `totpRequired=true` |
| `AuthContext.jsx` | `login()` accepte parametre optionnel `totpCode` |

### Tests mis a jour

| Fichier | Details |
|---|---|
| `AuthServiceTest.java` | +1 mock `TwoFactorService`, constructeur mis a jour, `LoginRequest` avec 3 args |
| `Login.test.jsx` | Mock `totpRequired: false` dans resolvedValue, assertion avec 3 args |

---

## Phase 10 - Redis Cache + Observabilite (19 Aout 2026)

### Redis KPI Cache

| Fichier | Details |
|---|---|
| `pom.xml` | +`spring-boot-starter-cache` + `spring-boot-starter-data-redis` |
| `docker-compose.yml` | Service `redis` (redis:7-alpine, 64MB LRU), dependance backend → redis |
| `application.yml` (prod) | `spring.cache.type=redis`, TTL 300s, prefix `fleethub:` |
| `CacheConfig.java` | `@EnableCaching`, `RedisCacheManager` (prod, conditional on `RedisConnectionFactory`) + `ConcurrentMapCacheManager` (dev/test fallback) |
| `tenantKeyGenerator` | Cle cache composite : `{companyId}:{methodName}:{params}` (multi-tenant safe) |
| `KpiService.java` | `@Cacheable` sur `computeAllCouples`, `computeDetail`, `computeTruckDetail`, `computeNorthStarWidgets` |
| `DashboardService.java` | `@Cacheable` sur `summary` |

### Stack monitoring (Prometheus + Grafana + Loki)

| Fichier | Details |
|---|---|
| `monitoring/prometheus.yml` | Scrape `/actuator/prometheus` toutes les 15s |
| `monitoring/loki.yml` | Loki single-node, filesystem, retention 7j |
| `monitoring/promtail.yml` | Collecte logs Docker via socket, relabel container/stream |
| `monitoring/grafana/grafana.ini` | Config production, sign-up desactive |
| `monitoring/grafana/provisioning/datasources/datasources.yml` | Prometheus + Loki pre-configures |
| `monitoring/grafana/provisioning/dashboards/dashboards.yml` | Auto-load dashboard JSON |
| `monitoring/grafana/dashboards/fleethub.json` | Dashboard 8 panels : HTTP req/s, P95 response time, error rate 5xx, threads, JVM heap, HikariCP, GC pause, P50/P95/P99 |
| `docker-compose.yml` | +`prometheus`, `grafana`, `loki`, `promtail` services |
| `caddy/Caddyfile` | Route `/grafana` → `grafana:3000` |
| `.env.example` | Variables `GRAFANA_USER`, `GRAFANA_PASSWORD` documentees |

### Tests

| Check | Resultat |
|---|---|
| Backend tests | 93/93 passing (Phase 10) → 103/103 passing (Phase 11) |
| Frontend tests | 11/11 passing |
| ESLint | 0 erreurs |
| Backend compile | Clean |

---

## Phase 11 - Import de fichiers AS24 (19 Aout 2026)

### Contexte

Pour la demo avec un chef d'entreprise de transport, integration de l'import de fichiers
AS24 (tachygraphe Tak&drive + carburant Infoservice). Pas d'API REST disponible chez AS24 :
l'integration se fait par upload de fichiers CSV depuis l'espace client.

### Backend

| Fichier | Role |
|---|---|
| `dto/ImportResultDto.java` | DTO de retour : fileType, rowsRead, rowsImported, rowsSkipped, errors |
| `integration/parser/TachoFileParser.java` | Parser CSV tachygraphe → `TachographDayDto`. En-tetes flexibles (licence_number/license_number, driving_hours/driving, etc.), gestion erreurs par ligne |
| `integration/parser/FuelFileParser.java` | Parser CSV/DSW carburant → `FuelTransactionDto`. Detection auto du separateur (virgule/point-virgule/tabulation), formats dates ISO + francais (dd/MM/yyyy), format europeen (1.234,56) |
| `service/FileImportService.java` | Orchestre le parsing puis passe les DTOs a `IntegrationSyncService.ingest*()` (jointure par cle metier + idempotence existante) |
| `controller/FileImportController.java` | `POST /api/import/tachograph` + `POST /api/import/fuel` (multipart, JWT requis, role ADMIN/GESTIONNAIRE) |

### Frontend

| Fichier | Modification |
|---|---|
| `pages/DataImport.jsx` | Page complete : 2 onglets (Tachygraphe / Carburant), drag-and-drop, affichage du resultat (lignes lues/importees/ignorees/erreurs), tableau des formats attendus |
| `App.jsx` | Route `/import` ajoutee (lazy-loaded) |
| `Layout.jsx` | Nav item "Import fichiers" + icone upload ajoutes dans la sidebar |
| `styles.css` | Styles `.drop-zone` (drag-over, uploading) ajoutes |

### Donnees de demo

| Fichier | Contenu |
|---|---|
| `demo/sample_tacho.csv` | 21 jours de donnees tachygraphe pour 3 chauffeurs (7 jours × 3) |
| `demo/sample_fuel.csv` | 18 transactions carburant pour 6 camions |

### Tests

| Fichier | Tests |
|---|---|
| `FileImportParserTest.java` | 10 tests unitaires : parsing CSV tacho (standard, en-tetes alternatifs, erreurs, fichier vide) + parsing CSV fuel (standard, date francaise, separateur point-virgule, format europeen, erreurs, fichier vide) |

### Resultats

| Check | Resultat |
|---|---|
| Backend tests | 103/103 passing (+10 nouveaux) |
| Frontend build | Clean (DataImport-CrTwemn_.js lazy-loaded) |
| Compilation | Aucune erreur |

### Pipeline de donnees

```
Upload UI (drag-and-drop) → FileImportController → FileImportService
    → TachoFileParser / FuelFileParser → TachographDayDto / FuelTransactionDto
    → IntegrationSyncService.ingest*(data, companyId)
    → Jointure par cle metier (licenseNumber / registration)
    → Anti-doublon (idempotence)
    → Persistance dans TachographDay / FuelRecord
    → KPIs recalcules automatiquement
```

### Format des fichiers acceptes

**Tachygraphe (CSV) :**
```
licence_number,date,driving_hours,work_hours,rest_minutes
FR-104-852-371,2026-08-15,8.5,10.0,480
```

**Carburant (CSV ou DSW/AUL) :**
```
registration,date,liters,amount,odometer_km
GT-123-AB,2026-08-15,120.5,185.30,125000
```

---

## Ce qu'il reste a faire

### Demo / Go-to-market

| Tache | Effort | Priorite |
|---|---|---|
| Parser DDD binaire (tachygraphe europeen signe) | ~2 jours | Haute |
| Parser DSW/AUL avec colonnes positionnelles AS24 | ~1 jour | Haute |
| Historique des imports sur la page /import | ~2h | Moyenne |
| Page settings 2FA (setup/enable/disable UI) | ~4h | Moyenne |
| Page settings IP allowlisting (UI admin) | ~2h | Basse |

### Production

| Tache | Effort | Priorite |
|---|---|---|
| E2E Playwright dans le CI | ~1 jour | Moyenne |
| Scan vulnerabilites Trivy dans le CI | ~1h | Basse |
| UptimeRobot sur `/actuator/health` | ~15min | Basse |
| `TrialExpiryReminderTaskTest` bug | ~1h | Basse |

---

## Recap chronologique

| Phase | Date | Description |
|---|---|---|
| 1-6 | Juillet-Aout 2026 | Securite, qualite, UI/UX, dark mode |
| 7 | 18 Aout 2026 | Production readiness : Sentry, backups, emails, revocation JWT |
| 8 | 19 Aout 2026 | Securite avancee : index DB, cache HTTP, body limits, exception handler, email fix, TruckController tx, CI/CD, rate limiting par route, revoke-all admin, IP allowlisting |
| 9 | 19 Aout 2026 | 2FA/TOTP complet : backend (service + endpoints + migration) + frontend (login flow) + tests |
| 10 | 19 Aout 2026 | Redis KPI cache (TTL 5min, multi-tenant) + stack monitoring (Prometheus + Grafana + Loki + Promtail) + dashboard |
| 11 | 19 Aout 2026 | Import fichiers AS24 : parsers CSV tachygraphe + carburant, endpoints upload, page drag-and-drop, 10 tests, 103/103 passing |
