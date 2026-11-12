# ADR 0009 — Perbaikan Komentar Migrasi Terapan lewat Repair Checksum

- **Status**: Dipakai
- **Tanggal**: 2026-11-12

## Konteks

Migrasi `V5__add_search_and_relation_indexes.sql` sudah dijalankan di database
pengembangan. Isinya benar, tetapi satu komentar memuat kata yang rusak
(`kehatiakasiaan`). Kata itu tidak memengaruhi perintah SQL, namun membuat
dokumentasi skema sulit dibaca dan menutupi maksud aslinya.

Flyway menghitung checksum dari seluruh isi berkas, termasuk komentar. Menyunting
berkas yang sudah tercatat karena itu membuat validasi gagal saat aplikasi
start. ADR 0001 melarang menyunting migrasi terapan, dan larangan itu benar
untuk perubahan yang mengubah SQL. Pertanyaannya: apakah larangan yang sama
harus berlaku untuk komentar yang tidak mengubah satu perintah pun?

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Biarkan komentar rusak** | Tidak ada risiko checksum | Kesalahan menetap di sumber kebenaran skema |
| **B. Buat migrasi baru untuk komentar** | Mengikuti larangan secara harfiah | SQL hanya bisa menambah komentar baru, tidak menghapus komentar lama. Isi berkas lama tetap rusak, jadi masalahnya tidak selesai |
| **C. Sunting komentar, lalu sinkronkan checksum** | Berkas sumber menjadi benar | Setiap environment harus menjalankan repair. Butuh prosedur yang jelas |
| **D. Buat ulang seluruh migrasi V5 dengan nomor baru** | Tidak menyentuh berkas lama | Database yang sudah punya V5 tetap menyimpan komentar lama. Tidak menyelesaikan sumber kebenaran |

## Keputusan

**Menyunting komentar V5, lalu menyinkronkan checksum di setiap environment
yang sudah menjalankan migrasi itu.** Pengecualian ini sempit dan hanya berlaku
bila seluruh syarat berikut terpenuhi:

1. Perubahan hanya menyentuh komentar atau spasi. Tidak ada perintah SQL yang
   ditambah, dihapus, atau diubah.
2. Perbedaan dibuktikan lewat diff, bukan lewat pembacaan sekilas.
3. Checksum baru dihitung dan disinkronkan dengan `flyway repair`, atau dengan
   pembaruan `flyway_schema_history` bila repair resmi tidak tersedia.
4. Prosedurnya dicatat, dan setiap environment yang sudah menjalankan migrasi
   tersebut ikut diperbaiki.

Checksum Flyway adalah CRC32 dari setiap baris berkas tanpa pemisah baris,
dihitung atas byte UTF-8. Algoritme itu diverifikasi dengan membandingkan
checksum berkas V1 sampai V6 terhadap baris yang sudah tercatat di
`flyway_schema_history` sebelum perubahan dilakukan. Hasilnya sama persis untuk
ketujuh migrasi.

## Dampak

**Positif**

- Sumber kebenaran skema tidak lagi memuat kata rusak.
- Larangan menyunting migrasi tetap berlaku untuk perubahan SQL yang berbahaya.
- Prosedur repair tercatat, jadi tidak dikerjakan sebagai langkah rahasia.

**Negatif**

- Setiap environment yang sudah menjalankan V5 harus menjalankan repair.
  Environment yang tertinggal akan gagal start karena validasi checksum.
- Pengecualian ini bisa disalahgunakan untuk menyunting SQL sungguhan. Karena
  itu syarat "hanya komentar" harus diperiksa lewat diff, bukan dipercaya begitu
  saja.

**Alternatif yang ditolak**

- **A** ditolak karena membiarkan kesalahan di sumber kebenaran.
- **B** ditolak karena tidak bisa menghapus komentar yang sudah tertanam di
  berkas lama.
- **D** ditolak karena menambah riwayat migrasi tanpa memperbaiki berkas yang
  menjadi acuan.
