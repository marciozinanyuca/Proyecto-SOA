-- Bootstrap de una base nueva del contenedor; no copia ni reemplaza usuarios locales.
-- Las bases existentes incompatibles deben fallar en validate, sin migracion automatica.
CREATE TABLE IF NOT EXISTS usuarios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre VARCHAR(120) NOT NULL,
    username VARCHAR(60) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'ANALISTA')),
    activo BOOLEAN NOT NULL
);
