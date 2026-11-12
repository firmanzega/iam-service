-- Data contoh untuk pengembangan.
--
-- Berkas ini berada di lokasi migrasi terpisah (db/migration-dev) yang hanya
-- dipindai pada profil dev. Di profil lain, termasuk produksi, lokasi ini
-- tidak pernah dibaca sehingga data contoh ini tidak akan pernah muncul.
--
-- Kata sandi setiap pengguna contoh adalah "dummyNpass" dengan N sesuai
-- nomornya, misalnya "dummy1pass" untuk Dummy1.

INSERT INTO users (username, email, password_hash)
VALUES ('Dummy1', 'dummy1@mail.com', '$2a$10$68V6GlqZwOBdYnnKHmEiquWNUKasLVCOV86QnW1rBJUSmT2Wy/LI2'),
       ('Dummy2', 'dummy2@mail.com', '$2a$10$QsW8Gv6JvkC.dvI7SnYg7eLXaU9EOnUhb.uIYtr1SiLa/zRaDXvxe'),
       ('Dummy3', 'dummy3@mail.com', '$2a$10$8DAr0xuQaLi8Y4/.vnRIv.AabZvUPwqrkumBHo8l0iFSay7Rj6EQq'),
       ('Dummy4', 'dummy4@mail.com', '$2a$10$ue03sRn/9VOB115oQgZbweZ0hyOXNXAWiqbK9u3G/Lw1dzswU.Vdm'),
       ('Dummy5', 'dummy5@mail.com', '$2a$10$.JyGAlnlT7XXUOd1W7GMGO4BgPUeyC8Kdk50KrvTlXD406ZsoH4Sy'),
       ('Dummy6', 'dummy6@mail.com', '$2a$10$MCc3ELXMzVP.Cy6RL1z4L.F2lB4k.Mnoaji8uY93KE3BV9m.oKyq.'),
       ('Dummy7', 'dummy7@mail.com', '$2a$10$oj1WZm33TKTxRQc.DYRJo.5KaV/TTtGTfbGEpYRnYOWkGT/JLbGbq')
ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM (VALUES ('Dummy1', 'ADMIN'),
             ('Dummy2', 'AUDITOR'),
             ('Dummy3', 'USER'),
             ('Dummy4', 'MANAGER'),
             ('Dummy5', 'USER'),
             ('Dummy5', 'MANAGER'),
             ('Dummy6', 'USER'),
             ('Dummy6', 'AUDITOR')) AS m (username, role_name)
    JOIN users u ON u.username = m.username
    JOIN roles r ON r.name = m.role_name
ON CONFLICT (user_id, role_id) DO NOTHING;
