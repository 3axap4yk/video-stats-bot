package com.project;
import io.github.cdimascio.dotenv.Dotenv;

// Точка входа в приложение.
// Инициализация компонентов и запуск бота

public class App {
    private static final Dotenv dotenv = Dotenv.load();

    public static String getBotToken() {
        return dotenv.get("BOT_TOKEN");
    }

    public static String getYouTubeApiKey() {
        return dotenv.get("YOUTUBE_API_KEY");
    }
    public static void main(String[] args) {
        System.out.println("VideoStatsBot starting...");

        String botToken = getBotToken();
        String youtubeKey = getYouTubeApiKey();

        // Проверка: не пустые ли значения
        if (botToken == null || botToken.isEmpty() || youtubeKey == null || youtubeKey.isEmpty()) {
            System.out.println("Переменные окружения не найдены или пусты");
        } else {
            System.out.println("BOT_TOKEN найден: " + botToken);
            System.out.println("YOUTUBE_API_KEY найден: " + youtubeKey);
            System.out.println("Конфигурация загружена успешно");
        }

        System.out.println("App.java компилируется и работает!");
        // Дальше здесь будет инициализация бота:
        // new VideoStatsBot(botToken).start();
    }
}
