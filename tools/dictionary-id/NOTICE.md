Kamus Hunspell Bahasa Indonesia
================================

Berkas `id_ID.aff` dan `id_ID.dic` di folder ini berasal dari kamus
Hunspell Bahasa Indonesia.

  Sumber   : https://github.com/LibreOffice/dictionaries/tree/master/id
  Lisensi   : LGPL-3.0-only
  Penulis  : hunspell-id Authors

Kegunaan
---------

Kamus ini dipakai oleh `tools/check-bahasa.mjs` untuk memeriksa ejaan kata
dalam mode ketat, yaitu pada pilihan `--ketat`.

Mode bawaan tidak memakai kamus ini. Mode bawaan hanya memeriksa aksara
asing, kata yang sudah pernah salah ditulis, kata fungsi bahasa Inggris
yang tercampur di prosa Indonesia, dan kata yang tertulis dua kali
berturut-turut. Aturan itu tidak memerlukan kamus, sehingga pemeriksaan
bisa jalan tanpa memasang pustaka apa pun.

Penggunaan
----------

  npm install nspell
  node tools/check-bahasa.mjs --ketat docs/manual-testing.md

Lisensi LGPL-3.0 mengizinkan penggunaan dan redistribusi dengan syarat
file lisensi disertakan. Naskah lengkap lisensi ada di dalam berkas `id_ID.aff`
pada bagian awal.
