package com.project.repository;

import io.github.cdimascio.dotenv.Dotenv;
import com.project.utils.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Установление подключения к PostgreSQL через JDBC
 * Создание DataSource для управления соединениями
 */
public class DbConnection {
    private static Connection connection = null;
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
     * Получить соединение с БД (одиночка)
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.postgresql.Driver");
                Logger.info("Подключение к БД: " + getDbUrl());

                connection = DriverManager.getConnection(
                        getDbUrl(),
                        getDbUser(),
                        getDbPassword()
                );
                Logger.success("Подключение к PostgreSQL установлено");

            } catch (ClassNotFoundException e) {
                Logger.error("Драйвер PostgreSQL не найден. Добавьте зависимость в build.gradle");
                throw new SQLException("Driver not found", e);
            } catch (SQLException e) {
                Logger.error("Ошибка подключения к БД: " + e.getMessage());
                throw e;
            }
        }
        return connection;
    }

    /**
     * Закрыть соединение
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                Logger.info("Подключение к БД закрыто");
            } catch (SQLException e) {
                Logger.error("Ошибка при закрытии подключения: " + e.getMessage());
            }
        }
    }

    /**
     * Проверить подключение
     */
    public static boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Проверка доступности БД с возвратом статуса
     */
    public static boolean isDatabaseAvailable() {
        try {
            Connection conn = getConnection();
            boolean isConnected = conn != null && !conn.isClosed();
            closeConnection();
            return isConnected;
        } catch (SQLException e) {
            Logger.error("БД недоступна: " + e.getMessage());
            return false;
        }
    }

    /**
     * Инициализация БД: создание таблиц, если они не существуют
     * Совместимо с кодом Никиты (hostingUnavailable) и Ивана (Docker)
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
     * Тестовое подключение к БД (для отладки)
     */
    public static void testConnection() {
        Logger.info("=== ТЕСТ ПОДКЛЮЧЕНИЯ К БД ===");

        try {
            Connection conn = getConnection();

            if (conn != null && !conn.isClosed()) {
                Logger.success("ПОДКЛЮЧЕНИЕ УСПЕШНО!");
                Logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                Logger.info("📊 Информация о подключении:");
                Logger.info("   URL: " + conn.getMetaData().getURL());
                Logger.info("   Версия БД: " + conn.getMetaData().getDatabaseProductVersion());
                Logger.info("   Драйвер: " + conn.getMetaData().getDriverName());
                Logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            } else {
                Logger.error("Не удалось получить подключение");
            }

        } catch (SQLException e) {
            Logger.error("ОШИБКА ПОДКЛЮЧЕНИЯ!");
            Logger.error("Сообщение: " + e.getMessage());
            Logger.warn("Возможные причины:");
            Logger.warn("   1. PostgreSQL не запущен");
            Logger.warn("   2. Неправильные параметры в .env");
            Logger.warn("   3. База данных не существует");
            Logger.warn("   4. Неверный пароль");
            e.printStackTrace();
        }
    }
}