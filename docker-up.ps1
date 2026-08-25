<#
.SYNOPSIS
  Demarre les deux stacks Docker de SaasModduleOS (compliance-engine + fleet-hub).

.DESCRIPTION
  Les deux hubs sont des produits independants (voir docs/07-integration-fleet-hub.md :
  jamais fusionnes, integration par API uniquement). Ce script ne modifie ni ne fusionne
  leurs docker-compose.yml respectifs : il les lance comme deux projets Compose isoles
  (-p compliance-engine / -p saas-fleethub), chacun avec son propre .env. Le nom de
  projet "saas-fleethub" (et pas simplement "fleet-hub") est deliberement distinct du
  nom que prendrait `docker compose` par defaut si lance depuis le depot fleet-hub
  d'origine (D:\Users\nexus\Desktop\fleet-hub) -- reutiliser le meme nom de projet
  reutilise aussi ses volumes nommes (meme mot de passe Postgres deja fige dedans),
  ce qui a cause un vrai echec de demarrage (FATAL: password authentication failed)
  la premiere fois que ce script a ete teste sur cette machine.

  Seule collision de ports reelle entre les deux stacks : nginx (compliance-engine) et
  caddy (fleet-hub) veulent tous les deux 80/443. compliance-engine est deja parametrable
  via HTTP_PORT/HTTPS_PORT (aucune modif de fichier necessaire) donc ce script le
  redirige sur 8080/8443 et laisse fleet-hub sur 80/443 natif (son Caddyfile suppose des
  ports standards pour le TLS automatique Let's Encrypt en cas de vrai deploiement).

.PARAMETER InfraOnly
  Ne demarre que Postgres/Redis/MinIO de compliance-engine (via son docker-compose.dev.yml
  existant), pour un developpement avec le backend/frontend lances en local
  (mvn spring-boot:run, npm run dev) plutot qu'en conteneur. fleet-hub n'est PAS demarre
  dans ce mode : son docker-compose.yml ne publie aucun port host pour sa base/son cache
  (acces interne au reseau Docker uniquement), donc inutilisable depuis un backend lance
  en local de toute facon -- le flux de dev deja etabli pour fleet-hub cette session
  (mvn spring-boot:run avec H2 en memoire) reste la bonne approche en mode InfraOnly.

.EXAMPLE
  .\docker-up.ps1
  .\docker-up.ps1 -InfraOnly
#>
param(
    [switch]$InfraOnly
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$ceDir = Join-Path $root "hubs\compliance-engine\infrastructure\docker"
$ceCompose = Join-Path $ceDir "docker-compose.yml"
$ceDevCompose = Join-Path $ceDir "docker-compose.dev.yml"
$ceEnvExample = Join-Path $ceDir ".env.example"
$ceEnv = Join-Path $ceDir ".env"

$fhDir = Join-Path $root "hubs\fleet-hub"
$fhCompose = Join-Path $fhDir "docker-compose.yml"
$fhEnvExample = Join-Path $fhDir ".env.example"
$fhEnv = Join-Path $fhDir ".env"

function New-RandomSecret {
    param([int]$Bytes = 48)
    $buf = [byte[]]::new($Bytes)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buf)
    } finally {
        $rng.Dispose()
    }
    return ([Convert]::ToBase64String($buf) -replace '[/+=]', '')
}

function Initialize-EnvFile {
    param(
        [string]$ExamplePath,
        [string]$TargetPath,
        [string[]]$SecretKeys
    )
    if (Test-Path $TargetPath) {
        Write-Host "  .env deja present ($TargetPath) - conserve tel quel" -ForegroundColor DarkGray
        return
    }
    if (-not (Test-Path $ExamplePath)) {
        throw "Fichier modele introuvable: $ExamplePath"
    }
    Write-Host "  Generation de $TargetPath (secrets locaux auto-generes, jamais commite)" -ForegroundColor Yellow
    $lines = Get-Content -Path $ExamplePath
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        $written = $false
        foreach ($key in $SecretKeys) {
            if ($line -match "^$key=") {
                $out.Add("$key=$(New-RandomSecret)")
                $written = $true
                break
            }
        }
        if (-not $written) {
            $out.Add($line)
        }
    }
    Set-Content -Path $TargetPath -Value $out -Encoding utf8
}

Write-Host "=== SaasModduleOS :: demarrage Docker ===" -ForegroundColor Cyan
if ($InfraOnly) {
    Write-Host "Mode infra seule : compliance-engine (Postgres/Redis/MinIO) uniquement" -ForegroundColor Cyan
}
Write-Host ""

Write-Host "[compliance-engine] preparation .env" -ForegroundColor Cyan
Initialize-EnvFile -ExamplePath $ceEnvExample -TargetPath $ceEnv -SecretKeys @("POSTGRES_PASSWORD", "MINIO_ROOT_PASSWORD", "JWT_SECRET", "API_KEY_SALT")

if (-not $InfraOnly) {
    Write-Host "[fleet-hub] preparation .env" -ForegroundColor Cyan
    Initialize-EnvFile -ExamplePath $fhEnvExample -TargetPath $fhEnv -SecretKeys @("DB_PASSWORD", "JWT_SECRET", "ADMIN_PASSWORD", "GESTIONNAIRE_PASSWORD", "INTEGRATION_SECRET_KEY")
}

Write-Host ""
Write-Host "[compliance-engine] docker compose up" -ForegroundColor Cyan
if ($InfraOnly) {
    docker compose -p compliance-engine -f $ceDevCompose --env-file $ceEnv up -d
    if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de compliance-engine (infra)" }
} else {
    $env:HTTP_PORT = "8080"
    $env:HTTPS_PORT = "8443"
    try {
        docker compose -p compliance-engine -f $ceCompose --env-file $ceEnv up -d --build
        if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de compliance-engine" }
    } finally {
        Remove-Item Env:\HTTP_PORT -ErrorAction SilentlyContinue
        Remove-Item Env:\HTTPS_PORT -ErrorAction SilentlyContinue
    }
}

if (-not $InfraOnly) {
    Write-Host ""
    Write-Host "[fleet-hub] docker compose up" -ForegroundColor Cyan
    docker compose -p saas-fleethub -f $fhCompose --env-file $fhEnv up -d --build
    if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de fleet-hub" }
}

Write-Host ""
Write-Host "=== Stacks demarrees ===" -ForegroundColor Green
if ($InfraOnly) {
    Write-Host "compliance-engine : Postgres localhost:5432, Redis localhost:6379, MinIO localhost:9000/9001"
    Write-Host "  -> backend en local : mvn spring-boot:run -Dspring-boot.run.profiles=dev (hubs/compliance-engine/backend)"
    Write-Host "fleet-hub : non demarre en mode InfraOnly (voir -InfraOnly dans Get-Help .\docker-up.ps1 -Full)"
    Write-Host "  -> backend en local avec H2 : mvn spring-boot:run (hubs/fleet-hub/backend, profil par defaut)"
} else {
    Write-Host "compliance-engine : http://localhost:8080"
    Write-Host "fleet-hub         : http://localhost"
}
Write-Host ""
Write-Host "Statut : docker compose -p compliance-engine ps   /   docker compose -p saas-fleethub ps"
Write-Host "Arret  : .\docker-down.ps1"
