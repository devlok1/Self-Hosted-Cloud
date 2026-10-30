#!/usr/bin/env bash
# HomeCloud — Automated Backup Script (Linux / macOS)
# Backs up both PostgreSQL metadata and physical user storage.

set -euo pipefail

OUTPUT_DIR="${1:-backups}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "================================================"
echo "   HomeCloud Backup Utility                     "
echo "================================================"

# 1. Load environment variables
if [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    source "$SCRIPT_DIR/.env"
    set +a
fi

DB_NAME="${POSTGRES_DB:-homecloud}"
DB_USER="${POSTGRES_USER:-homecloud}"
DB_PASS="${POSTGRES_PASSWORD:-${DB_PASSWORD:-}}"
STORAGE_DIR="${STORAGE_LOCATION:-HomeCloudStorage}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_NAME="homecloud_backup_${TIMESTAMP}"
TARGET_DIR="${SCRIPT_DIR}/${OUTPUT_DIR}/${BACKUP_NAME}"
FINAL_ARCHIVE="${SCRIPT_DIR}/${OUTPUT_DIR}/${BACKUP_NAME}.tar.gz"

mkdir -p "$TARGET_DIR"

cleanup() {
    rm -rf "$TARGET_DIR"
}
trap cleanup EXIT

# 2. Dump PostgreSQL Database
echo "[1/3] Dumping PostgreSQL database (${DB_NAME})..."
SQL_DUMP="${TARGET_DIR}/database.sql"

if docker compose ps --services --filter "status=running" 2>/dev/null | grep -q "postgres"; then
    echo "      Using Docker postgres container..."
    docker compose exec -T postgres pg_dump -U "$DB_USER" "$DB_NAME" > "$SQL_DUMP"
elif command -v pg_dump >/dev/null 2>&1; then
    PGPASSWORD="$DB_PASS" pg_dump -U "$DB_USER" -h localhost -p 5432 -d "$DB_NAME" -f "$SQL_DUMP"
else
    echo "ERROR: pg_dump not found and Docker container not running." >&2
    exit 1
fi

echo "      Database dumped successfully ($(du -h "$SQL_DUMP" | cut -f1))."

# 3. Copy physical files
echo "[2/3] Backing up physical file storage..."
mkdir -p "${TARGET_DIR}/storage"
if [ -d "${SCRIPT_DIR}/${STORAGE_DIR}" ]; then
    cp -r "${SCRIPT_DIR}/${STORAGE_DIR}/." "${TARGET_DIR}/storage/"
    echo "      Copied storage files."
fi

# 4. Create archive
echo "[3/3] Compressing backup archive..."
tar -czf "$FINAL_ARCHIVE" -C "${SCRIPT_DIR}/${OUTPUT_DIR}" "$BACKUP_NAME"

echo ""
echo "SUCCESS: Backup created successfully!"
echo "Archive: ${FINAL_ARCHIVE} ($(du -h "$FINAL_ARCHIVE" | cut -f1))"
