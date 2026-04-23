package com.project;

import com.pengrad.telegrambot.TelegramBot;
import com.project.bot.TelegramUserWhitelist;
import com.project.bot.UrlResolver;
import com.project.bot.VideoStatsBot;
import com.project.repository.DbConnection;
import io.github.cdimascio.dotenv.Dotenv;

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

        if (botToken == null || botToken.isEmpty() || youtubeKey == null || youtubeKey.isEmpty()) {
            System.out.println("❌ Переменные окружения не найдены или пусты");
            System.out.println("Проверьте файл .env в корне проекта");
            return;
        }

        System.out.println("✅ BOT_TOKEN найден: " + botToken);
        System.out.println("✅ YOUTUBE_API_KEY найден: " + youtubeKey);
        System.out.println("Конфигурация загружена успешно");

        boolean dbAvailable = DbConnection.isDatabaseAvailable();
        if (!dbAvailable) {
            System.err.println("⚠️ БД недоступна, бот будет работать без сохранения данных");
        } else {
            System.out.println("✅ БД доступна");
        }

        TelegramBot bot = new TelegramBot(botToken);
        UrlResolver urlResolver = new UrlResolver();
        TelegramUserWhitelist whitelist = TelegramUserWhitelist.fromCommaSeparatedIds(null);

        VideoStatsBot videoStatsBot = new VideoStatsBot(bot, urlResolver, whitelist);
        videoStatsBot.start();

        System.out.println("✅ Бот запущен и слушает сообщения...");
        System.out.println("Нажмите Ctrl+C для остановки");
    }
}