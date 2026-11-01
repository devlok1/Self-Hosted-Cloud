# Run this script in Windows PowerShell AS ADMINISTRATOR on the HomeCloud host PC.
# It allows the HomeCloud Web UI and API through the Windows Firewall for LAN and Tailscale access.

# Load ports: Frontend (3000) and Backend (8080, 8081, or SERVER_PORT from .env)
$envFile = Join-Path $PSScriptRoot ".env"
$backendPort = @("8080", "8081")
if (Test-Path $envFile) {
    $line = Get-Content $envFile | Where-Object { $_ -match '^\s*SERVER_PORT\s*=\s*(.*)$' } | Select-Object -First 1
    if ($line) { $backendPort = $matches[1].Trim() }
}

$ports = @("3000", "8080", "8081")
if ($backendPort -and $backendPort -notin $ports) {
    $ports += $backendPort
}

$ruleName = "HomeCloud Web & API"

$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($existing) {
    Set-NetFirewallRule `
        -DisplayName $ruleName `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort $ports `
        -Action Allow `
        -Profile Any
    Write-Host "Updated firewall rule: $ruleName for TCP ports $($ports -join ', ')" -ForegroundColor Green
}
else {
    New-NetFirewallRule `
        -DisplayName $ruleName `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort $ports `
        -Action Allow `
        -Profile Any
    Write-Host "Created firewall rule: $ruleName for TCP ports $($ports -join ', ')" -ForegroundColor Green
}

Write-Host "HomeCloud ports $($ports -join ', ') are now allowed through Windows Firewall for LAN & Tailscale." -ForegroundColor Cyan
