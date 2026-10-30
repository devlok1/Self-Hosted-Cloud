# HomeCloud — Automated Backup Script (PowerShell)
# Backs up both PostgreSQL metadata and physical user storage files.

param(
    [string]$OutputDir = "backups",
    [switch]$Docker = $false
)

$ErrorActionPreference = "Stop"

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host "   HomeCloud Backup Utility                     " -ForegroundColor Cyan
Write-Host "================================================`n" -ForegroundColor Cyan

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

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupName = "homecloud_backup_$timestamp"
$targetBackupDir = Join-Path $PSScriptRoot (Join-Path $OutputDir $backupName)
$finalZipPath = Join-Path $PSScriptRoot (Join-Path $OutputDir "$backupName.zip")

New-Item -ItemType Directory -Force -Path $targetBackupDir | Out-Null

try {
    # 2. Dump PostgreSQL Database
    Write-Host "[1/3] Dumping PostgreSQL database ($dbName)..." -ForegroundColor Yellow
    $sqlDumpFile = Join-Path $targetBackupDir "database.sql"

    # Check if running in Docker mode or local pg_dump
    $useDockerDump = $false
    if ($Docker -or (Get-Command docker -ErrorAction SilentlyContinue)) {
        $containerStatus = docker compose ps --services --filter "status=running" 2>$null
        if ($containerStatus -match "postgres") {
            $useDockerDump = $true
        }
    }

    if ($useDockerDump) {
        Write-Host "      Detected running Docker Postgres container. Using container pg_dump..." -ForegroundColor Gray
        docker compose exec -T postgres pg_dump -U $dbUser $dbName > $sqlDumpFile
    } else {
        if (-not (Get-Command pg_dump -ErrorAction SilentlyContinue)) {
            throw "pg_dump was not found in PATH and Docker container is not running. Please install PostgreSQL command line tools or start Docker."
        }
        $env:PGPASSWORD = $dbPassword
        pg_dump -U $dbUser -h localhost -p 5432 -d $dbName -f $sqlDumpFile
        Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    }

    $sqlSize = (Get-Item $sqlDumpFile).Length
    Write-Host "      Database dumped successfully ($([Math]::Round($sqlSize / 1KB, 2)) KB)." -ForegroundColor Green

    # 3. Backup User Storage Files
    Write-Host "[2/3] Backing up physical file storage..." -ForegroundColor Yellow
    $backupStorageDir = Join-Path $targetBackupDir "storage"
    New-Item -ItemType Directory -Force -Path $backupStorageDir | Out-Null

    $resolvedStorage = Join-Path $PSScriptRoot $storageDir
    if (Test-Path $resolvedStorage) {
        Copy-Item -Path "$resolvedStorage\*" -Destination $backupStorageDir -Recurse -Force -ErrorAction SilentlyContinue
        $fileCount = (Get-ChildItem -Path $backupStorageDir -Recurse -File).Count
        Write-Host "      Copied $fileCount storage file(s)." -ForegroundColor Green
    } else {
        Write-Host "      Storage directory $storageDir does not exist yet; skipping files." -ForegroundColor Gray
    }

    # 4. Create Metadata Manifest
    $manifestPath = Join-Path $targetBackupDir "backup_manifest.json"
    $manifest = @{
        version = "1.0"
        timestamp = (Get-Date).ToString("o")
        database = $dbName
        dbUser = $dbUser
    } | ConvertTo-Json
    Set-Content -Path $manifestPath -Value $manifest

    # 5. Compress into ZIP Archive
    Write-Host "[3/3] Compressing backup archive..." -ForegroundColor Yellow
    Compress-Archive -Path "$targetBackupDir\*" -DestinationPath $finalZipPath -Force
    Remove-Item -Path $targetBackupDir -Recurse -Force

    $zipSize = (Get-Item $finalZipPath).Length
    Write-Host "`nSUCCESS: Backup created successfully!" -ForegroundColor Green
    Write-Host "Archive: $finalZipPath ($([Math]::Round($zipSize / 1MB, 2)) MB)`n" -ForegroundColor Cyan

} catch {
    Write-Host "`nERROR: Backup failed: $_" -ForegroundColor Red
    if (Test-Path $targetBackupDir) {
        Remove-Item -Path $targetBackupDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    exit 1
}
