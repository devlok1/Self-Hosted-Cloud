# Run this script in Windows PowerShell AS ADMINISTRATOR on the HomeCloud host PC.
# It allows the Spring Boot LAN API through the Windows Private network profile only.

$ruleName = "HomeCloud Spring Boot 8080"

$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "Firewall rule already exists: $ruleName"
} else {
    New-NetFirewallRule `
        -DisplayName $ruleName `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort 8080 `
        -Action Allow `
        -Profile Private

    Write-Host "Created firewall rule: $ruleName"
}

Write-Host "HomeCloud LAN port 8080 is allowed on the Private network profile."
