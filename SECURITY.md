# Keamanan

Layanan ini mengelola identitas dan akses pengguna. Melemahkan keamanan di
sini berarti orang lain bisa menyamar sebagai pengguna mana pun.

---

## Melaporkan Kerentanan

Kalau menemukan celah keamanan, **jangan** membuka issue publik.

Kirim laporan lewat salah satu cara berikut:

1. Buka issue privat di tab **Security** repositori ini
   (tab **Security** -> **Report a vulnerability**)
2. Kirim surel ke alamat yang tercantum di halaman profil maintainer repositori

Isi laporan yang membantu:

- Ringkasan masalahnya
- Langkah untuk memunculkannya
- Dampak dan alasannya
- Usulan solusi kalau ada

### Yang Perlu Dijaga

- Beri waktu yang cukup untuk rilis perbaikan, idealnya 90 hari
- Jangan ungkapkan data pengguna nyata milik orang lain
- Jangan bocorkan ke publik sebelum ada perbaikan yang keluar

### Yang akan Terjadi

Laporan akan dikonfirmasi diterima, diperiksa, dan dicatat di
`CHANGELOG.md`. Kalau kerentanannya sudah diperbaiki, nama pelapor akan
disebut di catatan perubahan kecuali diminta tidak.

---

## Versi yang Didukung

| Versi | Didukung |
|---|---|
| 0.1.x | Ya |

Belum ada rilis resmi. Selama masih tahap pengembangan, semua cabang
`main` dianggap didukung.

---

## Yang Sudah Dijaga

Ringkasan kontrol keamanan, alasan pilihannya, dan **batasannya** ada di
[`docs/security.md`](docs/security.md).

Bagian yang paling perlu diketahui:

- Kunci akun dihitung **per akun**, bukan per alamat IP
- Rotasi refresh token membuat token lama tidak bisa dipakai lagi
- Pesan galat login identik untuk username salah dan password salah, supaya
  username yang terdaftar tidak bisa ditebak
- Batas ukuran halaman ditegakkan di server, bukan hanya di klien
- Dokumentasi OpenAPI dimatikan di profil produksi

---

## Tanggung Jawab Pengguna

Gunakan layanan ini sesuai aturan. Yang berikut **tidak** diperbolehkan:

- Mencoba masuk dengan akun yang bukan milik sendiri
- Menggunakan kredensial yang bocor
- Menjalankan pengujian penetrasi terhadap sistem milik orang lain tanpa izin
- Menggunakan layanan ini untuk aktivitas yang melanggar hukum

---

## Menyusun Kredensial yang Aman

Kalau perlu membuat kredensial sendiri, jalankan:

```bash
openssl rand -base64 48
```

Jangan pernah memakai ulang kata sandi dari layanan lain, dan jangan pernah
menyimpan kredensial di berkas yang ikut ter-commit ke git.
