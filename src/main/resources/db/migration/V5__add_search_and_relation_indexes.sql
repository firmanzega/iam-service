-- Indeks pendukung untuk pencarian dan pemuatan role.
--
-- Alasan:
--   - PostgreSQL tidak membuat indeks untuk foreign key secara otomatis.
--     Tabel user_roles selalu dipakai saat memuat role milik seorang pengguna,
--     sehingga indeks pada role_id sangat membantu.
--   - Pencarian memakai pola LOWER(kolom) LIKE '%q%'. Pola yang diawali
--     wildcard tidak bisa dilayani indeks btree biasa, karena indeks hanya
--     berguna untuk pencocokan awalan. Ekstensi pg_trgm menyediakan indeks
--     yang bisa melayani pola seperti itu.
--   - Penyaringan berdasarkan status aktif perlu indeks sendiri, karena
--     sebaran nilai kolom boolean yang timpang membuat PostgreSQL cenderung
--     mengabaikan indeks btree biasa pada kolom itu.

CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_users_username_trgm ON users USING GIN (lower(username) gin_trgm_ops);
CREATE INDEX idx_users_email_trgm ON users USING GIN (lower(email) gin_trgm_ops);

CREATE INDEX idx_users_active ON users(active);
