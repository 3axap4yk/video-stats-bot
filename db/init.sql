-- =====================================================
-- АВТОМАТИЧЕСКОЕ СОЗДАНИЕ ТАБЛИЦ
-- Полное соответствие исходному DDL
-- =====================================================

-- 1. Создание схемы (если не существует)
CREATE SCHEMA IF NOT EXISTS public;

-- 2. Создание последовательностей
CREATE SEQUENCE IF NOT EXISTS public.videos_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 2147483647
    START 1
    CACHE 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS public.vk_video_info_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 2147483647
    START 1
    CACHE 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS public.youtube_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 2147483647
    START 1
    CACHE 1
    NO CYCLE;

-- 3. Таблица videos
CREATE TABLE IF NOT EXISTS public.videos (
    id INTEGER NOT NULL DEFAULT nextval('public.videos_id_seq'),
    link TEXT NOT NULL,
    platform VARCHAR(50),
    title TEXT,
    views_count BIGINT DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    hosting_unavailable BOOLEAN,

    CONSTRAINT videos_pkey PRIMARY KEY (id),
    CONSTRAINT videos_link_key UNIQUE (link)
);

-- 4. Таблица vk
CREATE TABLE IF NOT EXISTS public.vk (
    id INTEGER NOT NULL DEFAULT nextval('public.vk_video_info_id_seq'),
    video_link TEXT NOT NULL,
    id_vk VARCHAR(50),
    id_vk_external VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT vk_video_info_pkey PRIMARY KEY (id),
    CONSTRAINT vk_video_info_video_link_key UNIQUE (video_link),
    CONSTRAINT vk_videos_fk FOREIGN KEY (video_link)
        REFERENCES public.videos(link)
);

-- 5. Таблица youtube
CREATE TABLE IF NOT EXISTS public.youtube (
    id INTEGER NOT NULL DEFAULT nextval('public.youtube_id_seq'),
    video_link TEXT NOT NULL,
    id_youtube VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT youtube_pkey PRIMARY KEY (id),
    CONSTRAINT youtube_video_link_key UNIQUE (video_link),
    CONSTRAINT youtube_video_link_fkey FOREIGN KEY (video_link)
        REFERENCES public.videos(link)
        ON DELETE CASCADE
);

-- 6. Привязка последовательностей к колонкам (для правильного управления)
ALTER SEQUENCE public.videos_id_seq
    OWNED BY public.videos.id;

ALTER SEQUENCE public.vk_video_info_id_seq
    OWNED BY public.vk.id;

ALTER SEQUENCE public.youtube_id_seq
    OWNED BY public.youtube.id;