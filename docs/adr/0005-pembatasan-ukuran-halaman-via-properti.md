# ADR 0005 — Pembatasan Ukuran Halaman Lewat Properti Spring Boot

- **Status**: Dipakai
- **Tanggal**: 2026-01-11

## Konteks

Endpoint daftar pengguna menerima parameter `size` untuk menentukan berapa banyak
baris yang dikembalikan. Tanpa batas, klien bisa meminta `size=1000000`.

Dampaknya bukan sekadar "balasan besar". Setiap baris pengguna ikut menarik
relasi role-nya, jadi satu permintaan bisa menarik gugus baris dari database.
Ini penolakan layanan yang bisa dilakukan tanpa banyak usaha.

Yang membuat masalah ini tidak langsung terlihat: konfigurasi
`spring.data.web.pageable.max-page-size` **sudah ada** di berkas konfigurasi,
tetapi sama sekali tidak berlaku. Nilainya tetap 2000, nilai bawaan pustaka.

## Akar Masalah

Kelas `WebConfig` pada proyek ini memakai anotasi
`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`.

Anotasi itu mendaftarkan `PageableHandlerMethodArgumentResolver` sebagai bean
sendiri. Sementara itu, kelas konfigurasi otomatis Spring Boot
`DataWebAutoConfiguration` diberi syarat `@ConditionalOnMissingBean` untuk tipe
resolver yang sama. Karena bean sudah ada lebih dulu, **kelas konfigurasi
otomatis itu dilewati seluruhnya**.

Akibatnya, dua hal sekaligus hilang:

1. Batas ukuran halaman dari properti tidak pernah diterapkan.
2. Mode serialisasi `Page` pun ikut hilang, sehingga bentuk JSON berubah dari
   `{ "content": [...], "page": {...} }` menjadi bentuk mentah `PageImpl`.

Yang lebih repot: menghapus berkas `WebConfig.java` saja **tidak** cukup. Berkas
kelas lama (`WebConfig.class`) masih tertinggal di `target/classes`, sehingga
anotasi itu masih terbawa saat aplikasi dijalankan. Build bersih wajib dilakukan
setiap kali kelas konfigurasi dihapus.

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. `@EnableSpringDataWebSupport` tanpa parameter** | Bentuk JSON tetap | Batas ukuran halaman tetap diabaikan |
| **B. Hapus anotasi, andalkan auto-konfigurasi Spring Boot** | Batas dan serialisasi dikendalikan Spring Boot | Bentuk JSON harus dijaga lewat properti |
| **C. Validasi manual di controller** | Eksplisit | Terlalu banyak aturan yang tersebar di banyak tempat |
| **D. Batas lewat filter atau interceptor sendiri** | Fleksibel | Menambah lapisan yang tidak perlu |

## Keputusan

**Menghapus `@EnableSpringDataWebSupport` dan mempercayakan pengaturan
pagination sepenuhnya kepada auto-konfigurasi Spring Boot.**

Konfigurasi yang dipakai:

```yaml
spring:
  data:
    web:
      pageable:
        max-page-size: 50
        serialization-mode: via_dto
```

`serialization-mode: via_dto` diperlukan karena nilai bawaan auto-konfigurasi
adalah `direct`, yang menghasilkan bentuk JSON `PageImpl` mentah. Bentuk JSON
memang bagian dari kontrak API, jadi tidak boleh berubah diam-diam.

Ditambah pengujian regresi `PaginationConfigTest` yang mengunci nilai batas
ukuran halaman. Kalau suatu saat muncul konfigurasi yang menimpa resolver lagi,
pengujian itu langsung gagal.

## Dampak

**Positif**

- Batas ukuran halaman benar-benar berlaku. Terbukti lewat pengujian: permintaan
  dengan `size=99999` dijawab dengan `page.size = 50`.
- Bentuk JSON tidak lagi bergantung pada kode, melainkan pada konfigurasi yang
  bisa dibaca orang lain.
- Tidak ada lagi kelas konfigurasi yang mengunci auto-konfigurasi Spring Boot.

**Negatif**

- Bentuk JSON bergantung pada nilai bawaan pustaka yang bisa berubah pada versi
  berikutnya. Karena itu bentuknya dikunci lewat properti secara eksplisit.

**Alternatif yang ditolak**

- **A** ditolak karena masalahnya justru ada di situ.
- **C** ditolak karena aturan keamanan seperti ini tersebar di banyak tempat
  dan mudah terlewat saat ada endpoint baru.
- **D** ditolak karena menambah lapisan sendiri untuk sesuatu yang sudah
  disediakan pustaka.

## Aturan Tambahan

Properti pengurutan juga dibatasi lewat daftar yang diizinkan
(`UserSortableProperties`). Tanpa itu, klien bisa mengurutkan berdasarkan kolom
`password_hash`, sehingga hash kata sandi menjadi alat pengurutan bagi penyerang.
Properti yang tidak ada dalam daftar ditolak dengan kode 400.
