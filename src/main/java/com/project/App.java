package com.project;

import com.pengrad.telegrambot.TelegramBot;
import com.project.bot.VideoStatsBot;
import com.project.bot.UrlResolver;
import io.github.cdimascio.dotenv.Dotenv;

// Точка входа в приложение.
// Инициализация компонентов и запуск бота

public class App {
    private static final Dotenv dotenv = Dotenv.load();

    public static String getBotToken() {
        return dotenv.get("BOT_TOKEN");
    }

    public static void main(String[] args) {
        // Читаем токен из .env
        String botToken = getBotToken();
        if (botToken == null || botToken.isEmpty()) {
            System.err.println("Ошибка: не задана переменная BOT_TOKEN");
            return;
        }

        TelegramBot bot = new TelegramBot(botToken);
        VideoStatsBot videoStatsBot = new VideoStatsBot(bot, new UrlResolver());
        System.out.println("Бот запущен. Жду команды...");

        // Делегируем обработку обновлений специализированному классу бота.
        videoStatsBot.start();

        // Держим поток живым (для локального запуска)
        try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
    }
}
