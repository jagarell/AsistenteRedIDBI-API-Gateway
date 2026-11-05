-- Gateway 0.0.7 · migración para una base PostgreSQL que ya existe (ddl-auto=update agrega tablas y columnas,
-- pero NO cambia las restricciones CHECK que Hibernate creó para los enums).

-- 1) Rol ADMINISTRADOR en users.role
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('TECNICO', 'SUPERVISOR', 'ADMINISTRADOR'));

-- 2) Primer administrador de la empresa (cambiar el correo; la contraseña se define con «Recuperar contraseña»)
-- UPDATE users SET role = 'ADMINISTRADOR' WHERE email = 'admin@empresa.pe';

-- 3) Supervisor a cargo de cada técnico (Reportes › Mis técnicos)
-- UPDATE users SET supervisor_id = (SELECT id FROM users WHERE email = 'supervisor@empresa.pe') WHERE email IN ('tecnico1@empresa.pe');

-- 4) Minutas anteriores: tipo y versión
UPDATE minutas SET tipo = 'IMPLEMENTACION' WHERE tipo IS NULL;
UPDATE minutas SET version_num = 1 WHERE version_num IS NULL;

-- Tablas nuevas (las crea Hibernate al arrancar): role_permissions, minuta_versions, reglas_empresa,
-- preguntas_flujo, fichas_conocimiento. Los roles base y las reglas base se cargan solos si las tablas están vacías.
