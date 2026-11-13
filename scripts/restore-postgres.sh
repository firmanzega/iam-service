#!/usr/bin/env bash
#
# Memulihkan cadangan yang dibuat oleh backup-postgres.sh.
#
# Pemulihan menimpa objek yang namanya sama di database tujuan. Karena itu
# skrip ini menolak berjalan tanpa tanda --yes yang diketik sadar.
#
# Pemakaian:
#   POSTGRES_DB=iam_db POSTGRES_USER=iam_user POSTGRES_PASSWORD=rahasia \
#     scripts/restore-postgres.sh berkas.dump --yes
#
# Untuk database yang berjalan di dalam container:
#   BACKUP_DOCKER_CONTAINER=iam-service-db \
#     POSTGRES_DB=iam_db POSTGRES_USER=iam_user POSTGRES_PASSWORD=rahasia \
#     scripts/restore-postgres.sh berkas.dump --yes

set -euo pipefail

DB_NAME="${POSTGRES_DB:-}"
DB_USER="${POSTGRES_USER:-}"
DB_PASSWORD="${POSTGRES_PASSWORD:-}"
DB_HOST="${POSTGRES_HOST:-localhost}"
DB_PORT="${POSTGRES_PORT:-5433}"
CONTAINER="${BACKUP_DOCKER_CONTAINER:-}"

DUMP=""
CONFIRMED="no"

for argument in "$@"; do
    case "$argument" in
        --yes)
            CONFIRMED="yes"
            ;;
        -*)
            echo "Pilihan tidak dikenal: $argument" >&2
            exit 1
            ;;
        *)
            if [ -n "$DUMP" ]; then
                echo "Hanya satu berkas cadangan yang boleh diberikan." >&2
                exit 1
            fi
            DUMP="$argument"
            ;;
    esac
done

if [ -z "$DB_NAME" ] || [ -z "$DB_USER" ]; then
    echo "POSTGRES_DB dan POSTGRES_USER wajib diisi." >&2
    exit 1
fi

if [ -z "$DUMP" ] || [ ! -f "$DUMP" ]; then
    echo "Berkas cadangan tidak ditemukan. Berikan path berkas .dump sebagai argumen." >&2
    exit 1
fi

if [ "$CONFIRMED" != "yes" ]; then
    echo "Pemulihan akan menimpa objek di database $DB_NAME." >&2
    echo "Jalankan ulang dengan --yes kalau database tujuan sudah benar." >&2
    exit 1
fi

echo "Memulihkan $DUMP ke database $DB_NAME ..." >&2

if [ -n "$CONTAINER" ]; then
    if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
        echo "Container $CONTAINER tidak ditemukan." >&2
        exit 1
    fi

    docker exec -i -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER" \
        pg_restore --username "$DB_USER" --dbname "$DB_NAME" \
        --clean --if-exists --no-owner --no-privileges --single-transaction < "$DUMP"
else
    if ! command -v pg_restore >/dev/null 2>&1; then
        echo "pg_restore tidak ditemukan. Pasang client PostgreSQL atau isi BACKUP_DOCKER_CONTAINER." >&2
        exit 1
    fi

    PGPASSWORD="$DB_PASSWORD" pg_restore \
        --host "$DB_HOST" --port "$DB_PORT" \
        --username "$DB_USER" --dbname "$DB_NAME" \
        --clean --if-exists --no-owner --no-privileges --single-transaction < "$DUMP"
fi

echo "Pemulihan selesai." >&2
