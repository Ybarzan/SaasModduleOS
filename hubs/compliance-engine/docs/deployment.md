# Déploiement IncoKalk

> Guide de déploiement pour les environnements de développement, staging et production.

---

## Prérequis

- Docker 24+ et Docker Compose
- Kubernetes 1.28+ (pour la production)
- kustomize 5.0+
- Un cluster Kubernetes avec ingress-controller + cert-manager
- DNS configuré pour le domaine de production

---

## Développement local

### Avec Docker Compose

```bash
cd infrastructure/docker

# Copier la configuration
cp .env.example .env

# Modifier les valeurs dans .env (mots de passe, clés API, ports)

# Démarrer tous les services
docker compose up -d --build
```

Services disponibles :

| Service | URL |
|---|---|
| Frontend | http://localhost:5174 |
| API / Swagger | http://localhost:8081/api/swagger-ui.html |
| MinIO Console | http://localhost:9001 |
| pgAdmin (dev) | http://localhost:5050 |

### Sans Docker (dev local)

```bash
# Backend (port 8080)
cd backend
mvn spring-boot:run

# Frontend (port 5173)
cd frontend
npm install
npm run dev
```

---

## Staging

### Avec Docker Compose

```bash
cd infrastructure/docker

# Utiliser le profil staging
docker compose --profile staging up -d --build
```

### Variables d'environnement staging

```env
# .env.staging
API_PORT=8081
FRONTEND_PORT=5174
CORS_ORIGINS=https://staging.incokalk.com

# Database
DB_URL=jdbc:postgresql://postgres-staging:5432/incokalk_staging
DB_USERNAME=incokalk_staging
DB_PASSWORD=<staging_db_password>

# JWT
JWT_SECRET=<staging_jwt_secret>

# Stripe (clé test)
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Transporteurs (clés de test)
DHL_API_KEY=<test_key>
GEODIS_API_KEY=<test_key>
```

---

## Production

### Prérequis Kubernetes

- Cluster Kubernetes 1.28+
- Ingress controller (nginx-ingress ou traefik)
- cert-manager pour les certificats TLS
- PostgreSQL managé (RDS, CloudSQL, etc.) ou StatefulSet
- Redis managé ou Deployment
- MinIO ou S3 pour le stockage objets

### Déploiement avec Kustomize

```bash
cd infrastructure/k8s

# Appliquer la configuration de base
kustomize build . | kubectl apply -f -

# Ou avec un overlay de production
kustomize build overlays/production | kubectl apply -f -
```

### Configuration des secrets

Les secrets Kubernetes doivent être créés avant le déploiement :

```bash
kubectl create secret generic incokalk-secrets \
  -n incokalk \
  --from-literal=DB_PASSWORD=<prod_db_password> \
  --from-literal=JWT_SECRET=<prod_jwt_secret> \
  --from-literal=STRIPE_SECRET_KEY=<prod_stripe_key> \
  --from-literal=STRIPE_WEBHOOK_SECRET=<prod_stripe_webhook_secret> \
  --from-literal=MINIO_ACCESS_KEY=<minio_access_key> \
  --from-literal=MINIO_SECRET_KEY=<minio_secret_key>
```

### Variables d'environnement production

```env
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://<prod-db-host>:5432/incokalk_prod
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  redis:
    host: <prod-redis-host>
    port: 6379

jwt:
  secret: ${JWT_SECRET}

stripe:
  secret-key: ${STRIPE_SECRET_KEY}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET}

minio:
  endpoint: <minio-or-s3-endpoint>
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}

tracing:
  enabled: true
  endpoint: http://otel-collector:4318
```

---

## CI/CD (GitHub Actions)

Le workflow `.github/workflows/ci.yml` exécute :

1. **Tests backend** : JUnit 5 + Testcontainers (PostgreSQL + Redis)
2. **Lint + tests frontend** : ESLint + Vitest
3. **Build** : Maven package + Vite build
4. **Publication** : Images Docker sur GHCR (branche `master` uniquement)
5. **Couverture** : Upload Codecov

### Déclenchement du déploiement

Le déploiement n'est pas automatisé dans le workflow CI actuel. Pour déployer :

```bash
# 1. Pousser sur master
git push origin master

# 2. Le CI build et teste automatiquement

# 3. Déployer manuellement (ou via GitHub Actions manual trigger)
kubectl set image deployment/incokalk-backend \
  incokalk-backend=ghcr.io/<org>/incokalk-backend:latest \
  -n incokalk

kubectl set image deployment/incokalk-frontend \
  incokalk-frontend=ghcr.io/<org>/incokalk-frontend:latest \
  -n incokalk
```

---

## Health Checks

### Backend

```bash
# Liveness probe
curl http://localhost:8081/api/actuator/health/liveness

# Readiness probe
curl http://localhost:8081/api/actuator/health/readiness
```

### Frontend

```bash
curl http://localhost:5174/health
```

### Base de données

```bash
# PostgreSQL
docker exec -it <postgres-container> pg_isready -U postgres

# Redis
docker exec -it <redis-container> redis-cli ping
```

---

## Monitoring

### Métriques applicatives

- Spring Boot Actuator : `/actuator/metrics`, `/actuator/health`, `/actuator/info`
- Prometheus : scraping des métriques Spring Boot
- Grafana : dashboards de monitoring (à configurer)

### Logs

- Les logs sont envoyés dans la console (stdout) par défaut
- En production, configurer un collecteur centralisé (Fluentd, Logstash, Loki)

---

## Backup & Restauration

### PostgreSQL

```bash
# Backup
pg_dump -h <host> -U postgres incokalk_prod > backup_$(date +%Y%m%d).sql

# Restauration
psql -h <host> -U postgres incokalk_prod < backup_20260731.sql
```

### MinIO/S3

Le service `backup` dans `docker-compose.yml` effectue un backup automatique toutes les 2h vers MinIO.

Pour les sauvegardes S3, configurer un bucket externe dans `.env`.

---

## Mises à jour

### Migrations Flyway

Les migrations Flyway s'exécutent automatiquement au démarrage de l'application. Elles sont appliquées dans l'ordre numérique (V1, V2, ...).

**Règles :**
- Ne jamais renommer une migration déjà appliquée en production
- Les migrations `V45__warehouse_receiving.sql` et `V46__shipment_items.sql` sont les dernières
- Pour ajouter une nouvelle migration : `V47__description.sql`

### Rollback

Flyway ne supporte pas le rollback automatique. En cas de problème :
1. Restaurer la base de données depuis le backup
2. Corriger la migration
3. Redéployer

---

## Scaling

### Horizontal Pod Autoscaler (K8s)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: incokalk-backend
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: incokalk-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### Ressources recommandées

| Composant | CPU | Mémoire |
|---|---|---|
| Backend | 500m - 2 | 512Mi - 2Gi |
| Frontend | 100m - 1 | 128Mi - 512Mi |
| PostgreSQL | 1 - 4 | 1Gi - 4Gi |
| Redis | 100m - 500m | 128Mi - 512Mi |
| MinIO | 200m - 1 | 256Mi - 1Gi |
