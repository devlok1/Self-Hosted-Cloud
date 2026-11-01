<#
.SYNOPSIS
    HomeCloud Database Migration Runner
.DESCRIPTION
    Applies versioned SQL migrations to PostgreSQL idempotently and safely.
    Preserves all existing data, users, files, folders and relationships.
#>

param (
    [string]$MigrationFile = ""
)

$ErrorActionPreference = "Stop"

# Load .env variables
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*([^#=\s]+)\s*=\s*(.*)$' } | ForEach-Object {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$dbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "homecloud" }
$dbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "homecloud" }
$dbPort = if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }
$dbHost = "localhost"

# Locate psql executable
$psqlPaths = @(
    "C:\ARKSET\Custom Programs\Postgresql\bin\psql.exe",
    (Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
    "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    "C:\Program Files\PostgreSQL\16\bin\psql.exe",
    "C:\Program Files\PostgreSQL\15\bin\psql.exe"
) | Where-Object { $_ -and (Test-Path $_) }

$validPaths = @($psqlPaths)
if ($validPaths.Count -eq 0) {
    Write-Error "Could not locate 'psql.exe'. Please ensure PostgreSQL client tools are installed."
    exit 1
}

$psql = "$($validPaths[0])"

$env:PGPASSWORD = $env:DB_PASSWORD

Write-Host "Connecting to PostgreSQL database '$dbName' on ${dbHost}:${dbPort} as user '$dbUser'..." -ForegroundColor Cyan

$migrationsDir = Join-Path $PSScriptRoot "migrations"
$filesToRun = @()

if ($MigrationFile) {
    $fullPath = if (Test-Path $MigrationFile) { $MigrationFile } else { Join-Path $migrationsDir $MigrationFile }
    if (-not (Test-Path $fullPath)) {
        Write-Error "Specified migration file '$MigrationFile' not found."
        exit 1
    }
    $filesToRun += $fullPath
} else {
    if (Test-Path $migrationsDir) {
        $filesToRun = Get-ChildItem -Path $migrationsDir -Filter "*.sql" | Sort-Object Name | Select-Object -ExpandProperty FullName
    }
}

if ($filesToRun.Count -eq 0) {
    Write-Host "No migration files found." -ForegroundColor Yellow
    exit 0
}

foreach ($file in $filesToRun) {
    $fileName = Split-Path -Leaf $file
    Write-Host "Applying migration: $fileName..." -ForegroundColor Yellow
    $res = & $psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $file 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Migration failed for '$fileName': $res"
        exit 1
    }
    Write-Host "  -> Successfully applied: $fileName" -ForegroundColor Green
}

Write-Host ""
Write-Host "Verifying 'files' table schema and status backfill..." -ForegroundColor Cyan
$verifyQuery = "SELECT COUNT(*) AS total_files, COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_files, COUNT(*) FILTER (WHERE status IS NULL) AS null_status_files FROM files;"
& $psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -c $verifyQuery

Write-Host ""
Write-Host "Database migration completed successfully!" -ForegroundColor Green
