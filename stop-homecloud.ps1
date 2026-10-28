# HomeCloud Shutdown Script
# Gracefully stops backend and frontend servers

$ports = @(8081, 3000)
$stoppedAny = $false

Write-Host "Stopping HomeCloud services..." -ForegroundColor Cyan

foreach ($port in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($pidToKill in $pids) {
            $proc = Get-Process -Id $pidToKill -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Host "Stopping process '$($proc.ProcessName)' (PID: $pidToKill) on port $port..." -ForegroundColor Yellow
                Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                $stoppedAny = $true
            }
        }
    }
}

if ($stoppedAny) {
    Write-Host "HomeCloud servers (ports 8081 & 3000) have been stopped successfully." -ForegroundColor Green
} else {
    Write-Host "No HomeCloud servers were found running on ports 8081 or 3000." -ForegroundColor Gray
}
