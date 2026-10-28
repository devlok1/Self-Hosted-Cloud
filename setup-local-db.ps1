# HomeCloud Local PostgreSQL Setup Script
# Automatically creates the database and user matching your .env file
# Run this once on your host machine if using local PostgreSQL instead of Docker.

$ErrorActionPreference = "Stop"

# 1. Load .env variables
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$' } | ForEach-Object {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$dbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "homecloud" }
$dbPass = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "Your_Secret" }
$dbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "homecloud" }

# 2. Find psql executable
$psql = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $customPaths = @(
        "C:\ARKSET\Custom Programs\Postgresql\bin\psql.exe",
        "C:\Program Files\PostgreSQL\18\bin\psql.exe",
        "C:\Program Files\PostgreSQL\17\bin\psql.exe",
        "C:\Program Files\PostgreSQL\16\bin\psql.exe"
    )
    foreach ($path in $customPaths) {
        if (Test-Path $path) {
            $psql = $path
            break
        }
    }
}

if (-not $psql) {
    Write-Error "Could not find psql.exe. Please ensure PostgreSQL is installed."
    exit 1
}

Write-Host "Found PostgreSQL client at: $psql" -ForegroundColor Cyan
Write-Host "Setting up HomeCloud database..." -ForegroundColor Yellow
Write-Host " Target Database: $dbName"
Write-Host " Target User:     $dbUser"
Write-Host ""

$tempSql = Join-Path $env:TEMP "homecloud-setup-$([System.Guid]::NewGuid().ToString('N')).sql"

$sqlScript = @"
DO
`$do`$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$dbUser') THEN
      CREATE ROLE $dbUser LOGIN PASSWORD '$dbPass';
   ELSE
      ALTER ROLE $dbUser WITH PASSWORD '$dbPass';
   END IF;
END
`$do`$;

SELECT 'CREATE DATABASE $dbName OWNER $dbUser'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$dbName')\gexec

GRANT ALL PRIVILEGES ON DATABASE $dbName TO $dbUser;
"@

Set-Content -Path $tempSql -Value $sqlScript -Encoding UTF8

Write-Host "Enter your PostgreSQL superuser ('postgres') password if prompted:" -ForegroundColor Cyan
try {
    & $psql -U postgres -p 5432 -h localhost -f $tempSql
    Write-Host ""
    Write-Host "Database '$dbName' and user '$dbUser' are ready!" -ForegroundColor Green
    Write-Host "You can now run: .\start-homecloud.ps1" -ForegroundColor Green
} catch {
    Write-Error "Failed to execute setup script. Please verify your postgres password."
} finally {
    if (Test-Path $tempSql) {
        Remove-Item -Force $tempSql
    }
}
