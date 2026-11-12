# 0010 - Pemeliharaan Berkala di Dalam Proses Aplikasi

- Status: diterima
- Tanggal: 2026-11-12

## Konteks

Dua struktur data tumbuh tanpa batas selama layanan hidup:

1. **Tabel `refresh_tokens`.** Setiap login dan penukaran token menambah satu
   baris. Token yang dicabut tetap disimpan, dan token yang kedaluwarsa tidak
   pernah dipakai lagi. Tanpa pembersihan, tabel dan indeksnya membesar terus.
2. **Peta penghitung rate limiter.** `RateLimitFilter` menyimpan satu antrean
   waktu per alamat IP. Antrean lama dipangkas saat klien kembali memanggil,
   tetapi klien yang berhenti memanggil meninggalkan entri di memori selamanya.

Keduanya bukan masalah pada satu instance dengan trafik kecil, tetapi menjadi
kebocoran sumber daya yang lambat pada layanan yang berjalan lama.

## Alternatif yang Dipertimbangkan

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Penjadwal di dalam proses (`@Scheduled`)** | Tidak menambah infrastruktur, ikut ter-deploy bersama kode, mudah diuji | Setiap instance menjalankan tugas yang sama |
| **B. `pg_cron` di PostgreSQL** | Satu eksekusi untuk semua instance | Menambah ekstensi dan hak istimewa database, logika terpisah dari kode |
| **C. CronJob Kubernetes atau cron sistem** | Eksekusi tunggal, terpisah dari aplikasi | Menambah artefak operasional dan konfigurasi yang harus dijaga |
| **D. TTL di penyimpanan bersama seperti Redis** | Pembersihan otomatis | Menambah dependensi dan infrastruktur baru |

## Keputusan

Memakai **alternatif A** untuk saat ini:

- `RefreshTokenCleanupJob` berjalan setiap jam dan menghapus baris dengan
  `expires_at` sebelum waktu sekarang.
- `RateLimitFilter` memindai dan membuang entri klien yang tidak lagi punya
  permintaan di dalam jendela waktu, setiap sepuluh menit.
- Kedua selang dapat diubah lewat `application.yaml`, dan pembersihan token
  dapat dimatikan lewat `iam.refresh-token-cleanup.enabled`.

Penghapusan token sengaja hanya menyentuh baris yang **sudah kedaluwarsa**.
Token yang dicabut tetap disimpan sampai masa berlakunya lewat, karena baris
itu masih berguna bila nanti ditambahkan deteksi pemakaian ulang.

## Dampak

**Positif**

- Tidak ada infrastruktur baru, dan perilakunya ikut diuji bersama kode.
- Penghapusan bersifat idempoten. Kalau dua instance menjalankannya bersamaan,
  hasil akhirnya tetap sama; paling banyak satu pihak menghapus nol baris.
- Beban tiap eksekusi kecil karena berjalan berkala, bukan pada jalur panas.

**Negatif**

- Pada deployment banyak instance, pekerjaan pembersihan berjalan beberapa kali.
  Untuk operasi hapus yang idempoten, dampaknya dapat diabaikan.
- Jadwal bergantung pada jam masing-masing instance.
- Kalau kebutuhan pembersihan tumbuh menjadi berat, pola ini perlu dipindahkan
  ke penjadwal eksternal (alternatif B atau C).

## Tindak Lanjut

Kalau jumlah instance bertambah atau pembersihan mulai memakan waktu lama,
pindahkan tugas ke cron job terpisah. Logika penghapusan sudah berada di
`RefreshTokenCleanupService`, sehingga pemindahan itu tidak perlu mengubah
aturan domain.
