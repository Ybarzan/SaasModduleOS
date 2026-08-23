#!/bin/sh
set -e

# ── Configuration ──────────────────────────────────────────────────
BACKUP_DIR="/backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/incokalk_${DATE}.sql.gz"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

mkdir -p "${BACKUP_DIR}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting backup..."

# ── Dump PostgreSQL ────────────────────────────────────────────────
pg_dump -U "${PGUSER}" -d "${PGDATABASE}" \
    --format=plain \
    --no-owner \
    --no-privileges \
    --verbose 2>/dev/null | gzip > "${BACKUP_FILE}"

BACKUP_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup created: ${BACKUP_FILE} (${BACKUP_SIZE})"

# ── Upload to MinIO ───────────────────────────────────────────────
if command -v mc > /dev/null 2>&1; then
    mc alias set backup-minio "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}" --api S3v4 2>/dev/null
    mc cp "${BACKUP_FILE}" "backup-minio/incokalk-backups/postgres/${DATE}.sql.gz" 2>/dev/null && \
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] Uploaded to MinIO: postgres/${DATE}.sql.gz" || \
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] WARNING: MinIO upload failed"
fi

# ── Cleanup old local backups ──────────────────────────────────────
DELETED=$(find "${BACKUP_DIR}" -name "incokalk_*.sql.gz" -mtime +${RETENTION_DAYS} -delete -print | wc -l)
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleaned ${DELETED} old backup(s) (retention: ${RETENTION_DAYS} days)"

# ── Cleanup old MinIO backups ──────────────────────────────────────
if command -v mc > /dev/null 2>&1; then
    mc rm --recursive --force --older-than "${RETENTION_DAYS}d" "backup-minio/incokalk-backups/postgres/" 2>/dev/null || true
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup completed successfully"
