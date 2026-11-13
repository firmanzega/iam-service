#!/usr/bin/env bash
#
# Membuat cadangan logis database PostgreSQL dalam format custom.
#
# Kenapa format custom, bukan SQL biasa:
#      - pg_restore bisa memilih objek yang dipulihkan dan menjalankan
#        pemulihan dalam satu transaksi.
#      - Berkas SQL biasa harus dijalankan berurutan dan lebih sulit
#        diperiksa sebelum dipulihkan.
#
# Pemakaian:
#   POSTGRES_DB=iam_db POSTGRES_USER=iam_user POSTGRES_PASSWORD=rahasia \
#     scripts/backup-postgres.sh [berkas-keluaran]
#
# Untuk database yang berjalan di dalam container:
#   BACKUP_DOCKER_CONTAINER=iam-service-db \
#     POSTGRES_DB=iam_db POSTGRES_USER=iam_user POSTGRES_PASSWORD=rahasia \
#     scripts/backup-postgres.sh
#
# Kredensial tidak pernah ditulis ke berkas oleh skrip ini. Kata sandi
# diteruskan lewat environment PGPASSWORD, bukan argumen baris perintah, supaya
# tidak terlihat di daftar proses.

set -euo pipefail

DB_NAME="${POSTGRES_DB:-}"
DB_USER="${POSTGRES_USER:-}"
DB_PASSWORD="${POSTGRES_PASSWORD:-}"
DB_HOST="${POSTGRES_HOST:-localhost}"
DB_PORT="${POSTGRES_PORT:-5433}"
CONTAINER="${BACKUP_DOCKER_CONTAINER:-}"

if [ -z "$DB_NAME" ] || [ -z "$DB_USER" ]; then
    echo "POSTGRES_DB dan POSTGRES_USER wajib diisi." >&2
    exit 1
fi

OUTPUT="${1:-backups/$(date +%Y%m%d-%H%M%S)-${DB_NAME}.dump}"

if [ -e "$OUTPUT" ]; then
    echo "Berkas $OUTPUT sudah ada. Pilih nama lain agar cadangan lama tidak tertimpa." >&2
    exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"

if [ -n "$CONTAINER" ]; then
    if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
        echo "Container $CONTAINER tidak ditemukan." >&2
        exit 1
    fi

    docker exec -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER" \
        pg_dump --username "$DB_USER" --dbname "$DB_NAME" --format=custom > "$OUTPUT"
else
    if ! command -v pg_dump >/dev/null 2>&1; then
        echo "pg_dump tidak ditemukan. Pasang client PostgreSQL atau isi BACKUP_DOCKER_CONTAINER." >&2
        exit 1
    fi

    PGPASSWORD="$DB_PASSWORD" pg_dump \
        --host "$DB_HOST" --port "$DB_PORT" \
        --username "$DB_USER" --dbname "$DB_NAME" \
        --format=custom > "$OUTPUT"
fi

if [ ! -s "$OUTPUT" ]; then
    echo "Cadangan gagal: berkas $OUTPUT kosong." >&2
    exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
    CHECKSUM=$(sha256sum "$OUTPUT" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
    CHECKSUM=$(shasum -a 256 "$OUTPUT" | awk '{print $1}')
else
    CHECKSUM="tidak tersedia"
fi

echo "Cadangan dibuat: $OUTPUT"
echo "SHA-256: $CHECKSUM"
