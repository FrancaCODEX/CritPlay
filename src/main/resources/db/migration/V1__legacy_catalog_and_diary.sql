-- Também representa o esquema da primeira entrega, anterior às contas de usuário.
CREATE TABLE games (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genre VARCHAR(255),
    platforms VARCHAR(255),
    release_year INTEGER NOT NULL,
    steam_app_id BIGINT,
    description VARCHAR(2000)
);
CREATE TABLE diary_entries (
    game_id BIGINT PRIMARY KEY,
    status VARCHAR(255) NOT NULL,
    rating INTEGER,
    review VARCHAR(2000),
    favorite BOOLEAN NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE
);
