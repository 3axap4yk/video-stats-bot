package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;

import static com.project.bot.BotCallbacks.ADD_LINK;
import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotCallbacks.CANCEL;
import static com.project.bot.BotCallbacks.LINKS_LIST;
import static com.project.bot.BotCallbacks.REFRESH_STATS;
import static com.project.bot.BotMessages.ACCESS_DENIED;
import static com.project.bot.BotMessages.BTN_ADD_LINK;
import static com.project.bot.BotMessages.BTN_LINKS_LIST;
import static com.project.bot.BotMessages.BTN_REFRESH_STATS;
import static com.project.bot.BotMessages.GREETING;

// Главный класс бота: точка входа для обработки всех входящих обновлений Telegram
public class VideoStatsBot {

    private final TelegramBot bot;
    private final TelegramUserWhitelist userWhitelist;  // Список разрешённых пользователей
    private final AddLinks addLinks;                    // Обработчик добавления ссылок
    private final RefreshStatsLinks updateLinks;        // Обработчик обновления статистики
    private final ListLinks listLinks;                  // Обработчик отображения списка видео

    public VideoStatsBot(
            TelegramBot bot,
            UrlResolver urlResolver,
            TelegramUserWhitelist userWhitelist) {
        this.bot = bot;
        this.userWhitelist = userWhitelist;
        // Передаём sendStartDialog как callback, чтобы обработчики могли вернуть пользователя в главное меню
        this.addLinks = new AddLinks(bot, urlResolver, this::sendStartDialog);
        this.updateLinks = new RefreshStatsLinks(bot);
        this.listLinks = new ListLinks(bot);
    }

    // Запускает бота: сбрасывает webhook и переключается в режим long polling
    public void start() {
        System.out.println("🔄 Удаляем webhook и запускаем long polling...");
        // Удаление webhook обязательно — иначе Telegram не будет отправлять обновления через getUpdates
        bot.execute(new com.pengrad.telegrambot.request.DeleteWebhook());
        bot.setUpdatesListener(this::processUpdates);
    }

    // Основной цикл обработки пакета обновлений от Telegram
    private int processUpdates(java.util.List<Update> updates) {
        System.out.println("📨 Получено обновлений: " + updates.size());

        for (Update update : updates) {
            Long userId = extractTelegramUserId(update);
            System.out.println("User ID: " + userId);

            // Проверяем whitelist до любой обработки — неизвестные пользователи не получают доступ
            if (!userWhitelist.allows(userId)) {
                System.out.println("❌ Доступ запрещён для user: " + userId);
                handleDenied(update, userId);
                continue;
            }

            if (update.message() != null && "/start".equals(update.message().text())) {
                long chatId = update.message().chat().id();
                System.out.println("📌 Команда /start от чата: " + chatId);
                // Сбрасываем состояние диалога — пользователь мог прервать ввод ссылки
                addLinks.resetChat(chatId);
                sendStartDialog(chatId);

            } else if (update.message() != null
                    && update.message().text() != null
                    && !update.message().text().startsWith("/")
                    && addLinks.isAwaitingUrl(update.message().chat().id())) {
                // Текстовое сообщение не является командой и бот ожидает ввод URL от этого чата
                long chatId = update.message().chat().id();
                System.out.println("🔗 Получена ссылка от чата: " + chatId + " -> " + update.message().text());
                addLinks.onSubmittedUrl(chatId, update.message().text().trim());

            } else if (update.callbackQuery() != null) {
                // Пользователь нажал inline-кнопку
                String data = update.callbackQuery().data();
                long chatId = update.callbackQuery().message().chat().id();
                String callbackQueryId = update.callbackQuery().id();
                int messageId = update.callbackQuery().message().messageId();
                System.out.println("🔘 Callback получен: data=" + data + ", chatId=" + chatId);

                // Маршрутизация по значению callback_data кнопки
                if (ADD_LINK.equals(data)) {
                    System.out.println("➡ Обработка ADD_LINK");
                    addLinks.onAddLinkClick(chatId, callbackQueryId);

                } else if (LINKS_LIST.equals(data)) {
                    System.out.println("📋 Обработка LINKS_LIST");
                    listLinks.onClick(chatId, callbackQueryId);

                } else if (REFRESH_STATS.equals(data)) {
                    System.out.println("🔄 Обработка REFRESH_STATS");
                    updateLinks.onClick(chatId, callbackQueryId, messageId);

                } else if (CANCEL.equals(data)) {
                    // Пользователь отменил ввод ссылки — сбрасываем состояние и возвращаем меню
                    System.out.println("❌ Обработка CANCEL");
                    addLinks.onCancel(chatId, callbackQueryId);

                } else if (BACK.equals(data)) {
                    // Возврат в главное меню из любого вложенного экрана
                    System.out.println("🔙 Обработка BACK");
                    addLinks.onBack(chatId, callbackQueryId);

                } else {
                    // Защита от устаревших или неизвестных callback_data (например, после деплоя)
                    System.out.println("⚠️ Неизвестный callback: " + data);
                }
            }
        }

        // Подтверждаем все обновления — Telegram не будет повторно их присылать
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    // Извлекает Telegram user ID из обновления независимо от его типа
    // Возвращает null, если источник не определён (например, служебные сообщения канала)
    private static Long extractTelegramUserId(Update update) {
        if (update.message() != null && update.message().from() != null) {
            return update.message().from().id();
        }
        if (update.callbackQuery() != null && update.callbackQuery().from() != null) {
            return update.callbackQuery().from().id();
        }
        return null;
    }

    // Обрабатывает попытку доступа от неавторизованного пользователя
    private void handleDenied(Update update, Long userId) {
        // Если пришёл callback — обязательно отвечаем, иначе кнопка "залипнет" в состоянии загрузки
        if (update.callbackQuery() != null) {
            bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()));
        }
        long chatId = resolveChatId(update);
        if (chatId != 0L) {
            bot.execute(new SendMessage(chatId, ACCESS_DENIED));
        } else {
            // Такое возможно для обновлений без привязанного чата (inline-запросы, служебные события)
            System.err.println("Доступ запрещён (user id=" + userId + "), chat id определить не удалось.");
        }
    }

    // Определяет chat ID из любого типа обновления
    // Возвращает 0L как сигнальное значение при невозможности определить чат
    private static long resolveChatId(Update update) {
        if (update.message() != null) {
            return update.message().chat().id();
        }
        if (update.callbackQuery() != null && update.callbackQuery().message() != null) {
            return update.callbackQuery().message().chat().id();
        }
        return 0L;
    }

    // Отправляет главное меню с тремя inline-кнопками
    // Используется как при /start, так и при возврате из вложенных экранов через callback
    private void sendStartDialog(long chatId) {
        System.out.println("📤 Отправляем стартовое меню в чат: " + chatId);
        InlineKeyboardButton addLinkBtn     = new InlineKeyboardButton(BTN_ADD_LINK).callbackData(ADD_LINK);
        InlineKeyboardButton linksListBtn   = new InlineKeyboardButton(BTN_LINKS_LIST).callbackData(LINKS_LIST);
        InlineKeyboardButton refreshStatsBtn = new InlineKeyboardButton(BTN_REFRESH_STATS).callbackData(REFRESH_STATS);
        // Все три кнопки в одной строке — горизонтальный ряд
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(addLinkBtn, linksListBtn, refreshStatsBtn);

        bot.execute(new SendMessage(chatId, GREETING).replyMarkup(keyboard));
    }
}