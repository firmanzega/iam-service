-- Rantai hash pada jejak audit
--
-- Kenapa perlu:
--   - Jejak audit yang bisa diubah tanpa jejak tidak berguna saat investigasi.
--     Dengan rantai hash, setiap baris mengikat dirinya ke baris sebelumnya.
--     Perubahan pada satu baris membuat hash baris itu tidak cocok lagi, dan
--     penghapusan baris membuat penerusnya kehilangan pendahulu.
--   - Hash dihitung di database, bukan di aplikasi, supaya jalur tulis apa pun
--     tetap menghasilkan rantai yang sama. Trigger juga memakai kunci
--     penasihat tingkat transaksi agar dua penulisan bersamaan tidak membaca
--     ujung rantai yang sama.
--
-- Batasan yang perlu diketahui:
--   - Ini bukan penyimpanan yang tidak bisa diubah. Pemilik database masih
--     bisa menulis ulang seluruh rantai dari awal. Rantai hanya membuat
--     perubahan itu harus disengaja dan sulit disembunyikan, bukan mustahil.
--   - Verifikasi membaca seluruh baris. Untuk volume sangat besar, pemeriksaan
--     perlu dipindahkan ke proses terjadwal, bukan endpoint permintaan biasa.

ALTER TABLE audit_logs ADD COLUMN prev_hash VARCHAR(64);
ALTER TABLE audit_logs ADD COLUMN entry_hash VARCHAR(64);

-- Bentuk data yang di-hash dipusatkan di satu fungsi. Backfill dan trigger
-- memakai fungsi yang sama, sehingga tidak mungkin ada dua rumus yang
-- menyimpang. Details diletakkan paling akhir karena isinya bebas dan bisa
-- memuat tanda pemisah; dengan begitu isi details tidak bisa menggeser field
-- lain saat hash dihitung ulang.
--
-- Waktu diubah menjadi mikrodetik epoch, bukan teks tanggal, supaya hasilnya
-- tidak bergantung pada zona waktu sesi database.
CREATE FUNCTION audit_log_hash(
    p_prev_hash TEXT,
    p_id BIGINT,
    p_actor_user_id BIGINT,
    p_action TEXT,
    p_target_user_id BIGINT,
    p_created_at TIMESTAMPTZ,
    p_details TEXT
) RETURNS TEXT
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT encode(sha256(convert_to(
        COALESCE(p_prev_hash, '') || '|' ||
        p_id::text || '|' ||
        COALESCE(p_actor_user_id::text, '') || '|' ||
        p_action || '|' ||
        COALESCE(p_target_user_id::text, '') || '|' ||
        ((EXTRACT(EPOCH FROM p_created_at) * 1000000)::bigint)::text || '|' ||
        COALESCE(p_details, '')
    , 'UTF8')), 'hex');
$$;

COMMENT ON FUNCTION audit_log_hash IS
    'Menghitung hash satu entri audit dari data kanonik. Dipakai backfill dan trigger insert.';

-- Baris lama dihitung ulang berurutan menurut id. Rantai dimulai dari baris
-- dengan id terkecil; prev_hash baris pertama bernilai NULL.
DO $$
DECLARE
    baris RECORD;
    hash_sebelumnya TEXT := NULL;
    hash_baris TEXT;
BEGIN
    FOR baris IN
        SELECT id, actor_user_id, action, target_user_id, details, created_at
        FROM audit_logs
        ORDER BY id
    LOOP
        hash_baris := audit_log_hash(
            hash_sebelumnya,
            baris.id,
            baris.actor_user_id,
            baris.action,
            baris.target_user_id,
            baris.created_at,
            baris.details);

        UPDATE audit_logs
        SET prev_hash = hash_sebelumnya,
            entry_hash = hash_baris
        WHERE id = baris.id;

        hash_sebelumnya := hash_baris;
    END LOOP;
END
$$;

ALTER TABLE audit_logs ALTER COLUMN entry_hash SET NOT NULL;

-- Hash yang sama tidak boleh muncul dua kali. Kalau muncul, berarti ada dua
-- entri dengan isi dan pendahulu yang sama, dan itu menandakan penulisan yang
-- tidak melalui rantai.
CREATE UNIQUE INDEX idx_audit_logs_entry_hash ON audit_logs(entry_hash);

COMMENT ON COLUMN audit_logs.prev_hash IS
    'Hash entri sebelumnya; NULL hanya untuk entri pertama dalam rantai';
COMMENT ON COLUMN audit_logs.entry_hash IS
    'Hash entri ini, dihitung dari prev_hash dan seluruh kolom audit';

-- Trigger insert mengisi kedua kolom hash. Kunci penasihat bertingkat transaksi
-- membuat penambahan berjalan satu per satu, sehingga dua transaksi tidak
-- membaca ujung rantai yang sama.
CREATE FUNCTION audit_logs_assign_hash() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    hash_sebelumnya VARCHAR(64);
BEGIN
    PERFORM pg_advisory_xact_lock(741852963);

    NEW.created_at := COALESCE(NEW.created_at, now());

    SELECT entry_hash
    INTO hash_sebelumnya
    FROM audit_logs
    ORDER BY id DESC
    LIMIT 1;

    NEW.prev_hash := hash_sebelumnya;
    NEW.entry_hash := audit_log_hash(
        hash_sebelumnya,
        NEW.id,
        NEW.actor_user_id,
        NEW.action,
        NEW.target_user_id,
        NEW.created_at,
        NEW.details);

    RETURN NEW;
END
$$;

CREATE TRIGGER trg_audit_logs_assign_hash
    BEFORE INSERT ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION audit_logs_assign_hash();
