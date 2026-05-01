package com.project.utils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Утилитарный класс для форматирования данных перед отправкой в Telegram
 * Содержит методы для форматирования просмотров и экранирования HTML
 */
public final class ViewFormatter {

    private ViewFormatter() {
        // Приватный конструктор для утилитарного класса
    }

    /**
     * Форматирует число просмотров с разделителями разрядов
     * Пример: 1234567 → "1 234 567"
     *
     * @param views количество просмотров
     * @return отформатированная строка
     */
    public static String formatViews(long views) {
        String formatted = NumberFormat.getInstance(new Locale("ru", "RU")).format(views);
        return formatted.replace('\u00A0', ' ');
    }

    /**
     * Экранирует спецсимволы HTML для безопасной вставки пользовательских данных
     * Заменяет: & < > " '
     *
     * @param text исходный текст
     * @return текст с экранированными HTML-символами
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}