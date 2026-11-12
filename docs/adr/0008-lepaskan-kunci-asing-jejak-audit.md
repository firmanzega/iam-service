# ADR 0008 - Lepaskan Kunci Asing pada Tabel Jejak Audit

- **Status**: Dipakai
- **Tanggal**: 2026-01-12

## Konteks

Tabel `audit_logs` awalnya memakai kunci asing ke `users` pada kolom
`actor_user_id` dan `target_user_id`, dengan aturan `ON DELETE SET NULL`.

Pengujian concurrent_update kemudian menunjukkan deadlock yang berulang:

```
Process 941 waits for ShareLock on transaction 3143; blocked by process 942.
Process 942 waits for ShareLock on transaction 3142; blocked by process 941.
Where: while updating tuple (0,68) in relation "users"
```

Penyebabnya adalah urutan kunci:

1. Setiap insert ke `audit_logs` memeriksa kunci asing, dan pemeriksaan itu
   mengambil kunci KEY SHARE pada baris pengguna yang dirujuk.
2. Pembaruan pengguna butuh kunci UPDATE pada baris yang sama.
3. Pada pembaruan bersamaan, kedua thread bisa memegang KEY SHARE lebih dulu
   lalu saling menunggu kunci UPDATE. Keduanya saling menahan, hasilnya
   deadlock.

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Pertahankan kunci asing, pakai `saveAndFlush`** | Skema tetap punya integritas rujukan | Kunci masih diambil pada setiap insert, dan risiko deadlock tetap ada untuk jalur tulis lain |
| **B. Lepaskan kunci asing** | Insert audit tidak lagi mengambil kunci pada baris pengguna, deadlock hilang di akar | Keutuhan rujukan tidak dijamin database |
| **C. Pindahkan jejak audit ke skema atau layanan terpisah** | Isolasi penuh | Terlalu besar untuk kebutuhan saat ini, menambah infrastruktur |

## Keputusan

**Melepaskan kunci asing dari `audit_logs`.** Angka `actor_user_id` dan
`target_user_id` tetap disimpan sebagai kolom biasa, lengkap dengan indeks
untuk pencarian.

Alasannya:

1. **Jejak audit adalah catatan historis.** Catatan itu harus tetap ada
   walaupun aktor atau targetnya pengguna sudah dihapus. Dengan kunci asing
   dan aturan `ON DELETE SET NULL`, penghapusan pengguna tetap menyentuh baris
   audit dan memicu penulisan ulang catatan.
2. **Keutuhan rujukan untuk jejak audit adalah janji palsu.** Nama akun bisa
   diubah, akun bisa dihapus, dan catatan tetap harus menunjukkan siapa yang
   melakukan apa pada waktu itu. Yang penting adalah nilai yang tercatat pada
   saat kejadian, bukan apakah baris induknya masih ada.
3. **Biaya pemeriksaan kunci asing pada setiap insert.** Tabel audit adalah
   tabel dengan volume tulis tinggi, dan setiap pemeriksaan kunci asing berarti
   satu pembacaan indeks tambahan pada tabel pengguna.

Pencatatan `saveAndFlush` pada adapter pengguna tetap dipertahankan. Itu
memastikan deteksi optimistic locking terjadi pada saat yang dapat diprediksi,
bukan saat transaksi committed.

## Dampak

**Positif**

- Deadlock pada pembaruan bersamaan hilang di akar masalahnya.
- Insert jejak audit tidak lagi mengambil kunci pada baris pengguna.
- Jejak audit bertahan apa pun yang terjadi pada data penggunanya.

**Negatif**

- Database tidak lagi menolak entri audit yang menunjuk pengguna yang tidak
  ada. Validasi itu jadi tanggung jawab aplikasi.
- Melihat jejak audit tidak otomatis tersaring saat pengguna dihapus.

**Alternatif yang ditolak**

- **A** ditolak karena hanya memindahkan gejala, bukan menghilangkan
  ketergantungan kunci. Jalur tulis lain ke `audit_logs` tetap bisa
  mengambil kunci yang sama.
- **C** ditolak karena alasan dan infrastrukturnya terlalu besar untuk kebutuhan saat ini.

## Verifikasi

Deadlock yang sebelumnya muncul sepuluh kali berturut-turut pada
`UserConcurrencyTest` tidak muncul lagi setelah migrasi V6 diterapkan, dan
konflik optimistic locking tetap terdeteksi dengan benar.
