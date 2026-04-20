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
    private static final String CALLBACK_ADD_LINK = "add_link_btn";
    private static final String CALLBACK_LINKS_LIST = "links_list_btn";
    private static final String CALLBACK_REFRESH_STATS = "refresh_stats_btn";
    private static final String CALLBACK_CANCEL = "cancel_btn";
    private static final String CALLBACK_BACK = "back_btn";
    private static final String PROMPT_SEND_URL = "Отправьте URL";
    private static final String DEAD_LINK_MESSAGE = "Отправленная Вами ссылка никуда не ведет. Проверьте и попробуйте добавить снова";
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
                    sendStartDialog(bot, chatId);
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

                    if (CALLBACK_ADD_LINK.equals(data)) {
                        handleAddLinkClick(bot, chatId, update.callbackQuery().id());
                    } else if (CALLBACK_LINKS_LIST.equals(data)) {
                        handleLinksListClick(bot, chatId, update.callbackQuery().id());
                    } else if (CALLBACK_REFRESH_STATS.equals(data)) {
                        handleRefreshStatsClick(bot, chatId, update.callbackQuery().id());
                    } else if (CALLBACK_CANCEL.equals(data)) {
                        chatsAwaitingUrl.remove(chatId);
                        bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()));
                        bot.execute(new SendMessage(chatId, "Добавление ссылки отменено."));
                        sendStartDialog(bot, chatId);
                    } else if (CALLBACK_BACK.equals(data)) {
                        chatsAwaitingUrl.remove(chatId);
                        bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()));
                        sendStartDialog(bot, chatId);
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
        if (!urlResolver.isValidUrl(rawUrl)) {
            chatsAwaitingUrl.remove(chatId);
            bot.execute(new SendMessage(chatId, "Некорректная ссылка. Проверь формат URL."));
            return;
        }

        UrlResolver.Platform platform = urlResolver.resolvePlatform(rawUrl);
        if (platform == UrlResolver.Platform.UNKNOWN) {
            chatsAwaitingUrl.remove(chatId);
            bot.execute(new SendMessage(chatId, "Платформа не поддерживается. Доступны YouTube и VK."));
            return;
        }

        if (!urlResolver.pointsToExistingVideo(rawUrl)) {
            bot.execute(new SendMessage(chatId, DEAD_LINK_MESSAGE));
            return;
        }

        chatsAwaitingUrl.remove(chatId);
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton("Добавить ссылку").callbackData(CALLBACK_ADD_LINK);
        InlineKeyboardButton backBtn = new InlineKeyboardButton("Вернуться").callbackData(CALLBACK_BACK);
        InlineKeyboardMarkup successKeyboard = new InlineKeyboardMarkup(addLinkBtn, backBtn);
        bot.execute(new SendMessage(chatId, "Ссылка принята. Платформа: " + platform).replyMarkup(successKeyboard));
    }

    private static void handleAddLinkClick(TelegramBot bot, long chatId, String callbackQueryId) {
        chatsAwaitingUrl.add(chatId);
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton("Отмена").callbackData(CALLBACK_CANCEL);
        InlineKeyboardMarkup cancelKeyboard = new InlineKeyboardMarkup(cancelBtn);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, PROMPT_SEND_URL).replyMarkup(cancelKeyboard));
    }

    private static void handleLinksListClick(TelegramBot bot, long chatId, String callbackQueryId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        // TODO: добавить логику показа списка сохранённых ссылок.
        bot.execute(new SendMessage(chatId, "Список ссылок пока недоступен."));
    }

    private static void handleRefreshStatsClick(TelegramBot bot, long chatId, String callbackQueryId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        // TODO: добавить логику обновления статистики по сохранённым ссылкам.
        bot.execute(new SendMessage(chatId, "Обновление статистики пока недоступно."));
    }

    private static void sendStartDialog(TelegramBot bot, long chatId) {
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(" Добавить ссылку").callbackData(CALLBACK_ADD_LINK);
        InlineKeyboardButton linksListBtn = new InlineKeyboardButton("Список ссылок").callbackData(CALLBACK_LINKS_LIST);
        InlineKeyboardButton refreshStatsBtn = new InlineKeyboardButton("Обновить статистику").callbackData(CALLBACK_REFRESH_STATS);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(addLinkBtn, linksListBtn, refreshStatsBtn);

        bot.execute(new SendMessage(chatId, "Приветствую!")
                .replyMarkup(keyboard));
    }
}
