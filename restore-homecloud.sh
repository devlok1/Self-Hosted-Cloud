#!/usr/bin/env bash
# HomeCloud — Automated Restoration Script (Linux / macOS)

set -euo pipefail

BACKUP_ARCHIVE="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -z "$BACKUP_ARCHIVE" ] || [ ! -f "$BACKUP_ARCHIVE" ]; then
    echo "Usage: ./restore-homecloud.sh <path-to-homecloud_backup_*.tar.gz>" >&2
    exit 1
fi

echo "================================================"
echo "   HomeCloud Restore Utility                    "
echo "================================================"

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

echo "WARNING: This will overwrite current records in '${DB_NAME}' and files in '${STORAGE_DIR}'!"
read -r -p "Type 'YES' to proceed: " CONFIRM
if [ "$CONFIRM" != "YES" ]; then
    echo "Aborted by user."
    exit 0
fi

TEMP_EXTRACT=$(mktemp -d "${SCRIPT_DIR}/restore_temp_XXXXXX")
cleanup() {
    rm -rf "$TEMP_EXTRACT"
}
trap cleanup EXIT

echo "[1/3] Extracting archive..."
tar -xzf "$BACKUP_ARCHIVE" -C "$TEMP_EXTRACT"

# Find database.sql inside extracted content
SQL_FILE=$(find "$TEMP_EXTRACT" -name "database.sql" -print -quit)
if [ -z "$SQL_FILE" ]; then
    echo "ERROR: database.sql not found in backup archive." >&2
    exit 1
fi

echo "[2/3] Restoring database..."
if docker compose ps --services --filter "status=running" 2>/dev/null | grep -q "postgres"; then
    docker compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" < "$SQL_FILE"
elif command -v psql >/dev/null 2>&1; then
    PGPASSWORD="$DB_PASS" psql -U "$DB_USER" -h localhost -p 5432 -d "$DB_NAME" -f "$SQL_FILE"
else
    echo "ERROR: psql not found and Docker postgres not running." >&2
    exit 1
fi

echo "[3/3] Restoring user storage files..."
STORAGE_SOURCE=$(find "$TEMP_EXTRACT" -type d -name "storage" -print -quit)
if [ -n "$STORAGE_SOURCE" ] && [ -d "$STORAGE_SOURCE" ]; then
    mkdir -p "${SCRIPT_DIR}/${STORAGE_DIR}"
    cp -r "${STORAGE_SOURCE}/." "${SCRIPT_DIR}/${STORAGE_DIR}/"
fi

echo ""
echo "SUCCESS: HomeCloud restored successfully from ${BACKUP_ARCHIVE}!"
