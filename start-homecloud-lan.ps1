# HomeCloud LAN / Multi-Device Launcher
# Starts both Spring Boot backend and Vite frontend with full LAN / Tailscale diagnostic reporting

param (
    [switch]$OnlyBackend,
    [switch]$OnlyFrontend,
    [switch]$NoBrowser
)

$startScript = Join-Path $PSScriptRoot "start-homecloud.ps1"
& $startScript -LanMode -OnlyBackend:$OnlyBackend -OnlyFrontend:$OnlyFrontend -NoBrowser:$NoBrowser @args


