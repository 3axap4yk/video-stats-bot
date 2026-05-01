package com.project.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Утилитарный класс для логирования
 * Обеспечивает единообразный вывод сообщений с временными метками
 */
public final class Logger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Logger() {
        // Приватный конструктор для утилитарного класса
    }

    /**
     * Информационное сообщение
     */
    public static void info(String message) {
        System.out.println(formatMessage("ℹ️ INFO", message));
    }

    /**
     * Сообщение об успехе
     */
    public static void success(String message) {
        System.out.println(formatMessage("✅ SUCCESS", message));
    }

    /**
     * Предупреждение
     */
    public static void warn(String message) {
        System.out.println(formatMessage("⚠️ WARN", message));
    }

    /**
     * Сообщение об ошибке
     */
    public static void error(String message) {
        System.err.println(formatMessage("❌ ERROR", message));
    }

    /**
     * Сообщение об ошибке с исключением
     */
    public static void error(String message, Throwable e) {
        System.err.println(formatMessage("❌ ERROR", message + " - " + e.getMessage()));
        e.printStackTrace();
    }

    private static String formatMessage(String level, String message) {
        return "[" + LocalDateTime.now().format(FORMATTER) + "] " + level + ": " + message;
    }
}