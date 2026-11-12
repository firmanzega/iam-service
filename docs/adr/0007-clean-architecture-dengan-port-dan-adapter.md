# ADR 0007 - Clean Architecture dengan Port dan Adapter

- **Status**: Dipakai
- **Tanggal**: 2026-01-11

## Konteks

Pada tahap awal, proyek ini memakai struktur berbasis fitur: paket `user`, `role`,
`auth`, dan `audit` masing-masing berisi entitas JPA, repository, service, dan
controller sekaligus.

Pola itu bekerja untuk proyek kecil, tapi punya beberapa masalah yang nyata:

- Entitas domain terikat JPA. Setiap pengujian aturan bisnis ikut menarik
  dependensi framework, dan aturan bisnis tidak bisa diuji tanpa database.
- Use case (`UserService`) tahu detail persistence. Ia memanggil
  `UserRepository` Spring Data secara langsung, sehingga sulit diuji dengan
  tiruan.
- Controller mengembalikan DTO yang dibuat manual, tapi DTO itu berbagi
  bentuk dengan objek database.
- Galat bisnis ditulis sebagai `ResponseStatusException`, yang berarti use case
  sudah tahu soal HTTP.

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Tetap struktur berbasis fitur** | Navigasi mudah, tidak banyak berkas | Lapisan bercampur, domain terikat framework |
| **B. Pemisahan paket per lapisan** (domain / application / infrastructure / adapter) | Arah dependensi jelas, domain bebas framework | Lebih banyak berkas, perlu adapter pemetaan |
| **C. Domain model dan persistence model dipisah** | Domain benar-benar bebas, aturannya murni | Dua model untuk satu agregat, banyak kode pemetaan |
| **D. Domain model dan persistence model disatukan** | Tidak ada kode pemetaan | Anotasi JPA tetap menempel di domain |

## Keputusan

**Menggunakan pemisahan paket per lapisan, dengan model domain dan model
persistence yang dipisah.**

```
id.osprey.iamservice
├── domain/          entitas dan port, tanpa dependensi framework
├── application/     use case, orkestrasi aturan bisnis
├── infrastructure/  adapter JPA, JWT, BCrypt
└── adapter/rest/    controller, DTO, penangkap galat
```

Aturan yang dipegang:

1. **Domain tidak mengimpor framework apa pun.** `User`, `Role`, `RefreshToken`,
   dan `AuditLog` tidak punya anotasi JPA. Aturan bisnis seperti
   `deactivate()` dan `registerFailedLogin()` diuji tanpa database.
2. **Repository dideklarasikan sebagai port di domain** dan diimplementasikan
   sebagai adapter di `infrastructure`. Use case hanya mengenal port.
3. **Use case tidak melempar galat HTTP.** Galat bisnis dibungkus dalam
   `DomainException` beserta alasan galatnya. Pemetaan alasan ke status HTTP
   ada di `adapter/rest/GlobalExceptionHandler`.
4. **Controller hanya memvalidasi, meneruskan, dan memetakan hasil.**
   Validasi bentuk permintaan ada di DTO, di lapisan antarmuka.

### Batas yang disengaja

`Pageable` dan `Page` dari Spring Data masih dipakai di port repository dan
use case. Keduanya adalah objek nilai, bukan bagian dari database, dan
menggantinya dengan tipe milik sendiri akan menambah kode berulang tanpa
menambah manfaat nyata. Kalau someday domain harus benar-benar bebas dari
Spring Data, cukup ganti tipe di satu berkas port dan di adapter-nya.

## Dampak

**Positif**

- Aturan bisnis bisa diuji murni, tanpa Spring, tanpa database.
- Arah dependensi bisa diperiksa dengan alat analisis statis. Domain tidak
  pernah mengimpor `org.springframework` maupun `jakarta.persistence`.
- Kegagalan persistence bisa diganti tanpa menyentuh use case.
- Setiap file punya satu alasan untuk berubah.

**Negatif**

- Jumlah berkas bertambah. Satu agregat yang dulu satu kelas kini punya entitas
  domain, entitas JPA, dan adapter.
- Ada lapisan pemetaan yang harus dijaga sinkron.
- Perubahan nama kolom database tidak lagi langsung terasa di domain.

**Alternatif yang ditolak**

- **A** ditolak karena masalah yang disebut di konteks memang terjadi, bukan
  risiko teoretis.
- **D** ditolak karenaanotasi JPA di entitas domain tetap membuat
  aturan bisnis sulit diuji tanpa database, yang justru menjadi alasan utama
  refactor ini.

## Verifikasi

Aturan dependensi di atas diperiksa secara otomatis. Berkas di paket `domain`
tidak boleh mengimpor framework. Pemeriksaan ini ada di
`ArchitectureLayerTest` dan dijalankan sebagai bagian dari rangkaian pengujian.
