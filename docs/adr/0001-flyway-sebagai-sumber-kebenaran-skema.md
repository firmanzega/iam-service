# ADR 0001 — Flyway sebagai Sumber Kebenaran Skema

- **Status**: Dipakai
- **Tanggal**: 2026-01-11

## Konteks

Awalnya proyek ini punya dua sumber skema database:

1. `src/main/resources/db/migration/` — migrasi Flyway (V1 sampai V3).
2. `sql/schema.sql`, `sql/seed.sql`, `sql/dev-data.sql` — skrip SQL manual.

Keduanya menggambarkan tabel yang sama, tapi **tidak sama persis**. Saat AUDIT menemukan
perbedaan, `sql/schema.sql` belum punya kolom `version` yang sudah ada di migrasi V3.
Kolom itu justru dipakai untuk penguncian optimistis, jadi skrip manual tersebut
sudah tidak bisa dipakai untuk membuat database yang benar.

Dua sumber kebenaran untuk hal yang sama selalu berakhir dengan divergen. Pertanyaannya
bukan "mana yang benar sekarang", tapi "bagaimana cara supaya tidak bisa berbeda lagi".

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Dua sumber tetap dipertahankan** | Skrip SQL mudah dipakai manual | Selalu ada risiko berbeda. Sudah terbukti terjadi |
| **B. Hanya Flyway** | Satu sumber, dapat diverifikasi, ada riwayatnya | Setup awal butuh understanding soal migrasi |
| **C. Hanya skrip SQL manual** | Sederhana | Tidak ada riwayat perubahan, tidak ada validasi saat boot |
| **D. Flyway untuk skema, skrip manual khusus data contoh** | Skema jadi satu sumber, data contoh tetap praktis | Butuh dua jalur, harus jelas batasannya |

## Keputusan

**Menggunakan Flyway sebagai satu-satunya sumber kebenaran skema.** Folder `sql/`
dihapus. Data contoh untuk pengembangan dipindahkan ke lokasi migrasi terpisah yang
hanya dipindai pada profil `dev`.

Konsekuensi teknis:

- `spring.jpa.hibernate.ddl-auto` bernilai `validate`, bukan `update` atau `create`.
  Boot gagal cepat kalau skema di database tidak cocok dengan hasil migrasi.
- `spring.flyway.validate-on-migrate` bernilai `true` pada profil produksi, supaya
  migrasi yang sudah pernah dijalankan tapi isinya berubah terdeteksi sebelum dipakai.
- Migrasi bersifat permanen. Tidak pernah mengedit migrasi yang sudah pernah
  dijalankan; perubahan baru harus berupa migrasi baru. Satu pengecualian
  sempit untuk komentar yang tidak mengubah SQL diatur di
  [ADR 0009](0009-perbaikan-komentar-migrasi-terapan.md).

## Dampak

**Positif**

- Skema di database selalu bisa ditelusuri lewat `flyway_schema_history`.
- Perbedaan skema tidak mungkin terjadi karena ada satu sumber saja.
- Build gagal lebih awal kalau skema tidak sesuai, bukan saat ada permintaan.

**Negatif**

- Database yang sudah ada perlu migration baseline, bukan sekadar menjalankan skrip.
- Menambah kolom berarti menambah berkas migrasi, bukan mengubah skrip lama.
- Data contoh sekarang perlu jalur terpisah agar tidak ikut ke produksi.

**Alternatif yang ditolak**

- **A** ditolak karena kegagalan yang sudah nyata terjadi, bukan risiko teoretis.
- **C** ditolak karena kehilangan riwayat perubahan dan validasi.
- **D** diterima, tapi dengan batasan tegas: hanya data contoh, hanya profil `dev`,
  dan hanya di lokasi migrasi terpisah yang tidak pernah dipindai di produksi.
