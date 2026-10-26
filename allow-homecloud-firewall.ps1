# Run this script in Windows PowerShell AS ADMINISTRATOR on the HomeCloud host PC.
# It allows the Spring Boot LAN API through the Windows Private network profile only.

# Load port from .env if available, default to 8081, 8080
$ports = @("8081", "8080")
$ruleName = "HomeCloud Spring Boot API"

$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Firewall rule already exists: $ruleName"
} else {
    New-NetFirewallRule `
        -DisplayName $ruleName `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort $ports `
        -Action Allow `
        -Profile Private

    Write-Host "Created firewall rule: $ruleName for ports $($ports -join ', ')"
}

Write-Host "HomeCloud LAN ports $($ports -join ', ') are allowed on the Private network profile."
