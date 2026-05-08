package com.project.repository;

import com.project.utils.Logger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Управление подключением к базе данных PostgreSQL.
 * Использует HikariCP для пула соединений (эффективное управление подключениями).
 * Конфигурация загружается из переменных окружения (.env файл).
 *
 * Структура БД:
 * - videos: основная таблица со всеми видео
 * - youtube: дополнительная информация для YouTube видео
 * - vk: дополнительная информация для VK видео
 * - views_history: история изменения просмотров для аналитики
 *
 * Триггеры:
 * - trigger_log_new_video: автоматически добавляет запись в views_history при INSERT в videos
 * - trigger_log_views_change: автоматически добавляет запись в views_history при изменении views_count
 */
public class DbConnection {

    // Пул соединений HikariCP - управляет переиспользованием подключений к БД
    private static HikariDataSource dataSource;

    /**
     * Статический блок инициализации - выполняется один раз при загрузке класса.
     * Загружает конфигурацию, создаёт пул соединений и инициализирует таблицы.
     */
    static {
        try {
            // Загружаем переменные окружения из .env файла
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

            // Параметры подключения к БД из .env
            String host = dotenv.get("DB_HOST");           // Хост: localhost или IP сервера
            String port = dotenv.get("DB_PORT");           // Порт: обычно 5432
            String dbName = dotenv.get("DB_NAME");         // Имя базы данных
            String user = dotenv.get("DB_USER");           // Имя пользователя
            String password = dotenv.get("DB_PASSWORD");   // Пароль

            // Формируем JDBC URL для подключения к PostgreSQL
            String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);

            // Настройка пула соединений HikariCP
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);                    // URL для подключения
            config.setUsername(user);                      // Имя пользователя
            config.setPassword(password);                  // Пароль
            config.setMaximumPoolSize(10);                 // Максимум 10 активных соединений
            config.setMinimumIdle(2);                      // Минимум 2 idle соединения в пуле
            config.setConnectionTimeout(30000);            // Таймаут получения соединения: 30 секунд
            config.setIdleTimeout(600000);                 // Таймаут idle соединения: 10 минут
            config.setMaxLifetime(1800000);                // Максимальное время жизни соединения: 30 минут

            // Загружаем JDBC драйвер PostgreSQL (регистрируем в JVM)
            Class.forName("org.postgresql.Driver");

            // Создаём пул соединений
            dataSource = new HikariDataSource(config);
            Logger.info("Пул соединений с БД инициализирован");

            // Создаём таблицы, если они ещё не существуют
            initDatabase();

        } catch (ClassNotFoundException e) {
            // Драйвер PostgreSQL не найден в classpath (проверьте зависимость в build.gradle)
            Logger.error("Драйвер PostgreSQL не найден", e);
            throw new RuntimeException("Драйвер PostgreSQL не найден", e);
        } catch (Exception e) {
            // Другая ошибка (неверные параметры, БД недоступна и т.д.)
            Logger.error("Ошибка инициализации пула соединений: " + e.getMessage(), e);
            throw new RuntimeException("Ошибка инициализации пула соединений", e);
        }
    }

    /**
     * Возвращает соединение с базой данных из пула.
     * Всегда закрывайте соединение после использования (try-with-resources).
     *
     * @return активное соединение с БД
     * @throws SQLException если пул не инициализирован или ошибка подключения
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Пул соединений не инициализирован");
        }
        return dataSource.getConnection();
    }

    /**
     * Инициализирует структуру базы данных:
     * - Создаёт таблицы (если не существуют)
     * - Создаёт индексы для ускорения запросов
     * - Создаёт триггеры для автоматического логирования истории просмотров
     *
     * Таблицы:
     * 1. videos - основная таблица со всеми видео
     * 2. youtube - YouTube ID для видео
     * 3. vk - VK ID для видео
     * 4. views_history - история просмотров для аналитики динамики
     *
     * Триггеры:
     * 1. log_new_video() + trigger_log_new_video - при вставке нового видео
     * 2. log_views_change() + trigger_log_views_change - при изменении просмотров
     *
     * Индексы созданы для часто используемых полей в WHERE, ORDER BY, JOIN.
     */
    public static void initDatabase() {
        String sql = """
            -- =====================================================
            -- ОСНОВНАЯ ТАБЛИЦА videos
            -- Хранит все видео независимо от платформы
            -- =====================================================
            CREATE TABLE IF NOT EXISTS videos (
                id SERIAL PRIMARY KEY,                    -- Уникальный ID видео (автоинкремент)
                link TEXT NOT NULL UNIQUE,                -- Полная ссылка на видео (уникальная)
                platform VARCHAR(50) NOT NULL,            -- Платформа: 'YouTube' или 'VK'
                title TEXT NOT NULL,                      -- Название видео
                views_count BIGINT NOT NULL DEFAULT 0,    -- Текущее количество просмотров
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Время последнего обновления
                hosting_unavailable BOOLEAN DEFAULT FALSE, -- TRUE если платформа временно недоступна
                created_at TIMESTAMP DEFAULT NOW()        -- Время добавления видео (для аналитики)
            );
            
            -- =====================================================
            -- ТАБЛИЦА vk
            -- Дополнительная информация для VK видео
            -- =====================================================
            CREATE TABLE IF NOT EXISTS vk (
                id SERIAL PRIMARY KEY,
                video_link TEXT NOT NULL UNIQUE,          -- Ссылка на VK видео (связь с videos.link)
                id_vk VARCHAR(50),                        -- VK ID видео (ownerId_videoId)
                created_at TIMESTAMP DEFAULT NOW(),       -- Время создания записи
                updated_at TIMESTAMP DEFAULT NOW()        -- Время последнего обновления
            );
            
            -- =====================================================
            -- ТАБЛИЦА youtube
            -- Дополнительная информация для YouTube видео
            -- =====================================================
            CREATE TABLE IF NOT EXISTS youtube (
                id SERIAL PRIMARY KEY,
                video_link TEXT NOT NULL UNIQUE,          -- Ссылка на YouTube видео
                id_youtube VARCHAR(50),                   -- YouTube Video ID (11 символов)
                created_at TIMESTAMP DEFAULT NOW(),
                updated_at TIMESTAMP DEFAULT NOW()
            );
            
            -- =====================================================
            -- ТАБЛИЦА views_history
            -- История изменения просмотров (для расчёта динамики)
            -- При каждом обновлении статистики автоматически добавляется новая запись через триггер
            -- =====================================================
            CREATE TABLE IF NOT EXISTS views_history (
                id SERIAL PRIMARY KEY,
                video_id INTEGER NOT NULL REFERENCES videos(id) ON DELETE CASCADE,  -- Внешний ключ к videos
                views_count BIGINT NOT NULL,               -- Значение просмотров в момент записи
                recorded_at TIMESTAMP DEFAULT NOW()        -- Время записи
            );
            
            -- =====================================================
            -- ИНДЕКСЫ ДЛЯ ТАБЛИЦЫ videos
            -- Ускоряют поиск и сортировку
            -- =====================================================
            CREATE INDEX IF NOT EXISTS idx_videos_platform ON videos(platform);           -- Поиск по платформе
            CREATE INDEX IF NOT EXISTS idx_videos_last_updated ON videos(last_updated);   -- Сортировка по дате обновления
            CREATE INDEX IF NOT EXISTS idx_videos_views_count ON videos(views_count DESC); -- Сортировка по просмотрам (для ТОПа)
            CREATE INDEX IF NOT EXISTS idx_videos_hosting_unavailable ON videos(hosting_unavailable); -- Поиск недоступных
            CREATE INDEX IF NOT EXISTS idx_videos_created_at ON videos(created_at DESC);   -- Сортировка по дате добавления
            
            -- Индексы для таблицы vk
            CREATE INDEX IF NOT EXISTS idx_vk_video_link ON vk(video_link);  -- Быстрый поиск по ссылке
            CREATE INDEX IF NOT EXISTS idx_vk_id_vk ON vk(id_vk);            -- Быстрый поиск по VK ID
            
            -- Индексы для таблицы youtube
            CREATE INDEX IF NOT EXISTS idx_youtube_video_link ON youtube(video_link);
            CREATE INDEX IF NOT EXISTS idx_youtube_id_youtube ON youtube(id_youtube);
            
            -- Индексы для таблицы views_history
            CREATE INDEX IF NOT EXISTS idx_views_history_video_id ON views_history(video_id);       -- JOIN с videos
            CREATE INDEX IF NOT EXISTS idx_views_history_recorded_at ON views_history(recorded_at DESC); -- Динамика по времени
            
            -- =====================================================
            -- ТРИГГЕРЫ ДЛЯ АВТОМАТИЧЕСКОГО ЛОГИРОВАНИЯ ИСТОРИИ ПРОСМОТРОВ
            -- =====================================================
            -- Это РЕШЕНИЕ проблемы транзакций:
            -- Больше не нужно вручную вызывать saveToHistory() из Java-кода.
            -- Триггеры сами добавят запись в views_history при INSERT и UPDATE.
            -- Плюсы: нет риска разъезда данных, работает для batch-операций, не требует изменений в Java.
            
            -- Функция для INSERT (новое видео)
            -- Вызывается автоматически после вставки строки в таблицу videos
            CREATE OR REPLACE FUNCTION log_new_video()
            RETURNS TRIGGER AS $$
            BEGIN
                INSERT INTO views_history (video_id, views_count, recorded_at)
                VALUES (NEW.id, NEW.views_count, NOW());
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
            
            -- Триггер на INSERT в таблицу videos
            -- Срабатывает ПОСЛЕ вставки (AFTER INSERT) для каждой строки (FOR EACH ROW)
            DROP TRIGGER IF EXISTS trigger_log_new_video ON videos;
            CREATE TRIGGER trigger_log_new_video
            AFTER INSERT ON videos
            FOR EACH ROW
            EXECUTE FUNCTION log_new_video();
            
            -- Функция для UPDATE (изменение просмотров)
            -- Вызывается только если количество просмотров реально изменилось
            CREATE OR REPLACE FUNCTION log_views_change()
            RETURNS TRIGGER AS $$
            BEGIN
                -- Проверяем, изменилось ли значение просмотров
                -- IS DISTINCT FROM учитывает NULL (хотя у нас views_count NOT NULL)
                IF OLD.views_count IS DISTINCT FROM NEW.views_count THEN
                    INSERT INTO views_history (video_id, views_count, recorded_at)
                    VALUES (NEW.id, NEW.views_count, NOW());
                END IF;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
            
            -- Триггер на UPDATE в таблицу videos
            -- Срабатывает только при обновлении колонки views_count
            -- WHEN (OLD.views_count IS DISTINCT FROM NEW.views_count) - дополнительная оптимизация
            DROP TRIGGER IF EXISTS trigger_log_views_change ON videos;
            CREATE TRIGGER trigger_log_views_change
            AFTER UPDATE OF views_count ON videos
            FOR EACH ROW
            WHEN (OLD.views_count IS DISTINCT FROM NEW.views_count)
            EXECUTE FUNCTION log_views_change();
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            Logger.info("Таблицы БД инициализированы");
            Logger.info("Триггеры для views_history настроены (автоматическое логирование)");
        } catch (SQLException e) {
            Logger.error("Ошибка инициализации БД: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет доступность базы данных.
     * Используется при старте бота для определения режима работы.
     *
     * @return true если БД доступна и можно выполнять запросы, false если нет
     */
    public static boolean isDatabaseAvailable() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            Logger.warn("БД недоступна: " + e.getMessage());
            return false;
        }
    }

    /**
     * Закрывает пул соединений.
     * Вызывается при завершении работы приложения (graceful shutdown).
     */
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            Logger.info("Пул соединений с БД закрыт");
        }
    }

    // =====================================================
    // ГЕТТЕРЫ ДЛЯ ДИАГНОСТИКИ (для отладки и логирования)
    // =====================================================

    /** Возвращает JDBC URL подключения (без пароля) */
    public static String getDbUrl() {
        return dataSource != null ? dataSource.getJdbcUrl() : null;
    }

    /** Возвращает имя пользователя БД */
    public static String getDbUser() {
        return dataSource != null ? dataSource.getUsername() : null;
    }

    /** Возвращает пароль БД (аккуратно с логированием!) */
    public static String getDbPassword() {
        return dataSource != null ? dataSource.getPassword() : null;
    }
}