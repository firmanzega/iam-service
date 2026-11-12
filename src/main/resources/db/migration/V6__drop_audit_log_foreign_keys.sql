-- Lepaskan kunci asing dari audit_logs.
--
-- Alasan:
--   - Jejak audit adalah catatan historis. Catatan itu harus tetap ada
--     walaupun aktor atau targetnya pengguna sudah dihapus. Dengan kunci
--     asing, penghapusan pengguna menyentuh baris audit dan memaksa setiap
--     insert audit memeriksa ulang kunci asing.
--   - Pemeriksaan kunci asing itu mengambil kunci KEY SHARE pada baris
--     pengguna. Ketika dua pembaruan pengguna berjalan bersamaan, satu
--     memegang kunci itu sementara yang lain butuh kunci UPDATE, sehingga
--     keduanya saling menunggu dan terjadi deadlock.
--   - Untuk audit, keutuhan rujukan adalah janji palsu. Akun bisa dihapus,
--     nama jalannya bisa berubah, dan catatan tetap harus menunjukkan siapa
--     yang melakukan apa pada waktu itu.
--
-- Angkanya tetap disimpan sebagai kolom biasa, dengan indeks untuk pencarian.

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_actor_user_id_fkey;
ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_target_user_id_fkey;

COMMENT ON COLUMN audit_logs.actor_user_id IS
    'Identitas pelaku, disimpan sebagai nilai biasa tanpa kunci asing agar catatan tetap ada setelah pengguna dihapus';
COMMENT ON COLUMN audit_logs.target_user_id IS
    'Objek yang diubah, disimpan sebagai nilai biasa tanpa kunci asing agar catatan tetap ada setelah pengguna dihapus';
