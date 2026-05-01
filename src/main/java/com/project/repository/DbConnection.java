package com.project.repository;

import io.github.cdimascio.dotenv.Dotenv;
import com.project.utils.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DbConnection {
    private static final Dotenv dotenv = Dotenv.load();

    private static String getDbUrl() {
        String host = dotenv.get("DB_HOST");
        String port = dotenv.get("DB_PORT");
        String dbName = dotenv.get("DB_NAME");
        return String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);
    }

    private static String getDbUser() {
        return dotenv.get("DB_USER");
    }

    private static String getDbPassword() {
        return dotenv.get("DB_PASSWORD");
    }

    /**
     * ВСЕГДА создаёт НОВОЕ соединение.
     * НЕ синглтон! Каждый вызов - новое подключение.
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
            Logger.info("Создаю новое подключение к БД: " + getDbUrl());

            Connection conn = DriverManager.getConnection(
                    getDbUrl(),
                    getDbUser(),
                    getDbPassword()
            );
            Logger.success("Новое подключение установлено");
            return conn;

        } catch (ClassNotFoundException e) {
            Logger.error("Драйвер PostgreSQL не найден");
            throw new SQLException("Driver not found", e);
        }
    }

    /**
     * Проверка доступности БД (создаём тестовое соединение и закрываем)
     */
    public static boolean isDatabaseAvailable() {
        try (Connection conn = getConnection()) {
            boolean isValid = conn != null && !conn.isClosed();
            Logger.info("Проверка БД: " + (isValid ? "доступна" : "недоступна"));
            return isValid;
        } catch (SQLException e) {
            Logger.error("БД недоступна: " + e.getMessage());
            return false;
        }
    }

    /**
     * Инициализация БД: создание таблиц
     */
    public static void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS videos (
                link VARCHAR(500) PRIMARY KEY,
                platform VARCHAR(50) NOT NULL,
                title TEXT NOT NULL,
                views_count BIGINT NOT NULL DEFAULT 0,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                hosting_unavailable BOOLEAN DEFAULT FALSE
            );
            
            CREATE INDEX IF NOT EXISTS idx_videos_platform ON videos(platform);
            CREATE INDEX IF NOT EXISTS idx_videos_last_updated ON videos(last_updated);
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            Logger.success("Таблицы БД инициализированы");
        } catch (SQLException e) {
            Logger.warn("Ошибка инициализации БД: " + e.getMessage());
        }
    }

    /**
     * Тестовое подключение (для отладки)
     */
    public static void testConnection() {
        Logger.info("=== ТЕСТ ПОДКЛЮЧЕНИЯ К БД ===");

        try (Connection conn = getConnection()) {
            if (conn != null && !conn.isClosed()) {
                Logger.success("ПОДКЛЮЧЕНИЕ УСПЕШНО!");
                Logger.info("   URL: " + conn.getMetaData().getURL());
                Logger.info("   Версия БД: " + conn.getMetaData().getDatabaseProductVersion());
            }
        } catch (SQLException e) {
            Logger.error("ОШИБКА ПОДКЛЮЧЕНИЯ: " + e.getMessage());
        }
    }
}