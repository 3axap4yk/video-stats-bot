CREATE TABLE IF NOT EXISTS public.videos (
    id SERIAL PRIMARY KEY,
    link TEXT NOT NULL UNIQUE,
    platform VARCHAR(50),
    title TEXT,
    views_count BIGINT DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);