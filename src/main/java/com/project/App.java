package com.project;

import com.project.bot.UrlResolver;
import io.github.cdimascio.dotenv.Dotenv;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Точка входа в приложение.
// Инициализация компонентов и запуск бота

public class App {
    private static final Dotenv dotenv = Dotenv.load();
    private static final UrlResolver urlResolver = new UrlResolver();
    private static final String CALLBACK_HELLO = "hello_btn";
    private static final String CALLBACK_ADD_LINK = "add_link_btn";
    private static final String PROMPT_SEND_URL = "Отправьте URL";
    /** Чаты, в которых после нажатия «Добавить ссылку» ждём следующее сообщение с URL. */
    private static final Set<Long> chatsAwaitingUrl = ConcurrentHashMap.newKeySet();

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
                    chatsAwaitingUrl.remove(chatId);

                    InlineKeyboardButton helloBtn = new InlineKeyboardButton("Нажми меня").callbackData(CALLBACK_HELLO);
                    InlineKeyboardButton addLinkBtn = new InlineKeyboardButton("Добавить ссылку").callbackData(CALLBACK_ADD_LINK);
                    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(helloBtn, addLinkBtn);

                    bot.execute(new SendMessage(chatId, "Привет! Я тестовый бот. Нажми кнопку ниже:")
                            .replyMarkup(keyboard));
                }

                // 1 URL после нажатия «Добавить ссылку»
                else if (update.message() != null
                        && update.message().text() != null
                        && !update.message().text().startsWith("/")
                        && chatsAwaitingUrl.contains(update.message().chat().id())) {
                    long chatId = update.message().chat().id();
                    processSubmittedUrl(bot, chatId, update.message().text().trim());
                }

                // 2. Обработка нажатия на кнопку
                else if (update.callbackQuery() != null) {
                    String data = update.callbackQuery().data();
                    long chatId = update.callbackQuery().message().chat().id();

                    if (CALLBACK_HELLO.equals(data)) {
                        bot.execute(new SendMessage(chatId, "Hello World!"));
                        bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()));
                    } else if (CALLBACK_ADD_LINK.equals(data)) {
                        chatsAwaitingUrl.add(chatId);
                        bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()));
                        bot.execute(new SendMessage(chatId, PROMPT_SEND_URL));
                    }
                }
            }
            
            // Подтверждаем, что все обновления обработаны
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        // Держим поток живым (для локального запуска)
        try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
    }

    private static void processSubmittedUrl(TelegramBot bot, long chatId, String rawUrl) {
        chatsAwaitingUrl.remove(chatId);

        if (!urlResolver.isValidUrl(rawUrl)) {
            bot.execute(new SendMessage(chatId, "Некорректная ссылка. Проверь формат URL."));
            return;
        }

        UrlResolver.Platform platform = urlResolver.resolvePlatform(rawUrl);
        if (platform == UrlResolver.Platform.UNKNOWN) {
            bot.execute(new SendMessage(chatId, "Платформа не поддерживается. Доступны YouTube и VK."));
            return;
        }

        bot.execute(new SendMessage(chatId, "Ссылка принята. Платформа: " + platform));
    }
}
