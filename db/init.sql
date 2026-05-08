-- =====================================================
-- АВТОМАТИЧЕСКОЕ СОЗДАНИЕ ТАБЛИЦ
-- Расширенная версия (с индексами, CASCADE, историей просмотров и триггерами)
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

CREATE SEQUENCE IF NOT EXISTS public.views_history_id_seq
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

-- 4. Таблица views_history (история изменения просмотров)
CREATE TABLE IF NOT EXISTS public.views_history (
    id INTEGER NOT NULL DEFAULT nextval('public.views_history_id_seq'),
    video_id INTEGER NOT NULL,
    views_count BIGINT NOT NULL,
    recorded_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT views_history_pkey PRIMARY KEY (id),
    CONSTRAINT views_history_video_id_fkey FOREIGN KEY (video_id)
        REFERENCES public.videos(id) ON DELETE CASCADE
);

-- 5. Таблица vk (специфичные данные для VK)
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

-- 6. Таблица youtube (специфичные данные для YouTube)
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

-- 7. Привязка последовательностей к колонкам
ALTER SEQUENCE public.videos_id_seq OWNED BY public.videos.id;
ALTER SEQUENCE public.views_history_id_seq OWNED BY public.views_history.id;
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

CREATE INDEX IF NOT EXISTS idx_videos_created_at
    ON public.videos(created_at DESC);

-- Индексы для таблицы views_history
CREATE INDEX IF NOT EXISTS idx_views_history_video_id
    ON public.views_history(video_id);



CREATE INDEX IF NOT EXISTS idx_views_history_recorded_at
    ON public.views_history(recorded_at DESC);

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

-- 9. Создание функций для триггеров

-- Функция нормализации названия платформы
CREATE OR REPLACE FUNCTION public.normalize_platform()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.platform ILIKE '%youtube%' THEN
        NEW.platform := 'YouTube';
    ELSIF NEW.platform ILIKE '%vk%' THEN
        NEW.platform := 'VK Video';
    ELSIF NEW.platform ILIKE '%rutube%' THEN
        NEW.platform := 'RuTube';
    ELSIF NEW.platform ILIKE '%дзен%' OR NEW.platform ILIKE '%zen%' THEN
        NEW.platform := 'Дзен';
    ELSE
        NEW.platform := 'UNKNOWN';
    END IF;

    RETURN NEW;
END;
$function$;

-- Функция логирования нового видео
CREATE OR REPLACE FUNCTION public.log_new_video()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    INSERT INTO views_history (video_id, views_count, recorded_at)
    VALUES (NEW.id, NEW.views_count, NOW());
    RETURN NEW;
END;
$function$;

-- Функция логирования изменения просмотров
CREATE OR REPLACE FUNCTION public.log_views_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    IF OLD.views_count IS DISTINCT FROM NEW.views_count THEN
        INSERT INTO views_history (video_id, views_count, recorded_at)
        VALUES (NEW.id, NEW.views_count, NOW());
    END IF;
    RETURN NEW;
END;
$function$;

-- 10. Создание триггеров

-- Триггер для нормализации платформы (перед вставкой или обновлением)
DROP TRIGGER IF EXISTS trg_normalize_platform ON public.videos;
CREATE TRIGGER trg_normalize_platform
    BEFORE INSERT OR UPDATE OF platform ON public.videos
    FOR EACH ROW
    EXECUTE FUNCTION normalize_platform();

-- Триггер для логирования нового видео (после вставки)
DROP TRIGGER IF EXISTS trigger_log_new_video ON public.videos;
CREATE TRIGGER trigger_log_new_video
    AFTER INSERT ON public.videos
    FOR EACH ROW
    EXECUTE FUNCTION log_new_video();

-- Триггер для логирования изменения просмотров (после обновления)
DROP TRIGGER IF EXISTS trigger_log_views_change ON public.videos;
CREATE TRIGGER trigger_log_views_change
    AFTER UPDATE OF views_count ON public.videos
    FOR EACH ROW
    WHEN (OLD.views_count IS DISTINCT FROM NEW.views_count)
    EXECUTE FUNCTION log_views_change();

-- 11. Комментарии к таблицам и колонкам (документация)
COMMENT ON TABLE public.videos IS 'Общая информация о всех видео';
COMMENT ON COLUMN public.videos.link IS 'URL видео (уникальный идентификатор)';
COMMENT ON COLUMN public.videos.platform IS 'Платформа: youtube, vk, rutube и т.д.';
COMMENT ON COLUMN public.videos.views_count IS 'Количество просмотров';
COMMENT ON COLUMN public.videos.last_updated IS 'Время последнего обновления данных';
COMMENT ON COLUMN public.videos.hosting_unavailable IS 'Флаг недоступности хостинга';

COMMENT ON TABLE public.views_history IS 'История изменения количества просмотров видео';
COMMENT ON COLUMN public.views_history.video_id IS 'ID видео из таблицы videos';
COMMENT ON COLUMN public.views_history.views_count IS 'Количество просмотров в момент записи';
COMMENT ON COLUMN public.views_history.recorded_at IS 'Время записи значения просмотров';

COMMENT ON TABLE public.vk IS 'Специфичные данные для видео VK';
COMMENT ON COLUMN public.vk.id_vk IS 'Внутренний ID видео в VK';
COMMENT ON COLUMN public.vk.id_vk_external IS 'Внешний ID видео в VK';

COMMENT ON TABLE public.youtube IS 'Специфичные данные для видео YouTube';
COMMENT ON COLUMN public.youtube.id_youtube IS 'ID видео на YouTube';

-- 12. Обновление статистики для оптимизатора
ANALYZE public.videos;
ANALYZE public.views_history;
ANALYZE public.vk;
ANALYZE public.youtube;