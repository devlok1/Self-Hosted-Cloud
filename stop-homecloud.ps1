# HomeCloud Shutdown Script
# Gracefully stops backend (Java) and frontend (Node/Vite) servers

$envFile = Join-Path $PSScriptRoot ".env"
$backendPort = "8080"
if (Test-Path $envFile) {
    $line = Get-Content $envFile | Where-Object { $_ -match '^\s*SERVER_PORT\s*=\s*(.*)$' } | Select-Object -First 1
    if ($line) { $backendPort = $matches[1].Trim() }
}

$ports = @([int]$backendPort, 8080, 8081, 3000) | Select-Object -Unique
$stoppedAny = $false

Write-Host "Checking for HomeCloud services on ports $($ports -join ', ')..." -ForegroundColor Cyan

foreach ($port in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pidToKill in $pids) {
            $proc = Get-Process -Id $pidToKill -ErrorAction SilentlyContinue
            if ($proc) {
                # Safety check: ONLY terminate Java (Spring Boot) and Node (Vite), never unrelated system daemons like httpd
                if ($proc.ProcessName -match "java|javaw|node") {
                    Write-Host "Stopping HomeCloud process '$($proc.ProcessName)' (PID: $pidToKill) listening on port $port..." -ForegroundColor Yellow
                    Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                    $stoppedAny = $true
                } else {
                    Write-Host "Skipping unrelated process '$($proc.ProcessName)' (PID: $pidToKill) on port $port (not a HomeCloud service)." -ForegroundColor Gray
                }
            }
        }
    }
}

if ($stoppedAny) {
    Write-Host "HomeCloud services have been stopped successfully." -ForegroundColor Green
} else {
    Write-Host "No active HomeCloud Java or Node processes found listening on ports $($ports -join ', ')." -ForegroundColor Gray
}
