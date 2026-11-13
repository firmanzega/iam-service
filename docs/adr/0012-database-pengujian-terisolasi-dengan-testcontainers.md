# 0012 - Database Pengujian Terisolasi dengan Testcontainers

- Status: diterima
- Tanggal: 2026-11-13

## Konteks

Pengujian integrasi sebelumnya memakai database PostgreSQL yang sama dengan
pengembangan, yaitu container `iam-service-db` di port `5433` yang diisi lewat
berkas `.env`. Setiap kelas tes membersihkan datanya sendiri.

Ada tiga masalah yang muncul dari keadaan itu:

1. **Hasil tes bergantung pada keadaan database.** Data sisa dari percobaan
   sebelumnya, atau data contoh pengembangan, bisa membuat tes lulus atau
   gagal tanpa perubahan kode.
2. **Data pengembangan bisa hilang.** Pembersihan di akhir tes menghapus baris
   berdasarkan awalan nama pengujian. Kesalahan pada pola penghapusan bisa
   menghapus data yang bukan milik tes.
3. **Build di mesin bersih tidak mandiri.** CI harus membuat berkas `.env`
   dan menjalankan layanan PostgreSQL tambahan hanya agar tes bisa berjalan.

Skema sudah menjadi sumber kebenaran tunggal lewat Flyway. Yang belum
terjamin adalah database tempat migrasi itu dijalankan saat pengujian.

## Alternatif yang Dipertimbangkan

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Tetap memakai database pengembangan** | Tidak ada perubahan, paling cepat | Hasil tidak dapat diulang, data pengembangan berisiko terhapus, CI bergantung pada `.env` |
| **B. H2 dalam mode kompatibilitas PostgreSQL** | Cepat, tidak butuh Docker | Dialek, constraint, dan perilaku transaksi berbeda. Tes bisa lulus di H2 tetapi gagal di PostgreSQL |
| **C. Testcontainers baru untuk setiap kelas tes** | Isolasi paling ketat | Biaya start container berulang untuk setiap konteks Spring, seluruh rangkaian tes menjadi jauh lebih lambat |
| **D. Satu container Testcontainers per JVM** | Isolasi dari database pengembangan, dialek sama, biaya start hanya sekali | Tes dalam satu JVM berbagi database, sehingga tetap harus membersihkan datanya sendiri |
| **E. Layanan PostgreSQL hanya di CI** | Lokal tetap memakai Docker Compose | Perbedaan lingkungan lokal dan CI tetap ada, dan tes lokal masih menyentuh database pengembangan |

## Keputusan

**Memilih D: satu container PostgreSQL 16 dibuat sekali per JVM dan dipakai
seluruh pengujian integrasi.**

Bentuk keputusannya ada di
`src/test/java/id/osprey/iamservice/TestcontainersConfiguration.java`:

- Container dibuat statis dan distart di blok `static`, lalu didaftarkan
  sebagai bean `@ServiceConnection`.
- Setiap kelas tes integrasi mengimpor konfigurasi itu lewat
  `@Import(TestcontainersConfiguration.class)`.
- Kunci JWT pengujian dipindahkan ke `src/test/resources/application.properties`,
  sehingga berkas `.env` tidak diperlukan lagi untuk menjalankan tes.
- Workflow CI tidak lagi menyediakan layanan PostgreSQL atau membuat `.env`.

## Konsekuensi

**Positif**

- Database pengembangan tidak pernah tersentuh oleh pengujian.
- Skema dibangun ulang dari migrasi Flyway pada setiap proses tes, sehingga
  perbedaan antara migrasi dan pemetaan JPA langsung terlihat.
- Versi database yang diuji sama dengan versi produksi, termasuk perilaku
  constraint dan dialek SQL.
- Build di mesin bersih dan di CI memakai jalur yang sama.

**Negatif**

- Docker wajib berjalan untuk menjalankan pengujian integrasi.
- Start pertama lebih lambat karena harus mengunduh image PostgreSQL.
- Tes dalam satu JVM berbagi satu database. Pembersihan data per kelas tetap
  diperlukan; isolasi yang dijamin hanya terhadap database pengembangan.
- Container memakai memori tambahan selama tes berjalan.

## Alternatif yang Ditolak

- **A** ditolak karena masalah yang ingin diselesaikan justru berasal dari
  database bersama.
- **B** ditolak karena H2 tidak membuktikan perilaku PostgreSQL yang sebenarnya.
- **C** ditolak karena biaya start berulang tidak sebanding dengan manfaat
  isolasi tambahan. Konteks Spring sudah berbeda per kelas, dan container
  statis tetap memisahkan tes dari data pengembangan.
- **E** ditolak karena tidak menghilangkan ketergantungan tes lokal pada
  database pengembangan.

## Verifikasi

- `clean verify` menjalankan 130 tes tanpa kegagalan, semuanya memakai satu
  container PostgreSQL 16 pada port acak (contoh saat dijalankan: `53582`),
  bukan port `5433`.
- Log Flyway menunjukkan URL container, bukan URL database pengembangan.
- Konteks aplikasi tidak bergantung pada berkas `.env`: dengan `.env`
  dipindahkan sementara, `IamServiceApplicationTests` dan
  `TracingConfigurationTest` tetap lulus. Dua tes itulah yang memuat konteks
  Spring penuh, sehingga hasilnya cukup untuk membuktikan konfigurasi
  pengujian berdiri sendiri.
- `AuthSecurityIntegrationTest`, `AuditControllerIntegrationTest`,
  `UserConcurrencyTest`, `RefreshTokenCleanupIntegrationTest`, dan
  `RefreshTokenReuseIntegrationTest` memakai container yang sama.

## Catatan Tambahan

Keputusan ini melengkapi [ADR 0001](0001-flyway-sebagai-sumber-kebenaran-skema.md).
Flyway tetap menjadi sumber kebenaran skema; Testcontainers hanya menyediakan
database kosong tempat migrasi itu dijalankan.
