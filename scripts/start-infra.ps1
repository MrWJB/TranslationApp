# TranslationApp - start IM infrastructure (Redis, MinIO, optional MySQL/coturn)
# Skips Docker Hub pull when images already exist locally (helps on slow/restricted networks).
param(
    [string[]]$Services = @("redis", "minio", "minio-init"),
    [switch]$IncludeMysql,
    [switch]$IncludeCoturn,
    [switch]$ForcePull
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

# Docker Compose writes routine status lines to stderr; do not treat them as terminating errors.
$PrevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"

if ($IncludeMysql) { $Services = @("mysql") + $Services }
if ($IncludeCoturn) { $Services = $Services + @("coturn") }

function Test-PortListening([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $conn
}

function Test-DockerImagePresent([string]$ImageRef) {
    $id = docker image inspect $ImageRef --format "{{.Id}}" 2>$null
    return [bool]$id
}

function Get-ComposeImageRefs {
    param([string[]]$ServiceNames)
    $refs = @()
    foreach ($svc in $ServiceNames) {
        $img = docker compose config --images $svc 2>$null
        if ($img) { $refs += $img.Trim() }
    }
    return $refs | Select-Object -Unique
}

function Show-DockerHubMirrorHelp {
    Write-Host ""
    Write-Host "Docker Hub pull timed out (common on restricted networks / China)." -ForegroundColor Yellow
    Write-Host "Options:"
    Write-Host "  1. Configure a registry mirror in Docker Desktop -> Settings -> Docker Engine, e.g.:"
    Write-Host '     { "registry-mirrors": ["https://docker.1panel.live", "https://docker.m.daocloud.io"] }'
    Write-Host "     Then: Docker Desktop -> Apply & Restart, and re-run this script."
    Write-Host "  2. If images already exist locally, re-run without -ForcePull (default skips pull)."
    Write-Host "  3. Use a host Redis on port 6379 and skip the redis service:"
    Write-Host "     .\scripts\start-infra.ps1 -Services minio,minio-init"
    Write-Host ""
}

Write-Host "TranslationApp infrastructure startup"
Write-Host "Services: $($Services -join ', ')"
Write-Host ""

$portChecks = @{
    6379 = "redis"
    9000 = "minio"
    3306 = "mysql"
}
foreach ($entry in $portChecks.GetEnumerator()) {
    if ($entry.Value -in $Services -and (Test-PortListening $entry.Key)) {
        $container = docker compose ps -q $entry.Value 2>$null
        if (-not $container) {
            Write-Host "Port $($entry.Key) is in use (not from compose '$($entry.Value)'). Container may fail to bind." -ForegroundColor Yellow
        }
    }
}

$imageRefs = Get-ComposeImageRefs -ServiceNames $Services
$missing = @($imageRefs | Where-Object { -not (Test-DockerImagePresent $_) })

if ($ForcePull -or $missing.Count -gt 0) {
    if ($missing.Count -gt 0) {
        Write-Host "Missing local images: $($missing -join ', ')"
    }
    Write-Host "Pulling images (may fail on slow Docker Hub)..."
    $pullOut = docker compose pull @Services 2>&1
    $pullOut | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        Show-DockerHubMirrorHelp
        if ($missing.Count -eq 0) {
            Write-Host "Pull failed but all required images exist locally; starting without re-pull." -ForegroundColor Yellow
        } else {
            throw "docker compose pull failed and required images are missing locally."
        }
    }
} else {
    Write-Host "All required images present locally; skipping pull."
}

Write-Host "Starting containers (docker compose up -d --no-build)..."
$upOut = docker compose up -d --no-build @Services 2>&1
$upOut | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed (exit $LASTEXITCODE)."
}

Write-Host ""
docker compose ps @Services
Write-Host ""
Write-Host "Infrastructure ready. Redis: localhost:6379, MinIO: localhost:9000 (console :9001)"
$ErrorActionPreference = $PrevEap
