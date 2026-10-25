# HomeCloud LAN starter for Windows PowerShell.
# Run this from the project root or from the backend directory.

$backend = Join-Path $PSScriptRoot "backend"
if (-not (Test-Path $backend)) {
    $backend = $PSScriptRoot
}

$ip = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -notlike "127.*" -and
        $_.IPAddress -notlike "169.254.*" -and
        $_.PrefixOrigin -ne "WellKnown"
    } |
    Select-Object -First 1 -ExpandProperty IPAddress

Write-Host "HomeCloud LAN API: http://$ip`:8080"
Write-Host "Health check:       http://$ip`:8080/api/health"
Write-Host ""
Write-Host "Start the backend now..."

Set-Location $backend

if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd spring-boot:run
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn spring-boot:run
} else {
    Write-Error "Maven was not found. Open the backend in IntelliJ or install Maven."
}
