param (
    [switch]$OnlyBackend,
    [switch]$OnlyFrontend
)

$ErrorActionPreference = "Stop"

# Load .env variables
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$' } | ForEach-Object {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8080" }
$env:SERVER_PORT = $port
$frontendPort = "3000"

$ip = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -notlike "127.*" -and
        $_.IPAddress -notlike "169.254.*" -and
        $_.PrefixOrigin -ne "WellKnown" -and
        $_.InterfaceAlias -notmatch "VMware|Virtual|vEthernet|Loopback"
    } |
    Select-Object -First 1 -ExpandProperty IPAddress

if (-not $ip) { $ip = "localhost" }

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "                HomeCloud - Self-Hosted Cloud             " -ForegroundColor White -BackgroundColor DarkBlue
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host " [Frontend Web UI]  http://localhost:$frontendPort  (LAN: http://$ip`:$frontendPort)" -ForegroundColor Green
Write-Host " [Backend API]     http://localhost:$port  (LAN: http://$ip`:$port)" -ForegroundColor Green
Write-Host " [Health Check]    http://localhost:$port/api/health" -ForegroundColor Gray
Write-Host ""

$backendDir = Join-Path $PSScriptRoot "backend"
$frontendDir = Join-Path $PSScriptRoot "frontend"

if (-not $OnlyFrontend) {
    Write-Host "[1/2] Launching Backend (Spring Boot on port $port)..." -ForegroundColor Yellow
    $backendCmd = "cd '$backendDir'; mvn.cmd spring-boot:run"
    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $backendCmd
}

if (-not $OnlyBackend) {
    Write-Host "[2/2] Launching Frontend (Vite on port $frontendPort)..." -ForegroundColor Yellow
    $frontendCmd = "cd '$frontendDir'; npm.cmd run dev"
    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $frontendCmd
}

Write-Host ""
Write-Host "Both services are starting in dedicated terminal windows." -ForegroundColor Cyan
Write-Host "Open http://localhost:$frontendPort in your web browser." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
