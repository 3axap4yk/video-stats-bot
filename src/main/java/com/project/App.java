package com.project;

import com.pengrad.telegrambot.TelegramBot;
import com.project.bot.TelegramUserWhitelist;
import com.project.bot.UrlResolver;
import com.project.bot.VideoStatsBot;
import com.project.repository.DbConnection;
import com.project.service.StatisticsService;
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
            System.out.println("❌ Переменные окружения не найдены или пусты");
            System.out.println("Проверьте файл .env в корне проекта");
            return;
        }

        System.out.println("✅ BOT_TOKEN найден: " + botToken);
        System.out.println("✅ YOUTUBE_API_KEY найден: " + youtubeKey);
        System.out.println("Конфигурация загружена успешно");

        // Проверка доступности БД (до запуска бота)
        boolean dbAvailable = DbConnection.isDatabaseAvailable();
        if (!dbAvailable) {
            System.err.println("⚠️ БД недоступна, бот будет работать без сохранения данных");
        } else {
            System.out.println("✅ БД доступна");
        }

        // Инициализация компонентов
        TelegramBot bot = new TelegramBot(botToken);
        UrlResolver urlResolver = new UrlResolver();
        StatisticsService statisticsService = new StatisticsService();
        TelegramUserWhitelist whitelist = TelegramUserWhitelist.fromCommaSeparatedIds(null);

        // Запуск бота
        VideoStatsBot videoStatsBot = new VideoStatsBot(bot, urlResolver, statisticsService, whitelist);
        videoStatsBot.start();

        System.out.println("✅ Бот запущен и слушает сообщения...");
        System.out.println("Нажмите Ctrl+C для остановки");
    }
}