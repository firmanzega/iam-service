# Library yang Digunakan

Dokumen ini menjelaskan setiap pustaka yang dipakai di layanan ini: apa
fungsinya, kenapa dipilih, dan di mana penggunaannya. Tujuannya supaya
pembaca proyek ini tidak perlu menebak-nebak kenapa sebuah library ada.

Semua versi library dikelola oleh `spring-boot-starter-parent`, kecuali
`springdoc` yang dikunci manual karena tidak ikut dikelola Spring Boot.

---

## 1. Ringkasan

| Kategori | Library | Versi |
|---|---|---|
| Web | `spring-boot-starter-webmvc` | 4.1.2 |
| Data | `spring-boot-starter-data-jpa` | 4.1.2 |
| Database | `postgresql` | 42.7.14 |
| Migrasi | `spring-boot-starter-flyway` + `flyway-database-postgresql` | 12.4.0 |
| Keamanan | `spring-boot-starter-security` | 4.1.2 (Spring Security 7.1.2) |
| Token | `spring-boot-starter-oauth2-resource-server` + Nimbus | 4.1.2 (Nimbus 10.9.1) |
| Hash kata sandi | `spring-security-crypto` | 7.1.2 |
| Validasi | `spring-boot-starter-validation` | 4.1.2 |
| Dokumentasi | `springdoc-openapi-starter-webmvc-ui` | 3.2.0 |
| Operasional | `spring-boot-starter-actuator` | 4.1.2 |
| Pengujian | `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test` | 4.1.2 |

---

## 2. Dependency Langsung

### 2.1 `spring-boot-starter-webmvc`

**Apa itu:** Starter untuk membangun REST API memakai Spring MVC. Di Spring
Boot 4, starter ini menggantikan `spring-boot-starter-web` yang lama.

**Kenapa dipakai:** Seluruh endpoint layanan ini adalah REST API berbasis
JSON. Spring MVC menangani routing, pemetaan parameter, validasi, dan
serialisasi JSON.

**Dipakai di:** `adapter/rest/*Controller.java`

**Yang perlu diperhatikan:**

- Spring Boot 4 memecah menjadi beberapa starter kecil. Yang dipakai di sini
  hanya yang benar-benar dibutuhkan, supaya permintaan tidak menarik pustaka
  yang tidak terpakai.
- `@RestController` sudah termasuk serialisasi JSON, jadi tidak perlu
  `@ResponseBody` terpisah.

### 2.2 `spring-boot-starter-data-jpa`

**Apa itu:** Starter untuk lapisan persistensi berbasis JPA. Menggabungkan
Spring Data JPA, Hibernate ORM, dan Spring JDBC.

**Kenapa dipakai:** Memetakan entitas ke basis data tanpa menulis SQL manual,
serta menyediakan repository generik dan penguncian optimistis lewat
anotasi `@Version`.

**Dipakai di:** `infrastructure/persistence/*`

**Yang perlu diperhatikan:**

- Skema divalidasi lewat `spring.jpa.hibernate.ddl-auto: validate` di profil
  dev dan produksi. Skema database harus cocok dengan hasil migrasi Flyway.
- Penguncian optimistis bekerja lewat kolom `version`. Kalau kolom itu
  dihapus dari entity, mekanismenya ikut hilang.
- HQL diurai dengan **ANTLR**, karena itu `antlr4-runtime` ikut terbawa.

### 2.3 `postgresql` (JDBC Driver)

**Apa itu:** Driver JDBC untuk PostgreSQL.

**Kenapa dipakai:** Database yang dipakai adalah PostgreSQL 16.

**Dipakai di:** Dikonfigurasi lewat `spring.datasource.url` di berkas
`application-*.yaml`.

**Yang perlu diperhatikan:**

- Driver ini memetakan tipe PostgreSQL seperti `TIMESTAMP WITH TIME ZONE` dan
  `JSONB` ke tipe Java yang sesuai, sehingga tidak perlu pustaka tambahan.
  Ekstensi `pg_trgm` sendiri dipasang lewat migrasi Flyway, bukan lewat driver.
- Pool koneksinya adalah **HikariCP**, bukan PgBouncer. Untuk beban tinggi,
  connection pool eksternal lebih baik.

### 2.4 `spring-boot-starter-flyway` + `flyway-database-postgresql`

**Apa itu:** Flyway adalah alat migrasi database. Ia menyimpan riwayat
perubahan skema di tabel `flyway_schema_history` dan menerapkannya berurutan.

**Kenapa dipakai:** Skema harus punya **satu sumber kebenaran** yang bisa
diaudit. Lihat [ADR 0001](adr/0001-flyway-sebagai-sumber-kebenaran-skema.md).

**Dipakai di:** `src/main/resources/db/migration/`

**Yang perlu diperhatikan:**

- Migrasi yang sudah pernah dijalankan **tidak boleh diedit**. Flyway
  memeriksa checksum dan build akan gagal kalau isinya berubah.
- `flyway-database-postgresql` harus dipasang terpisah sejak Flyway 10.
  Tanpa itu, Flyway tidak mengenali tipe data PostgreSQL.
- Ada lokasi migrasi terpisah khusus profil dev, yaitu `db/migration-dev/`.
  Lokasi itu tidak pernah dipindai di profil lain.

### 2.5 `spring-boot-starter-security`

**Apa itu:** Framework keamanan Spring. Menyediakan filter autentikasi,
otorisasi per URL dan per method, serta pengaman kata sandi.

**Kenapa dipakai:** Layanan ini mengatur identitas dan akses. Tanpa Security,
setiap endpoint bisa dipanggil siapa saja.

**Dipakai di:** `config/SecurityConfig.java`, `config/RestAuthenticationEntryPoint.java`,
`config/RestAccessDeniedHandler.java`

**Yang perlu diperhatikan:**

- **Spring Security 7** mengubah tanda tangan `AuthenticationEntryPoint`.
  Sekarang method-nya menerima tiga argumen: request, response, dan
  exception. Dua argumen tidak lagi dikompilasi.
- `@PreAuthorize` memerlukan `@EnableMethodSecurity` di kelas konfigurasi.
- Filter Security berjalan **sebelum** lapisan MVC. Exception dari
  `@PreAuthorize` dilempar di dalam DispatcherServlet, jadi kalau tidak
  ditangani khusus, `GlobalExceptionHandler` yang umum akan menelan dan
  mengubahnya jadi galat 500. Handle `AccessDeniedException` secara
  tersendiri.

### 2.6 `spring-boot-starter-oauth2-resource-server`

**Apa itu:** Starter untuk memvalidasi token JWT pada permintaan HTTP.

**Kenapa dipakai:** Token akses yang diterbitkan sendiri harus diverifikasi
dan ditandatangani dengan kunci yang sama. Resource server memakai
pustaka Nimbus untuk itu.

**Dipakai di:** `config/SecurityConfig.java` (bean `JwtEncoder` dan
`JwtDecoder`), `infrastructure/security/JwtTokenService.java`

**Yang perlu diperhatikan:**

- Pustaka yang benar-benar bekerja adalah **Nimbus JOSE JWT**
  (`com.nimbusds.jose` dan `com.nimbusds.jwt`). Spring Security hanya
  mengonfigurasi decoder dan encoder di atasnya.
- Layanan ini belum memasang header `kid` pada token. Header itu baru
  dibutuhkan kalau nanti ada rotasi kunci tanpa memutus sesi lama, karena ia
  menandai kunci mana yang dipakai.
- **Validasi issuer** (`iss`) harus diaktifkan. Tanpa itu, token yang
  ditandatangani dengan kunci yang sama tapi penerbit berbeda akan diterima.

### 2.7 `spring-security-crypto`

**Apa itu:** Pustaka kriptografi Spring Security. Mengandung `BCryptPasswordEncoder`.

**Kenapa dipakai:** Dipakai terpisah dari starter keamanan, karena satu
pustaka ini hanya perlu algoritma hash kata sandi, bukan seluruh rangkaian
filter keamanan.

**Dipakai di:** `infrastructure/security/BCryptPasswordEncoderAdapter.java`

**Yang perlu diperhatikan:**

- **BCrypt hanya memakai 72 byte pertama.** Byte setelah itu diabaikan.
  Karena itu aturan kekuatan kata sandi membatasi panjang masukan, walau
  pemeriksaannya memakai jumlah karakter sehingga kata sandi non-ASCII tetap
  bisa melewati 72 byte.
- BCrypt dengan **salt bawaan** menghasilkan hash berbeda setiap kali untuk
  kata sandi yang sama. Itu perilaku yang benar, bukan bug.

### 2.8 `spring-boot-starter-validation`

**Apa itu:** Implementasi Jakarta Bean Validation. Menyediakan anotasi seperti
`@NotBlank`, `@Size`, `@Email`, dan `@Pattern`.

**Kenapa dipakai:** Validasi bentuk permintaan harus terjadi di satu tempat,
yaitu lapisan antarmuka, bukan di dalam logika bisnis.

**Dipakai di:** `adapter/rest/dto/*Request.java`

**Yang perlu diperhatikan:**

- Anotasi validasi **tidak jalan** kalau kelas controller tidak diberi
  `@Validated` atau parameter tidak diberi `@Valid`. Di sini setiap
  parameter body diberi `@Valid`.
- Aturan kekuatan kata sandi **tidak** pakai anotasi, karena aturan itu
  milik *domain* dan dipakai juga oleh reset kata sandi oleh admin.

### 2.9 `springdoc-openapi-starter-webmvc-ui`

**Apa itu:** Pembuat dokumentasi OpenAPI dari kode, sekaligus halaman
Swagger UI.

**Kenapa dipakai:** Dokumentasi yang ditulis manual akan cepat basi.
springdoc membaca anotasi pada controller dan menghasilkan deskripsi API
yang selalu sesuai dengan kode.

**Dipakai di:** `config/OpenApiConfig.java`, seluruh `adapter/rest/*Controller.java`

**Yang perlu diperhatikan:**

- **Versi 3.x untuk Spring Boot 4.** Versi 2.x hanya untuk Boot 3.
- Deskripsi API dibuat dari anotasi `@Tag`, `@Operation`, dan `@ApiResponse`.
- Aset Swagger UI dilayani sebagai webjar. Kalau path `/webjars/**` atau
  `/swagger-ui/**` diblokir aturan keamanan, halamannya akan **kosong
  putih**.
- **Content Security Policy** yang terlalu ketat juga membuat halamannya
  kosong putih. Directive `default-src 'none'` memblokir seluruh skrip dan
  gaya.
- Di profil produksi, springdoc dimatikan seluruhnya lewat
  `springdoc.api-docs.enabled: false` dan `springdoc.swagger-ui.enabled: false`.

### 2.10 `spring-boot-starter-actuator`

**Apa itu:** Endpoint operasional Spring Boot untuk kesehatan, metrik, dan
informasi aplikasi.

**Kenapa dipakai:** Orkestrasi butuh tahu apakah layanan sehat. Metriknya
juga berguna untuk melihat perilaku nyata di produksi.

**Dipakai di:** `application.yaml` pada blok `management`

**Yang perlu diperhatikan:**

- Hanya `health` dan `metrics` yang dibuka. Endpoint lain seperti
  `env` dan `heapdump` membocorkan detail internal.
- `show-details: never` supaya detail komponen tidak tampil ke pemanggil.
- Health check dibiarkan terbuka karena dipakai probe orkestrasi. Endpoint
  metrics tetap butuh peran ADMIN.

### 2.11 Pustaka Pengujian

| Library | Isi |
|---|---|
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, Hamcrest, JSONassert |
| `spring-boot-starter-webmvc-test` | MockMvc, `@WebMvcTest`, JSON Path |
| `spring-boot-starter-data-jpa-test` | `@DataJpaTest` dan pengujian database |

**Kenapa dipisah:** Spring Boot 4 memecah paket pengujian menjadi beberapa
starter per modul. Yang dipakai hanya yang sesuai.

**Yang perlu diperhatikan:**

- **Mockito memakai Byte Buddy** untuk membuat tiruan. Peringatan
  "Mockito is currently self-attaching" muncul karena Java-agent dimuat
  secara dinamis. Peringatan ini belum menjadi masalah, tetapi akan menjadi
  masalah kalau suatu saat Java melarang pemuatan agent dinamis.
- **JSONassert** dipakai oleh pengujian integrasi untuk membandingkan JSON
  tanpa bergantung pada urutan field.

---

## 3. Dependency Transitif yang Perlu Dipahami

Kadang library yang paling sering membuat bingung justru yang tidak disebut
langsung di `pom.xml`.

| Library | Versi | Peran |
|---|---|---|
| `tools.jackson.core:jackson-databind` | 3.1.7 | **Jackson 3**. Serializer dan deserializer JSON |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.21 | Anotasi Jackson. Jackson 3 masih memakai paket anotasi yang lama |
| `org.hibernate.orm:hibernate-core` | 7.4.13.Final | Hibernate ORM, implementasi JPA |
| `org.hibernate.validator:hibernate-validator` | 9.1.5.Final | Implementasi Jakarta Bean Validation |
| `com.nimbusds:nimbus-jose-jwt` | 10.9.1 | Pembuat dan pembaca JWT |
| `com.zaxxer:HikariCP` | 7.0.2 | Connection pool untuk koneksi database |
| `io.swagger.core.v3:swagger-core-jakarta` | 2.2.55 | Model objek OpenAPI |
| `org.webjars:swagger-ui` | 5.34.0 | Aset web Swagger UI |
| `org.webjars:webjars-locator-lite` | 1.1.5 | Pencari aset webjar |
| `org.yaml:snakeyaml` | 2.6 | Pembaca berkas YAML, dipakai untuk berkas konfigurasi |
| `ch.qos.logback:logback-classic` | 1.5.38 | Implementasi logging |
| `org.slf4j:slf4j-api` | 2.0.20 | Facade logging yang dipakai kode aplikasi |
| `io.micrometer:micrometer-core` | 1.17.2 | Pengumpulan metrik |
| `net.bytebuddy:byte-buddy` | 1.18.15 | Pembuat kelas tiruan untuk Mockito |
| `org.jspecify:jspecify` | 1.0.1 | Anotasi nullability |
| `org.antlr:antlr4-runtime` | 4.13.2 | Parser HQL milik Hibernate |
| `jakarta.validation:jakarta.validation-api` | 3.1.1 | Antarmuka Bean Validation |
| `jakarta.persistence:jakarta.persistence-api` | 3.2.0 | Antarmuka JPA |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.27 | Server HTTP tempat aplikasi berjalan |

### Jebakan Jackson 3

Spring Boot 4 memakai **Jackson 3**, yang paketnya pindah ke `tools.jackson`:

```java
// Jackson 3, untuk serialisasi dan deserialisasi
import tools.jackson.databind.ObjectMapper;

// anotasi masih di paket lama
import com.fasterxml.jackson.annotation.JsonInclude;
```

Kode yang mengimpor `com.fasterxml.jackson.databind.ObjectMapper` **tidak akan
kompilasi** di proyek ini. Perhatikan juga bahwa `spring-boot-starter-test`
masih membawa Jackson 2 untuk keperluan pengujian.

---

## 4. Library yang Sengaja Tidak Dipakai

| Library | Kenapa tidak |
|---|---|
| **Lombok** | Membuat setter otomatis pada entitas domain. Entitas di sini sengaja **tidak punya setter** untuk bagian sensitif, dan aturan bisnisnya memakai method bermakna. Lombok juga menambah annotation processor yang memperlambat build |
| **MapStruct** | Pemetaan di proyek ini masih sedikit dan masih jelas ditulis manual. Menambah pustaka pemetaan untuk empat agregat tidak sebanding dengan konfigurasinya |
| **QueryDSL** | Query yang dipakai masih bisa ditulis jelas dengan Spring Data dan satu query JPQL. QueryDSL menambah generator kelas dan langkah build baru |
| **Bucket4j** | Rate limiter bawaan sudah cukup untuk satu instance, dan kunci akun per akun sudah menjadi lapisan kedua. Kalau nanti butuh rate limit global, tempatnya di gateway, bukan di aplikasi |
| **Redis** | Belum ada kebutuhan terdistribusi. Menambahkannya berarti menambah infrastruktur untuk masalah yang belum terjadi |
| **Testcontainers** | Database pengembangan lokal sudah ada lewat Docker Compose. Testcontainers lebih berguna kalau butuh pengujian dengan banyak versi database sekaligus |
| **Resilience4j** | Belum ada panggilan ke layanan luar yang perlu retry atau circuit breaker |

---

## 5. Aturan Menambah Dependency Baru

1. **Pakai starter Spring Boot kalau ada.** Itu cara paling aman karena
   versinya sudah dikelola `spring-boot-starter-parent`.
2. **Pastikan benar-benar dipakai.** Kalau hanya satu kelas yang memakainya,
   pustaka penuh mungkin berlebihan.
3. **Bungkus di balik port.** Kalau pustaka itu menyentuh lapisan domain,
   aturan di Bagian 3 pada [ADR 0007](adr/0007-clean-architecture-dengan-port-dan-adapter.md)
   berlaku.
4. **Perbarui dokumen ini** setiap ada dependency baru, supaya alasan memilih
   pustaka dan tempat penggunaannya tidak basi.
5. **Jalankan build bersih** untuk memastikan cakupan dan pengujian masih
   lolos:

   ```bash
   # Windows (PowerShell)
   .\mvnw.cmd clean verify

   # Linux dan macOS
   ./mvnw clean verify
   ```
