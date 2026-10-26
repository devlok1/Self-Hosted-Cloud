# Load .env variables into current process if .env exists
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$' } | ForEach-Object {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8080" }

$backend = Join-Path $PSScriptRoot "backend"
if (-not (Test-Path $backend)) {
    $backend = $PSScriptRoot
}

$ip = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -notlike "127.*" -and
        $_.IPAddress -notlike "169.254.*" -and
        $_.PrefixOrigin -ne "WellKnown" -and
        $_.InterfaceAlias -notmatch "VMware|Virtual|vEthernet|Loopback"
    } |
    Select-Object -First 1 -ExpandProperty IPAddress

Write-Host "HomeCloud LAN API: http://$ip`:$port"
Write-Host "Health check:       http://$ip`:$port/api/health"
Write-Host "Local API:          http://localhost:$port"
Write-Host ""

# Check if target port is occupied
$portCheck = Get-NetTCPConnection -LocalPort ([int]$port) -State Listen -ErrorAction SilentlyContinue
if ($portCheck) {
    $proc = Get-Process -Id $portCheck[0].OwningProcess -ErrorAction SilentlyContinue
    Write-Warning "Port $port is currently in use by process '$($proc.ProcessName)' (PID: $($portCheck[0].OwningProcess))."
    Write-Warning "If this is not HomeCloud, change SERVER_PORT in .env or stop that service."
    Write-Host ""
}

Write-Host "Starting the backend now..."

Set-Location $backend

if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd spring-boot:run
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn spring-boot:run
} else {
    Write-Error "Maven was not found. Open the backend in IntelliJ or install Maven."
}

