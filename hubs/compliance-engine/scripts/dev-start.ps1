# 🚀 IncoKalk — Démarrage de l'environnement de développement
# Lance Postgres + Redis + MinIO + pgAdmin via Docker,
# puis affiche les commandes à lancer dans d'autres terminaux.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dockerDir = Join-Path $root "infrastructure\docker"
$backendDir = Join-Path $root "backend"

function Write-Step($msg) { Write-Host "`n▶ $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  ✔ $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  ⚠ $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "  ✖ $msg" -ForegroundColor Red }

# ── 1. Pré-requis : Docker ─────────────────────────────────
Write-Step "Vérification de Docker…"
try {
    $null = docker version 2>$null
    if ($LASTEXITCODE -ne 0) { throw "docker non disponible" }
    Write-Ok "Docker est disponible"
} catch {
    Write-Err "Docker n'est pas lancé ou n'est pas installé."
    Write-Host "    → Télécharge Docker Desktop : https://www.docker.com/products/docker-desktop/"
    exit 1
}

# ── 2. Fichier .env.dev ────────────────────────────────────
Write-Step "Préparation du fichier d'environnement…"
$envFile = Join-Path $dockerDir ".env.dev"
if (-not (Test-Path $envFile)) {
    Write-Warn ".env.dev manquant — création d'un fichier par défaut"
    @"
POSTGRES_DB=incokalk
POSTGRES_USER=incokalk
POSTGRES_PASSWORD=incokalk_dev_2026
POSTGRES_PORT=5432
REDIS_PORT=6379
REDIS_PASSWORD=
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
JWT_SECRET=hTrlRyrc3j5S/D6zg5XiNtXjPfYSfcdTnEfA2ebVP6PHPy047YSNaIpfrAiftdyo
API_KEY_SALT=110861
EXCHANGERATE_API_KEY=
CORS_ORIGINS=http://localhost:5173,http://localhost:3000
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=
PGADMIN_EMAIL=admin@incokalk.io
PGADMIN_PASSWORD=admin
"@ | Out-File -FilePath $envFile -Encoding utf8
}

# ── 3. Démarrage de la stack Docker ───────────────────────
Write-Step "Démarrage de Postgres + Redis + MinIO + pgAdmin…"
Set-Location $dockerDir
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d
if ($LASTEXITCODE -ne 0) {
    Write-Err "Échec du démarrage Docker"
    exit 1
}
Write-Ok "Stack Docker démarrée"

# ── 4. Attente que Postgres soit prêt ─────────────────────
Write-Step "Attente de la disponibilité de Postgres…"
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    $status = docker inspect --format='{{.State.Health.Status}}' incokalk-postgres-dev 2>$null
    if ($status -eq "healthy") { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if ($ready) {
    Write-Ok "Postgres prêt (port 5432)"
} else {
    Write-Warn "Postgres pas encore 'healthy' — l'app Spring Boot retentera au démarrage"
}

# ── 5. Résumé & prochaines étapes ──────────────────────────
Write-Host ""
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  ✅  Stack de dev IncoKalk opérationnelle" -ForegroundColor Green
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "  📦  Postgres   : localhost:5432  (user=incokalk, pwd=incokalk_dev_2026)"
Write-Host "  💾  Redis      : localhost:6379"
Write-Host "  🪣  MinIO API  : http://localhost:9000  (console: http://localhost:9001)"
Write-Host "  🛠   pgAdmin    : http://localhost:5050  (admin@incokalk.io / admin)"
Write-Host ""
Write-Host "  ── Prochaines étapes ────────────────────────────" -ForegroundColor Cyan
Write-Host "  Terminal 1 (backend) :" -ForegroundColor Yellow
Write-Host "    cd $backendDir"
Write-Host '    mvn spring-boot:run "-Dspring-boot.run.profiles=dev"'
Write-Host ""
Write-Host "  Terminal 2 (frontend) :" -ForegroundColor Yellow
Write-Host "    cd $root\frontend"
Write-Host "    npm run dev"
Write-Host ""
Write-Host "  ── Arrêter la stack ──────────────────────────────" -ForegroundColor Cyan
Write-Host "    cd $dockerDir"
Write-Host "    docker compose -f docker-compose.dev.yml --env-file .env.dev down"
Write-Host ""