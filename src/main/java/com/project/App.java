package com.project;

import io.github.cdimascio.dotenv.Dotenv;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.List;

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
        System.out.println("Бот запущен. Жду команды...");

        // Слушаем обновления от Telegram
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {

                // 1. Обработка команды /start
                if (update.message() != null && "/start".equals(update.message().text())) {
                    long chatId = update.message().chat().id();

                    // Создаём инлайн-кнопку
                    InlineKeyboardButton btn = new InlineKeyboardButton("Нажми меня");
                    btn.callbackData("hello_btn"); // Данные, которые придут при нажатии

                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(btn);

                    // Отправляем сообщение с кнопкой
                    bot.execute(new SendMessage(chatId, "Привет! Я тестовый бот. Нажми кнопку ниже:")
                            .replyMarkup(keyboard));
                }

                // 2. Обработка нажатия на кнопку
                else if (update.callbackQuery() != null) {
                    String data = update.callbackQuery().data();
                    long chatId = update.callbackQuery().message().chat().id();

                    if ("hello_btn".equals(data)) {
                        bot.execute(new SendMessage(chatId, "Hello World!"));
                        // Убираем "часики" загрузки с кнопки
                        bot.execute(new com.pengrad.telegrambot.request.AnswerCallbackQuery(update.callbackQuery().id()));
                    }
                }
            }
            // Подтверждаем, что все обновления обработаны
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        // Держим поток живым (для локального запуска)
        try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
    }
}
