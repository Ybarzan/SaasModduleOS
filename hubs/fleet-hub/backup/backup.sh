#!/bin/sh
set -eu

# Backup quotidien PostgreSQL (dumps compressés dans /backups, prunés après BACKUP_KEEP_DAYS).
# Variables d'environnement : DB_HOST, DB_USER, DB_PASSWORD, BACKUP_KEEP_DAYS, BACKUP_INTERVAL_HOURS.
#
# Un dump uniquement local ne survit pas à la perte du VPS. Deux mécanismes optionnels :
#  - RCLONE_REMOTE (ex. "s3:mon-bucket/backups") + RCLONE_CONFIG_BASE64 (contenu de
#    rclone.conf encodé en base64, incluant les identifiants du stockage distant) :
#    chaque dump est copié hors site juste après sa création.
#  - BACKUP_PING_URL (ex. https://hc-ping.com/<uuid> sur healthchecks.io) : ping après
#    chaque sauvegarde réussie, et suffixé "/fail" en cas d'échec — alerte si le job
#    s'arrête de tourner silencieusement.

DB_NAME=${DB_NAME:-fleethub}
BACKUP_DIR=${BACKUP_DIR:-/backups}
KEEP_DAYS=${BACKUP_KEEP_DAYS:-7}
INTERVAL_HOURS=${BACKUP_INTERVAL_HOURS:-24}
RCLONE_REMOTE=${RCLONE_REMOTE:-}
BACKUP_PING_URL=${BACKUP_PING_URL:-}

if [ -n "$RCLONE_REMOTE" ] && ! command -v rclone >/dev/null 2>&1; then
    echo "[backup] installation de rclone (sauvegarde hors site demandée)..."
    apk add --no-cache rclone >/dev/null
fi
if [ -n "$RCLONE_REMOTE" ] && [ -n "${RCLONE_CONFIG_BASE64:-}" ]; then
    mkdir -p /root/.config/rclone
    echo "$RCLONE_CONFIG_BASE64" | base64 -d > /root/.config/rclone/rclone.conf
fi

ping() {
    [ -n "$BACKUP_PING_URL" ] && curl -fsS --retry 3 -m 10 "$BACKUP_PING_URL$1" >/dev/null 2>&1 || true
}

mkdir -p "$BACKUP_DIR"

backup() {
    ts=$(date +%Y%m%d-%H%M%S)
    file="$BACKUP_DIR/fleethub-$ts.sql.gz"
    tmp="$BACKUP_DIR/.fleethub-$ts.sql.tmp"

    # pg_dump | gzip masque l'échec de pg_dump : en sh/dash (pas de pipefail),
    # le code de sortie du pipe est celui de gzip, qui réussit même sur une
    # entrée vide. On dump donc dans un fichier temporaire d'abord, et on
    # vérifie le code de sortie de pg_dump directement.
    #
    # Quelques tentatives juste après un redémarrage de la stack (crash Docker,
    # rebuild) échouaient systématiquement car le réseau entre conteneurs n'est
    # pas encore stable au tout premier essai — d'où les 3 tentatives ci-dessous.
    attempt=1
    ok=0
    while [ "$attempt" -le 3 ]; do
        echo "[backup] $(date -Iseconds) démarrage de pg_dump (tentative $attempt/3)..."
        if PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" > "$tmp" 2>/tmp/pg_dump.err; then
            ok=1
            break
        fi
        echo "[backup] tentative $attempt échouée : $(cat /tmp/pg_dump.err)" >&2
        attempt=$((attempt + 1))
        [ "$attempt" -le 3 ] && sleep 10
    done

    if [ "$ok" -eq 1 ]; then
        gzip -c "$tmp" > "$file"
        rm -f "$tmp"
        size=$(wc -c < "$file")
        echo "[backup] $(date -Iseconds) terminé : $file ($size octets)"
    else
        rm -f "$tmp"
        echo "[backup] ÉCHEC du pg_dump après 3 tentatives" >&2
        ping "/fail"
        return
    fi

    if [ -n "$RCLONE_REMOTE" ]; then
        if rclone copy "$file" "$RCLONE_REMOTE" >/dev/null 2>&1; then
            echo "[backup] copié hors site vers $RCLONE_REMOTE"
        else
            echo "[backup] ÉCHEC de la copie hors site vers $RCLONE_REMOTE" >&2
            ping "/fail"
            return
        fi
    fi

    find "$BACKUP_DIR" -name 'fleethub-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
    ping ""
}

echo "[backup] conteneur de sauvegarde démarré (intervalle: ${INTERVAL_HOURS}h, rétention: ${KEEP_DAYS}j, hors site: ${RCLONE_REMOTE:-non configuré})"

while true; do
    backup
    echo "[backup] prochaine sauvegarde dans ${INTERVAL_HOURS}h"
    sleep $((INTERVAL_HOURS * 3600))
done
