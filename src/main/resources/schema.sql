-- =====================================================
-- АВТОМАТИЧЕСКОЕ СОЗДАНИЕ ТАБЛИЦ
-- Улучшенная версия (с индексами и CASCADE)
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

-- 3. Таблица videos (общая информация о видео)
CREATE TABLE IF NOT EXISTS public.videos (
                                             id INTEGER NOT NULL DEFAULT nextval('public.videos_id_seq'),
    link TEXT NOT NULL,
    platform VARCHAR(50),
    title TEXT,
    views_count BIGINT DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    hosting_unavailable BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT videos_pkey PRIMARY KEY (id),
    CONSTRAINT videos_link_key UNIQUE (link)
    );

-- 4. Таблица vk (специфичные данные для VK)
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
    REFERENCES public.videos(link) ON DELETE CASCADE
    );

-- 5. Таблица youtube (специфичные данные для YouTube)
CREATE TABLE IF NOT EXISTS public.youtube (
                                              id INTEGER NOT NULL DEFAULT nextval('public.youtube_id_seq'),
    video_link TEXT NOT NULL,
    id_youtube VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT youtube_pkey PRIMARY KEY (id),
    CONSTRAINT youtube_video_link_key UNIQUE (video_link),
    CONSTRAINT youtube_video_link_fkey FOREIGN KEY (video_link)
    REFERENCES public.videos(link) ON DELETE CASCADE
    );

-- 6. Таблица истории просмотров (для аналитики)
CREATE TABLE IF NOT EXISTS public.views_history (
                                                    id SERIAL PRIMARY KEY,
                                                    video_id INTEGER NOT NULL,
                                                    views_count BIGINT NOT NULL,
                                                    recorded_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT views_history_video_id_fkey FOREIGN KEY (video_id)
    REFERENCES public.videos(id) ON DELETE CASCADE
    );

-- 7. Привязка последовательностей к колонкам
ALTER SEQUENCE public.videos_id_seq OWNED BY public.videos.id;
ALTER SEQUENCE public.vk_video_info_id_seq OWNED BY public.vk.id;
ALTER SEQUENCE public.youtube_id_seq OWNED BY public.youtube.id;

-- 8. Создание индексов для оптимизации запросов

-- Индексы для таблицы videos
CREATE INDEX IF NOT EXISTS idx_videos_platform
    ON public.videos(platform);

CREATE INDEX IF NOT EXISTS idx_videos_last_updated
    ON public.videos(last_updated);

CREATE INDEX IF NOT EXISTS idx_videos_views_count
    ON public.videos(views_count DESC);

CREATE INDEX IF NOT EXISTS idx_videos_hosting_unavailable
    ON public.videos(hosting_unavailable);

CREATE INDEX IF NOT EXISTS idx_videos_platform_last_updated
    ON public.videos(platform, last_updated);

-- Индексы для таблицы vk
CREATE INDEX IF NOT EXISTS idx_vk_video_link
    ON public.vk(video_link);

CREATE INDEX IF NOT EXISTS idx_vk_id_vk
    ON public.vk(id_vk);

CREATE INDEX IF NOT EXISTS idx_vk_created_at
    ON public.vk(created_at);

-- Индексы для таблицы youtube
CREATE INDEX IF NOT EXISTS idx_youtube_video_link
    ON public.youtube(video_link);

CREATE INDEX IF NOT EXISTS idx_youtube_id_youtube
    ON public.youtube(id_youtube);

CREATE INDEX IF NOT EXISTS idx_youtube_created_at
    ON public.youtube(created_at);

-- Индексы для таблицы views_history
CREATE INDEX IF NOT EXISTS idx_views_history_video_id
    ON public.views_history(video_id);

CREATE INDEX IF NOT EXISTS idx_views_history_recorded_at
    ON public.views_history(recorded_at DESC);

-- 9. Комментарии к таблицам и колонкам (документация)
COMMENT ON TABLE public.videos IS 'Общая информация о всех видео';
COMMENT ON COLUMN public.videos.link IS 'URL видео (уникальный идентификатор)';
COMMENT ON COLUMN public.videos.platform IS 'Платформа: youtube, vk, rutube и т.д.';
COMMENT ON COLUMN public.videos.views_count IS 'Количество просмотров';
COMMENT ON COLUMN public.videos.last_updated IS 'Время последнего обновления данных';
COMMENT ON COLUMN public.videos.hosting_unavailable IS 'Флаг недоступности хостинга';

COMMENT ON TABLE public.vk IS 'Специфичные данные для видео VK';
COMMENT ON COLUMN public.vk.id_vk IS 'Внутренний ID видео в VK';
COMMENT ON COLUMN public.vk.id_vk_external IS 'Внешний ID видео в VK';

COMMENT ON TABLE public.youtube IS 'Специфичные данные для видео YouTube';
COMMENT ON COLUMN public.youtube.id_youtube IS 'ID видео на YouTube';

COMMENT ON TABLE public.views_history IS 'История изменения просмотров для аналитики динамики';
COMMENT ON COLUMN public.views_history.video_id IS 'Ссылка на видео';
COMMENT ON COLUMN public.views_history.views_count IS 'Количество просмотров в момент замера';
COMMENT ON COLUMN public.views_history.recorded_at IS 'Время замера';

-- 10. Обновление статистики для оптимизатора
ANALYZE public.videos;
ANALYZE public.vk;
ANALYZE public.youtube;
ANALYZE public.views_history;