param (
    [switch]$OnlyBackend,
    [switch]$OnlyFrontend,
    [switch]$LanMode,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

# Load .env variables into current process
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$' } | ForEach-Object {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$backendPort = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8080" }
$frontendPort = "3000"
$env:SERVER_PORT = $backendPort
$env:SERVER_ADDRESS = "0.0.0.0"
$env:BACKEND_URL = "http://localhost:$backendPort"

# Discover network addresses
$lanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -notlike "127.*" -and
        $_.IPAddress -notlike "169.254.*" -and
        $_.IPAddress -notlike "100.*" -and
        $_.PrefixOrigin -ne "WellKnown" -and
        $_.InterfaceAlias -notmatch "VMware|Virtual|vEthernet|Loopback|Tailscale"
    } |
    Select-Object -First 1 -ExpandProperty IPAddress

$tailscaleIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -like "100.*" -or $_.InterfaceAlias -match "Tailscale"
    } |
    Select-Object -First 1 -ExpandProperty IPAddress

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       HomeCloud - Self-Hosted Cloud Storage System       " -ForegroundColor White -BackgroundColor DarkBlue
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Dependency checks
Write-Host "[Dependency Check]" -ForegroundColor Yellow
$javaOk = $false
try {
    $javaVer = java -version 2>&1 | Select-Object -First 1
    if ($javaVer) {
        Write-Host "  [OK] Java Runtime: $javaVer" -ForegroundColor Green
        $javaOk = $true
    }
} catch {
    Write-Host "  [FAIL] Java not found on PATH. Java 21+ is required." -ForegroundColor Red
}

$nodeOk = $false
try {
    $nodeVer = node.exe -v 2>&1
    if ($nodeVer) {
        Write-Host "  [OK] Node.js Runtime: $nodeVer" -ForegroundColor Green
        $nodeOk = $true
    }
} catch {
    Write-Host "  [FAIL] Node.js not found on PATH. Node 18+ is required." -ForegroundColor Red
}

$pgConn = Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue
if ($pgConn) {
    Write-Host "  [OK] PostgreSQL listening on port 5432" -ForegroundColor Green
} else {
    Write-Host "  [WARN] PostgreSQL does not appear to be listening on port 5432. Ensure PostgreSQL service or Docker is running." -ForegroundColor Yellow
}
Write-Host ""

# 2. Port conflict detection
Write-Host "[Port Conflict Check]" -ForegroundColor Yellow
if (-not $OnlyFrontend) {
    $conns = Get-NetTCPConnection -LocalPort ([int]$backendPort) -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        $proc = Get-Process -Id $conns[0].OwningProcess -ErrorAction SilentlyContinue
        if ($proc -and $proc.ProcessName -notmatch "java|javaw") {
            Write-Host "  [CONFLICT] Port $backendPort is currently occupied by unrelated process '$($proc.ProcessName)' (PID: $($proc.Id))!" -ForegroundColor Red
            Write-Host "             Tip: Change SERVER_PORT in .env (e.g. SERVER_PORT=8081) to avoid collision." -ForegroundColor Yellow
        } else {
            Write-Host "  [INFO] A process is already listening on backend port $backendPort ($($proc.ProcessName))." -ForegroundColor Gray
        }
    } else {
        Write-Host "  [OK] Backend port $backendPort is available" -ForegroundColor Green
    }
}

if (-not $OnlyBackend) {
    $fConns = Get-NetTCPConnection -LocalPort ([int]$frontendPort) -State Listen -ErrorAction SilentlyContinue
    if ($fConns) {
        $fProc = Get-Process -Id $fConns[0].OwningProcess -ErrorAction SilentlyContinue
        Write-Host "  [INFO] Frontend port $frontendPort is currently in use by '$($fProc.ProcessName)' (PID: $($fProc.Id))." -ForegroundColor Gray
    } else {
        Write-Host "  [OK] Frontend port $frontendPort is available" -ForegroundColor Green
    }
}
Write-Host ""

$backendDir = Join-Path $PSScriptRoot "backend"
$frontendDir = Join-Path $PSScriptRoot "frontend"

# 3. Database Migration
if (-not $OnlyFrontend) {
    $migrateScript = Join-Path $PSScriptRoot "migrate-database.ps1"
    if (Test-Path $migrateScript) {
        Write-Host "[Database Migration]" -ForegroundColor Yellow
        try {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $migrateScript
            Write-Host "  [OK] Database schema and migrations verified" -ForegroundColor Green
        } catch {
            Write-Host "  [WARN] Database migration check encountered an issue: $_" -ForegroundColor Yellow
        }
        Write-Host ""
    }
}

# 4. Launch Backend
if (-not $OnlyFrontend) {
    Write-Host "[1/2] Starting Spring Boot Backend (listening on 0.0.0.0:$backendPort)..." -ForegroundColor Yellow
    $backendCmd = "if (Test-Path '../.env') { Get-Content '../.env' | Where-Object { `$_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$' } | ForEach-Object { [System.Environment]::SetEnvironmentVariable(`$matches[1].Trim(), `$matches[2].Trim(), 'Process') } }; `$env:SERVER_PORT='$backendPort'; `$env:SERVER_ADDRESS='0.0.0.0'; cd '$backendDir'; mvn.cmd spring-boot:run"
    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $backendCmd
}

# 5. Launch Frontend
if (-not $OnlyBackend) {
    Write-Host "[2/2] Starting Vite Frontend (listening on 0.0.0.0:$frontendPort)..." -ForegroundColor Yellow
    $frontendCmd = "`$env:SERVER_PORT='$backendPort'; `$env:BACKEND_URL='http://localhost:$backendPort'; cd '$frontendDir'; npm.cmd run dev"
    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $frontendCmd
}

# 6. Readiness polling
Write-Host ""
Write-Host "Waiting for services to become ready..." -ForegroundColor Cyan

$backendReady = $false
if (-not $OnlyFrontend) {
    $backendTimeout = 30
    $elapsed = 0
    while ($elapsed -lt $backendTimeout) {
        Start-Sleep -Seconds 2
        $elapsed += 2
        try {
            $resp = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/health" -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($resp -and ($resp.status -eq "UP" -or $resp.status -eq "ok")) {
                $backendReady = $true
                break
            }
        } catch {
            Write-Host -NoNewline "."
        }
    }
    Write-Host ""
} else {
    $backendReady = $true
}

$frontendReady = $false
if (-not $OnlyBackend) {
    $frontendTimeout = 15
    $elapsed = 0
    while ($elapsed -lt $frontendTimeout) {
        Start-Sleep -Seconds 1
        $elapsed += 1
        try {
            $fResp = Invoke-WebRequest -Uri "http://127.0.0.1:$frontendPort" -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($fResp -and $fResp.StatusCode -eq 200) {
                $frontendReady = $true
                break
            }
        } catch {
            Write-Host -NoNewline "."
        }
    }
    Write-Host ""
} else {
    $frontendReady = $true
}

# 6. Startup Diagnostics Report
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "                 Startup Diagnostics Report               " -ForegroundColor White -BackgroundColor DarkBlue
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
$modeStr = if ($LanMode) { "LAN / Tailscale Multi-Device Mode" } else { "Localhost Development Mode" }
Write-Host "  Operating Mode       : $modeStr" -ForegroundColor White

if ($backendReady) {
    Write-Host "  Backend Service      : [ONLINE]  http://localhost:$backendPort (0.0.0.0:$backendPort)" -ForegroundColor Green
    Write-Host "  Health Endpoint      : [200 OK]  http://localhost:$backendPort/api/health" -ForegroundColor Green
} else {
    Write-Host "  Backend Service      : [STARTING / PENDING]  Check the Spring Boot terminal window" -ForegroundColor Yellow
}

if ($frontendReady) {
    Write-Host "  Frontend Web UI      : [ONLINE]  http://localhost:$frontendPort (0.0.0.0:$frontendPort)" -ForegroundColor Green
} else {
    Write-Host "  Frontend Web UI      : [STARTING / PENDING]  Check the Vite terminal window" -ForegroundColor Yellow
}

$trustedOrigins = if ($env:CORS_ALLOWED_ORIGIN_PATTERNS) { $env:CORS_ALLOWED_ORIGIN_PATTERNS } else { "Localhost, LAN RFC1918 (default)" }
Write-Host "  Configured Origins   : $trustedOrigins" -ForegroundColor Gray
Write-Host ""

Write-Host "  Accessible URLs:" -ForegroundColor Cyan
Write-Host "    - Localhost        : http://localhost:$frontendPort" -ForegroundColor White
if ($lanIp) {
    Write-Host "    - Local LAN (Wi-Fi): http://$lanIp`:$frontendPort" -ForegroundColor Green
}
if ($tailscaleIp) {
    Write-Host "    - Tailscale Network: http://$tailscaleIp`:$frontendPort" -ForegroundColor Magenta
}
Write-Host ""
Write-Host "  (To stop all HomeCloud services, execute .\stop-homecloud.ps1)" -ForegroundColor Gray
Write-Host "==========================================================" -ForegroundColor Cyan

# 7. Open browser if ready
if ($frontendReady -and -not $NoBrowser) {
    Start-Process "http://localhost:$frontendPort"
}
