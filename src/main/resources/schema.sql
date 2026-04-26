-- Таблица для хранения статистики видео
CREATE TABLE IF NOT EXISTS videos (
                                      link VARCHAR(500) PRIMARY KEY,           -- URL видео (уникальный)
    platform VARCHAR(50) NOT NULL,           -- Платформа (YouTube, VK и т.д.)
    title TEXT NOT NULL,                      -- Название видео
    views_count BIGINT NOT NULL DEFAULT 0,   -- Количество просмотров
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Время последнего обновления
    hosting_unavailable BOOLEAN DEFAULT FALSE         -- Доступность платформы
    );

-- Индекс для быстрого поиска по платформе
CREATE INDEX IF NOT EXISTS idx_videos_platform ON videos(platform);

-- Индекс для сортировки по дате обновления
CREATE INDEX IF NOT EXISTS idx_videos_last_updated ON videos(last_updated DESC);