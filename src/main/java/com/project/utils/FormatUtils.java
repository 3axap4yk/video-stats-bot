package com.project.utils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Утилитарный класс для форматирования данных
 * Единое место для всех методов форматирования в проекте
 */
public final class FormatUtils {

    private FormatUtils() {
        // Приватный конструктор для утилитарного класса
    }

    /**
     * Форматирует число просмотров с разделителями тысяч
     * Пример: 1234567 → "1 234 567"
     *
     * @param views количество просмотров
     * @return отформатированная строка с пробелами как разделителями тысяч
     */
    public static String formatViews(long views) {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("ru", "RU"));
        String formatted = formatter.format(views);
        // Заменяем неразрывный пробел на обычный для корректного отображения в Telegram
        return formatted.replace('\u00A0', ' ');
    }

    /**
     * Форматирует просмотры с сокращением для больших чисел (опционально)
     * Пример: 1 234 567 → "1.2M"
     *
     * @param views количество просмотров
     * @return сокращённый формат
     */
    public static String formatViewsCompact(long views) {
        if (views < 1000) {
            return String.valueOf(views);
        }
        if (views < 1_000_000) {
            double thousands = views / 1000.0;
            return String.format(Locale.ROOT, "%.1fK", thousands).replace(",", ".");
        }
        if (views < 1_000_000_000) {
            double millions = views / 1_000_000.0;
            return String.format(Locale.ROOT, "%.1fM", millions).replace(",", ".");
        }
        double billions = views / 1_000_000_000.0;
        return String.format(Locale.ROOT, "%.1fB", billions).replace(",", ".");
    }
}