package com.project;

// Точка входа в приложение.
// Инициализация компонентов и запуск бота

public class App {
    public static void main(String[] args) {
        System.out.println("VideoStatsBot starting...");

        // Проверка: читаем переменные окружения
        String botToken = System.getenv("BOT_TOKEN");
        String youtubeKey = System.getenv("YOUTUBE_API_KEY");

        if (botToken == null || youtubeKey == null) {
            System.out.println("Предупреждение: переменные окружения не найдены");
            System.out.println(" Запустите через docker-compose или задайте BOT_TOKEN и YOUTUBE_API_KEY");
        } else {
            System.out.println("BOT_TOKEN: " + botToken);
            System.out.println("YOUTUBE_API_KEY: " + youtubeKey);
            System.out.println("Конфигурация загружена");
        }

        System.out.println("App.java компилируется и работает!");
        // Дальше здесь будет инициализация бота:
        // new VideoStatsBot(botToken).start();
    }
}
