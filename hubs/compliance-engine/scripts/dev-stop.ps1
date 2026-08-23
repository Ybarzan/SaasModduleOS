# 🛑 IncoKalk — Arrêt de l'environnement de développement
$dockerDir = Join-Path (Split-Path -Parent $PSScriptRoot) "infrastructure\docker"
Set-Location $dockerDir
docker compose -f docker-compose.dev.yml --env-file .env.dev down
Write-Host "✔ Stack arrêtée" -ForegroundColor Green