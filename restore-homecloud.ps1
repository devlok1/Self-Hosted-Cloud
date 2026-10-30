# HomeCloud — Automated Restoration Script (PowerShell)
# Restores both PostgreSQL metadata and physical user storage from a backup archive.

param(
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [switch]$Force = $false
)

$ErrorActionPreference = "Stop"

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host "   HomeCloud Restore Utility                    " -ForegroundColor Cyan
Write-Host "================================================`n" -ForegroundColor Cyan

if (-not (Test-Path $BackupFile)) {
    Write-Host "ERROR: Backup archive not found: $BackupFile" -ForegroundColor Red
    exit 1
}

# 1. Load environment variables from .env if present
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^[^#=]+=' } | ForEach-Object {
        $name, $value = $_.Split('=', 2)
        [System.Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim())
    }
}

$dbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "homecloud" }
$dbUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "homecloud" }
$dbPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { $env:DB_PASSWORD }
$storageDir = if ($env:STORAGE_LOCATION) { $env:STORAGE_LOCATION } else { "HomeCloudStorage" }

if (-not $Force) {
    Write-Host "WARNING: This will overwrite existing database records and storage files in '$storageDir'!" -ForegroundColor Yellow
    $confirm = Read-Host "Type 'YES' to proceed with restoration"
    if ($confirm -ne "YES") {
        Write-Host "Restoration cancelled by user." -ForegroundColor Gray
        exit 0
    }
}

$tempExtractDir = Join-Path $PSScriptRoot "restore_temp_extract"
if (Test-Path $tempExtractDir) {
    Remove-Item -Path $tempExtractDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $tempExtractDir | Out-Null

try {
    # 2. Extract Archive
    Write-Host "[1/3] Extracting backup archive..." -ForegroundColor Yellow
    Expand-Archive -Path $BackupFile -DestinationPath $tempExtractDir -Force

    $sqlDumpFile = Join-Path $tempExtractDir "database.sql"
    if (-not (Test-Path $sqlDumpFile)) {
        throw "Archive is invalid: database.sql not found inside backup."
    }

    # 3. Restore PostgreSQL Database
    Write-Host "[2/3] Restoring PostgreSQL database ($dbName)..." -ForegroundColor Yellow

    $useDocker = $false
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $containerStatus = docker compose ps --services --filter "status=running" 2>$null
        if ($containerStatus -match "postgres") {
            $useDocker = $true
        }
    }

    if ($useDocker) {
        Write-Host "      Detected running Docker Postgres container. Restoring via container..." -ForegroundColor Gray
        docker compose exec -T postgres psql -U $dbUser -d $dbName < $sqlDumpFile
    } else {
        if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
            throw "psql command not found in PATH and Docker container is not running."
        }
        $env:PGPASSWORD = $dbPassword
        psql -U $dbUser -h localhost -p 5432 -d $dbName -f $sqlDumpFile
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }
    Write-Host "      Database restored successfully." -ForegroundColor Green

    # 4. Restore Storage Files
    Write-Host "[3/3] Restoring user file storage..." -ForegroundColor Yellow
    $extractedStorage = Join-Path $tempExtractDir "storage"
    $targetStorage = Join-Path $PSScriptRoot $storageDir

    if (Test-Path $extractedStorage) {
        New-Item -ItemType Directory -Force -Path $targetStorage | Out-Null
        Copy-Item -Path "$extractedStorage\*" -Destination $targetStorage -Recurse -Force
        $fileCount = (Get-ChildItem -Path $targetStorage -Recurse -File).Count
        Write-Host "      Restored $fileCount storage file(s) to $storageDir." -ForegroundColor Green
    }

    Write-Host "`nSUCCESS: HomeCloud restoration completed successfully!`n" -ForegroundColor Green

} catch {
    Write-Host "`nERROR: Restoration failed: $_" -ForegroundColor Red
    exit 1
} finally {
    if (Test-Path $tempExtractDir) {
        Remove-Item -Path $tempExtractDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
