## Ringkasan

Jelaskan apa yang berubah dan **kenapa**. Bagian "apa" sudah terbaca dari
diff-nya, jadi yang perlu ditulis di sini adalah alasannya.

Closes #

## Jenis Perubahan

Centang yang sesuai:

- [ ] Perbaikan bug
- [ ] Fitur baru
- [ ] Perubahan dokumentasi
- [ ] Refactor tanpa perubahan perilaku
- [ ] Pengujian
- [ ] Perawatan

## Kontrak API

Centang **tidak ada** kalau tidak menyentuh endpoint:

- [ ] Tidak ada, kontrak API tidak berubah
- [ ] Ada, dan anotasi OpenAPI sudah diperbarui di controller

Kalau ada, jelaskan:

| Endpoint | Perubahan | Alasan kompatibilitas |
|---|---|---|
| | | |

## Pengujian

- [ ] Ada pengujian baru untuk perubahan ini
- [ ] Pengujian yang ada diperbarui mengikuti perubahan perilaku

Jelaskan cakupan yang ditambahkan:

## Checklist Sebelum Review

Salinan dari [`CONTRIBUTING.md` bagian 8](../CONTRIBUTING.md#8-alur-review):

- [ ] `.\mvnw.cmd clean verify` hijau di Windows, atau `./mvnw clean verify` di Linux dan macOS
- [ ] Cakupan baris tetap di atas 80 persen
- [ ] `ArchitectureLayerTest` hijau
- [ ] Tidak ada kredensial yang masuk ke commit
- [ ] Pesan commit mengikuti pola conventional commit

## Dokumentasi

- [ ] README diperbarui kalau ada perubahan yang dilihat pemakai
- [ ] `docs/libraries.md` diperbarui kalau ada pustaka baru
- [ ] ADR baru dibuat di `docs/adr/` kalau ada keputusan arsitektur baru
- [ ] `docs/security.md` diperbarui kalau ada kontrol keamanan atau batas keamanan yang berubah
- [ ] Tidak perlu perubahan dokumentasi

## Catatan untuk Pemeriksa

Tulis di sini kalau ada hal yang perlu diperhatikan waktu review, misalnya
migrasi yang wajib dijalankan berurutan, konfigurasi yang perlu disiapkan,
atau bagian yang sengaja belum ditutup.
