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

            Class.forName("org.postgresql.Driver");

            dataSource = new HikariDataSource(config);
            Logger.info("Пул соединений с БД инициализирован");

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