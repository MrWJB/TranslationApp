# TranslationApp - start crawler, backend, and frontend in separate windows
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host "Starting TranslationApp services from: $Root"

function Test-PortListening([int]$Port) {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $conn
}

if (-not (Test-PortListening 3000)) {
    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-Command",
        "Set-Location '$Root\crawler-service'; Write-Host 'Crawler Service (port 3000)'; npm start"
    )
    Write-Host "Started crawler-service on port 3000"
} else {
    Write-Host "Crawler already listening on port 3000"
}

Start-Sleep -Seconds 2

if (-not (Test-PortListening 8080)) {
    $backendCmd = @"
Set-Location '$Root\backend'
`$env:MYSQL_HOST='127.0.0.1'
`$env:JAVA_TOOL_OPTIONS='-Djava.net.useSystemProxies=false -DsocksNonProxyHosts=localhost|127.0.0.1|*'
Write-Host 'Backend (port 8080)'
mvn spring-boot:run -DskipTests '-Dspring-boot.run.jvmArguments=-Djava.net.useSystemProxies=false -DsocksNonProxyHosts=localhost|127.0.0.1|*'
"@
    Start-Process powershell -ArgumentList @("-NoExit", "-Command", $backendCmd)
    Write-Host "Started backend on port 8080"
} else {
    Write-Host "Backend already listening on port 8080"
}

Start-Sleep -Seconds 3

if (-not (Test-PortListening 5173)) {
    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-Command",
        "Set-Location '$Root\frontend'; Write-Host 'Frontend (port 5173)'; npm run dev"
    )
    Write-Host "Started frontend on port 5173"
} else {
    Write-Host "Frontend already listening on port 5173"
}

Write-Host ""
Write-Host "Ensure MySQL is running (see SETUP.md) and IM infra is up: .\scripts\start-infra.ps1"
Write-Host "Open http://localhost:5173 and login with admin / admin123"
Write-Host "Tip: use 'Import local documents' if crawled-docs already exists."
