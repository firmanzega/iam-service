# ADR 0006 - Transaksi Terpisah untuk Jejak Audit

- **Status**: Dipakai
- **Tanggal**: 2026-01-11

## Konteks

Jejak audit harus mencatat perubahan penting: pengguna dibuat, role diganti, kata
sandi diubah, login berhasil, login gagal, akun terkunci.

Ada dua jenis kejadian, dan keduanya punya Requirements transaksional yang
**berlawanan**:

| Jenis | Nasib transaksi bisnis | Yang diharapkan dari audit |
|---|---|---|
| Aksi **berhasil** | Commit | Tercatat bersama perubahan bisnis |
| Aksi **gagal** | Rollback | Tetap tercatat |

Kebutuhan "gagal tetap tercatat" paling terasa pada login gagal. Kalau catatannya
ikut hilang bersama rollback, percobaan serangan brute force justru tidak
perekam sama sekali, dan fitur kunci akun jadi tidak berguna.

## Alternatif

| Alternatif | Kelebihan | Kekurangan |
|---|---|---|
| **A. Selalu satu transaksi** | Sederhana | Jejak audit untuk aksi gagal hilang |
| **B. Selalu transaksi terpisah** | Jejak audit selalu ada | Jejak audit untuk aksi berhasil bisa tersimpan padahal aksi bisnisnya gagal |
| **C. Pilih per jenis kejadian** | Dua kebutuhan terpenuhi | Ada dua cara pemanggilan, harus disiplin |
| **D. Tulis ke berkas atau antrean pesan** | Tidak bergantung pada database | Sulit dijamin tertulis,pengoperasiannya lebih berat |

## Keputusan

**Memisahkan dua cara pencatatan pada `AuditService`:**

| Method | Propagasi | Dipakai untuk |
|---|---|---|
| `record(...)` | Ikut transaksi yang sedang berjalan | Aksi yang **berhasil** |
| `recordIndependently(...)` | `REQUIRES_NEW`, transaksi terpisah | Aksi yang **gagal** |

Konsekuensi dari pilihan "ikut transaksi": perubahan bisnis dan catatan audit
committed bersamaan. Tidak mungkin ada perubahan tanpa catatan, dan tidak mungkin
ada catatan untuk perubahan yang tidak jadi.

Konsekuensi dari "transaksi terpisah": catatan untuk aksi gagal bertahan,
walaupun transaksi bisnisnya dibatalkan.

### Kendala yang ditemukan saat implementasi

Transaksi terpisah tidak bisa melihat data yang belum di-commit pada transaksi
lain. Ketika pengguna baru dibuat dan audit-nya langsung mencatat id pengguna itu,
maka catatan tersebut merujuk ke id yang belum ada di database, dan muncul
galat foreign key.

Akibatnya, pencatatan untuk aksi **berhasil** harus memakai transaksi yang sama
dengan aksi bisnisnya. Hanya pencatatan untuk aksi **gagal** yang memakai transaksi
terpisah, karena pada kasus itu datanya sudah ada dan ter-commit.

## Dampak

**Positif**

- Jejak audit untuk aksi gagal tidak pernah hilang.
- Jejak audit untuk aksi berhasil bersifat konsisten dengan perubahan bisnisnya.
- Percobaan login yang gagal tercatat, sehingga bisa ditelusuri sebagai pola serangan.

**Negatif**

- Ada dua metode dengan nama yang mirip. Bedanya halus tapi penting, dan
 keterangannya harus jelas di tiap pemanggil.
- Transaksi terpisah memakai koneksi database tambahan dari pool. Saat transaksi
  luar masih aktif, dibutuhkan satu koneksi ekstra.

**Alternatif yang ditolak**

- **A** ditolak karena menghilangkan bukti yang paling dibutuhkan.
- **B** ditolak karena bisa menghasilkan catatan untuk perubahan yang sebenarnya tidak jadi terjadi.
- **D** ditolak karena menambah komponen infrastruktur hanya untuk satu kebutuhan
  sederhana, dan menjamin pencatatan jadi lebih sulit, bukan lebih mudah.
