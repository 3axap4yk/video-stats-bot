package com.project.repository;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// Установление подключения к PostgreSQL через JDBC
// Создание DataSource для управления соединениями

public class DbConnection {
    private static Connection connection = null;
    private static final Dotenv dotenv = Dotenv.load();

    // Формируем URL подключения из переменных .env
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

    // Получить соединение с БД (одиночка)
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                // Загружаем драйвер PostgreSQL
                Class.forName("org.postgresql.Driver");

                System.out.println("Подключение к БД: " + getDbUrl());


                // Создаем соединение
                connection = DriverManager.getConnection(
                        getDbUrl(),
                        getDbUser(),
                        getDbPassword()
                );
                System.out.println("Подключение к PostgreSQL установлено");

            } catch (ClassNotFoundException e) {
                System.err.println("Драйвер PostgreSQL не найден. Добавьте зависимость в build.gradle");
                throw new SQLException("Driver not found", e);
            } catch (SQLException e) {
                System.err.println("Ошибка подключения к БД: " + e.getMessage());
                throw e;
            }
        }
        return connection;
    }

    // Закрыть соединение
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("Подключение к БД закрыто");
            } catch (SQLException e) {
                System.err.println("Ошибка при закрытии подключения: " + e.getMessage());
            }
        }
    }

    // Проверить подключение
    public static boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // Проверка доступности БД с возвратом статуса
    public static boolean isDatabaseAvailable() {
        try {
            Connection conn = getConnection();
            boolean isConnected = conn != null && !conn.isClosed();
            closeConnection();
            return isConnected;
        } catch (SQLException e) {
            System.err.println("❌ БД недоступна: " + e.getMessage());
            return false;
        }
    }

    public static void testConnection() {
        System.out.println("\n=== ТЕСТ ПОДКЛЮЧЕНИЯ К БД ===\n");

        try {
            // Пытаемся подключиться
            Connection conn = getConnection();

            if (conn != null && !conn.isClosed()) {
                System.out.println("✅ ПОДКЛЮЧЕНИЕ УСПЕШНО!");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("📊 Информация о подключении:");
                System.out.println("   URL: " + conn.getMetaData().getURL());
                System.out.println("   Версия БД: " + conn.getMetaData().getDatabaseProductVersion());
                System.out.println("   Драйвер: " + conn.getMetaData().getDriverName());
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            } else {
                System.out.println("❌ Не удалось получить подключение");
            }

        } catch (SQLException e) {
            System.out.println("❌ ОШИБКА ПОДКЛЮЧЕНИЯ!");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Сообщение: " + e.getMessage());
            System.out.println("\nВозможные причины:");
            System.out.println("   1. PostgreSQL не запущен");
            System.out.println("   2. Неправильные параметры в .env");
            System.out.println("   3. База данных не существует");
            System.out.println("   4. Неверный пароль");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            e.printStackTrace();
        }
    }
    public static void initDatabase() {
        String sql = """
        CREATE TABLE IF NOT EXISTS public.videos (
            id SERIAL PRIMARY KEY,
            link TEXT NOT NULL UNIQUE,
            platform VARCHAR(50),
            title TEXT,
            views_count BIGINT DEFAULT 0,
            last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            System.out.println("✅ Таблица videos готова");

        } catch (SQLException e) {
            System.err.println("❌ Ошибка при инициализации БД: " + e.getMessage());
        }
    }
}