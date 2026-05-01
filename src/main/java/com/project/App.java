package com.project;

import com.pengrad.telegrambot.TelegramBot;
import com.project.bot.TelegramUserWhitelist;
import com.project.bot.UrlResolver;
import com.project.bot.VideoStatsBot;
import com.project.repository.DbConnection;
import com.project.utils.Logger;
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
        Logger.info("VideoStatsBot starting...");

        String botToken = getBotToken();
        String youtubeKey = getYouTubeApiKey();

        if (botToken == null || botToken.isEmpty() || youtubeKey == null || youtubeKey.isEmpty()) {
            Logger.error("Переменные окружения не найдены или пусты");
            Logger.warn("Проверьте файл .env в корне проекта");
            return;
        }

        Logger.success("Конфигурация загружена успешно");

        // Инициализация БД (создание таблиц, если их нет)
        DbConnection.initDatabase();

        boolean dbAvailable = DbConnection.isDatabaseAvailable();
        if (!dbAvailable) {
            Logger.warn("БД недоступна, бот будет работать без сохранения данных");
        } else {
            Logger.success("БД доступна");
        }

        TelegramBot bot = new TelegramBot(botToken);
        UrlResolver urlResolver = new UrlResolver();
        TelegramUserWhitelist whitelist = TelegramUserWhitelist.fromCommaSeparatedIds(null);

        VideoStatsBot videoStatsBot = new VideoStatsBot(bot, urlResolver, whitelist);
        videoStatsBot.start();

        Logger.success("Бот запущен и слушает сообщения...");
        Logger.info("Нажмите Ctrl+C для остановки");
    }
}