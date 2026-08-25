<#
.SYNOPSIS
  Arrete les deux stacks Docker de SaasModduleOS (compliance-engine + fleet-hub).

.PARAMETER InfraOnly
  N'arrete que le stack infra-seule de compliance-engine (docker-compose.dev.yml),
  a utiliser si demarre avec .\docker-up.ps1 -InfraOnly.

.PARAMETER RemoveVolumes
  Supprime aussi les volumes Docker (donnees Postgres/Redis/MinIO perdues). A utiliser
  uniquement pour repartir d'une base propre.

.EXAMPLE
  .\docker-down.ps1
  .\docker-down.ps1 -InfraOnly
  .\docker-down.ps1 -RemoveVolumes
#>
param(
    [switch]$InfraOnly,
    [switch]$RemoveVolumes
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$ceDir = Join-Path $root "hubs\compliance-engine\infrastructure\docker"
$ceCompose = Join-Path $ceDir "docker-compose.yml"
$ceDevCompose = Join-Path $ceDir "docker-compose.dev.yml"
$ceEnv = Join-Path $ceDir ".env"

$fhDir = Join-Path $root "hubs\fleet-hub"
$fhCompose = Join-Path $fhDir "docker-compose.yml"
$fhEnv = Join-Path $fhDir ".env"

$downArgs = @("down")
if ($RemoveVolumes) {
    $downArgs += "-v"
    Write-Host "ATTENTION : -RemoveVolumes va supprimer les donnees Postgres/Redis/MinIO." -ForegroundColor Red
}

Write-Host "=== SaasModduleOS :: arret Docker ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "[compliance-engine] docker compose down" -ForegroundColor Cyan
if ($InfraOnly) {
    docker compose -p compliance-engine -f $ceDevCompose --env-file $ceEnv @downArgs
} else {
    docker compose -p compliance-engine -f $ceCompose --env-file $ceEnv @downArgs
}

if (-not $InfraOnly) {
    Write-Host ""
    Write-Host "[fleet-hub] docker compose down" -ForegroundColor Cyan
    docker compose -p saas-fleethub -f $fhCompose --env-file $fhEnv @downArgs
}

Write-Host ""
Write-Host "=== Stacks arretees ===" -ForegroundColor Green
