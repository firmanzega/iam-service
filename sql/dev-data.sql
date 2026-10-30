-- User dummy, data contoh (Data apa yang cuma enak dipakai saat ngoding dan ngetes?)

INSERT INTO users (username, email, password_hash)
    VALUES ('Dummy1', 'dummy1@mail.com', 'dummy1pass'),
           ('Dummy2', 'dummy2@mail.com', 'dummy2pass'),
           ('Dummy3', 'dummy3@mail.com', 'dummy3pass'),
           ('Dummy4', 'dummy4@mail.com', 'dummy4pass'),
           ('Dummy5', 'dummy5@mail.com', 'dummy5pass'),
           ('Dummy6', 'dummy6@mail.com', 'dummy6pass'),
           ('Dummy7', 'dummy7@mail.com', 'dummy7pass')
    ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id
        FROM (VALUES ('Dummy1','ADMIN'),
                     ('Dummy2','AUDITOR'),
                     ('Dummy3','USER'),
                     ('Dummy4','MANAGER'),
                     ('Dummy5','USER'),
                     ('Dummy5','MANAGER'),
                     ('Dummy6','USER'),
                     ('Dummy6','AUDITOR')) AS m (username, role_name)
        JOIN users u ON u.username = m.username
        JOIN roles r ON r.name = m.role_name
    ON CONFLICT (user_id, role_id) DO NOTHING;