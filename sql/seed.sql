-- data referensi yang wajib ada (Data apa yang harus ada supaya aplikasi bisa jalan?)

INSERT INTO roles (name)
    VALUES ('ADMIN'),  ('USER'),  ('AUDITOR'), ('MANAGER')
    ON CONFLICT (name) DO NOTHING;