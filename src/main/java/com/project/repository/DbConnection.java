package com.project.repository;

import com.project.utils.Logger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DbConnection {
    private static HikariDataSource dataSource;

    static {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

            String host = dotenv.get("DB_HOST");
            String port = dotenv.get("DB_PORT");
            String dbName = dotenv.get("DB_NAME");
            String user = dotenv.get("DB_USER");
            String password = dotenv.get("DB_PASSWORD");

            String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(user);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            // Загружаем драйвер
            Class.forName("org.postgresql.Driver");

            dataSource = new HikariDataSource(config);
            Logger.info("Пул соединений с БД инициализирован");

            // Инициализируем таблицы
            initDatabase();

        } catch (ClassNotFoundException e) {
            Logger.error("Драйвер PostgreSQL не найден", e);
            throw new RuntimeException("Драйвер PostgreSQL не найден", e);
        } catch (Exception e) {
            Logger.error("Ошибка инициализации пула соединений: " + e.getMessage(), e);
            throw new RuntimeException("Ошибка инициализации пула соединений", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Пул соединений не инициализирован");
        }
        return dataSource.getConnection();
    }

    public static void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS videos (" +
                "    link VARCHAR(500) PRIMARY KEY," +
                "    platform VARCHAR(50) NOT NULL," +
                "    title TEXT NOT NULL," +
                "    views_count BIGINT NOT NULL DEFAULT 0," +
                "    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    hosting_unavailable BOOLEAN DEFAULT FALSE" +
                ");" +
                "CREATE INDEX IF NOT EXISTS idx_videos_platform ON videos(platform);" +
                "CREATE INDEX IF NOT EXISTS idx_videos_last_updated ON videos(last_updated);";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            Logger.info("Таблицы БД инициализированы");
        } catch (SQLException e) {
            Logger.error("Ошибка инициализации БД: " + e.getMessage(), e);
        }
    }

    public static boolean isDatabaseAvailable() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            Logger.warn("БД недоступна: " + e.getMessage());
            return false;
        }
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            Logger.info("Пул соединений с БД закрыт");
        }
    }

    public static String getDbUrl() {
        return dataSource != null ? dataSource.getJdbcUrl() : null;
    }

    public static String getDbUser() {
        return dataSource != null ? dataSource.getUsername() : null;
    }

    public static String getDbPassword() {
        return dataSource != null ? dataSource.getPassword() : null;
    }
}