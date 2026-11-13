# 0013 - Observability Penolakan Akses dan Tracing

- Status: diterima
- Tanggal: 2026-11-13

## Konteks

Sebelum perubahan ini, layanan hanya punya dua sumber informasi operasional:

- log aplikasi, dengan penolakan 401 dan 403 ditulis pada level DEBUG;
- endpoint `/actuator/metrics`, yang belum memuat metrik khusus keamanan.

Profil produksi memakai level INFO, sehingga penolakan akses pada praktiknya
tidak terlihat. Tidak ada cara menghitung lonjakan penolakan, tidak ada jejak
pelaku untuk 403, dan tidak ada penghubung antara satu permintaan dan
rentangannya saat masalah melibatkan lebih dari satu komponen.

## Alternatif yang Dipertimbangkan

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Biarkan seperti sekarang** | Tidak ada perubahan perilaku | Penolakan akses tidak terlihat di produksi, tidak ada dasar untuk investigasi |
| **B. Simpan 401 dan 403 ke jejak audit** | Semua penolakan punya jejak permanen | Permintaan tanpa kredensial bisa datang dari siapa saja. Tabel audit dapat dibanjiri trafik luar dan catatan penting tenggelam |
| **C. Simpan 403 saja, 401 cukup log dan metrik** | Pelaku eskalasi peran tercatat, volume audit tetap terkendali | Percobaan tanpa kredensial hanya bisa ditelusuri lewat log dan metrik |
| **D. Tracing selalu menyala** | Semua permintaan langsung punya rentang | Tanpa collector, ekspor gagal berulang dan memenuhi log. Biaya penyimpanan naik tanpa ada yang membaca |
| **E. Tracing menyala lewat environment, sampling bawaan nol** | Tidak ada percobaan ekspor saat collector belum ada, bisa dinyalakan saat dibutuhkan | Perlu langkah konfigurasi dan restart untuk mulai mengumpulkan rentang |

## Keputusan

**Memilih C untuk penolakan akses dan E untuk tracing.**

Aturan penolakan akses:

- **403** dicatat ke jejak audit sebagai `ACCESS_DENIED`, dengan
  `actor_user_id` dari subjek token dan detail berisi method serta URI.
  Penyimpanan memakai `AuditService.recordIndependently` agar tidak ikut
  transaksi yang dibatalkan.
- **401** tidak disimpan ke database. Kejadiannya tetap dinaikkan ke log WARN
  dan dihitung pada metrik.
- Keduanya menambah metrik `iam.security.rejected` dengan tag `reason`
  (`unauthorized` atau `access_denied`) dan `method`. URI tidak dijadikan tag
  karena nilai yang mengandung id akan meledakkan jumlah deret waktu.
- Kegagalan menyimpan audit tidak menggagalkan respons. Pemanggil tetap
  menerima 403, bukan 500.

Aturan tracing:

- Dependensi yang dipakai hanya komponen tracing:
  `spring-boot-micrometer-tracing-opentelemetry`,
  `micrometer-tracing-bridge-otel`, dan `opentelemetry-exporter-otlp`.
  `spring-boot-starter-opentelemetry` tidak dipakai karena ikut membawa
  `micrometer-registry-otlp` dan akan mengirim metrik tanpa diminta.
- `OTEL_TRACES_SAMPLING_PROBABILITY` bernilai `0.0` secara bawaan.
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` menunjuk collector OTLP/HTTP.
- Pola log memuat `traceId` dan `spanId`; keduanya kosong saat sampling mati.

## Konsekuensi

**Positif**

- Lonjakan 401 dan 403 bisa dilihat dari satu metrik, tanpa membuka log satu
  per satu.
- Setiap 403 punya pelaku yang bisa ditelusuri sampai ke akun.
- Volume jejak audit tidak bisa dibanjiri oleh permintaan tanpa kredensial.
- Aplikasi tidak mencoba menghubungi collector yang belum ada.
- Saat tracing dinyalakan, satu `traceId` menghubungkan log dengan rentangnya.

**Negatif**

- Penolakan 401 hanya tersimpan di log dan metrik. Kalau butuh riwayat jangka
  panjang, log harus dikirim ke sistem terpusat.
- Metrik memakai tag `method`, sehingga jumlah deret waktu bertambah sesuai
  jumlah method HTTP yang dipakai. Tag URI sengaja tidak dipakai.
- Collector dan penyimpanan rentang menjadi tanggung jawab lingkungan
  deployment, bukan repositori ini.
- Menyalakan tracing memerlukan restart karena nilainya dibaca saat start.

## Alternatif yang Ditolak

- **A** ditolak karena penolakan akses pada level INFO praktis tidak terlihat.
- **B** ditolak karena membuka jalur pengisian tabel audit oleh pihak tanpa
  kredensial. Jejak yang penting justru bisa tenggelam.
- **D** ditolak karena percobaan ekspor yang gagal berulang hanya memindahkan
  masalah dari "tidak ada tracing" menjadi "log penuh galat ekspor".

## Verifikasi

- `AuthSecurityIntegrationTest` memeriksa bahwa 403 menulis
  `ACCESS_DENIED` dengan aktor yang benar dan menambah metrik, sedangkan 401
  menambah metrik tanpa menulis baris audit.
- `SecurityEventRecorderTest` memeriksa aturan yang sama pada tingkat unit,
  termasuk kegagalan audit yang tidak boleh menggagalkan respons.
- `TracingConfigurationTest` menyalakan sampling `1.0` dan memastikan `Tracer`
  menghasilkan rentang dengan `traceId`.
- Seluruh `clean verify` lulus dengan 130 pengujian, Checkstyle tanpa temuan,
  dan SpotBugs tanpa temuan High.

## Catatan Tambahan

Keputusan ini memakai pola transaksi terpisah dari
[ADR 0006](0006-transaksi-terpisah-untuk-jejak-audit.md), karena penolakan
akses terjadi di luar transaksi bisnis yang sedang berjalan.
