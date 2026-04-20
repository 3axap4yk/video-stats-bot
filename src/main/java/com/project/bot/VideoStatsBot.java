package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.project.bot.BotCallbacks.ADD_LINK;
import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotCallbacks.CANCEL;
import static com.project.bot.BotCallbacks.LINKS_LIST;
import static com.project.bot.BotCallbacks.REFRESH_STATS;
import static com.project.bot.BotMessages.ADD_LINK_CANCELLED;
import static com.project.bot.BotMessages.BTN_ADD_LINK;
import static com.project.bot.BotMessages.BTN_BACK;
import static com.project.bot.BotMessages.BTN_CANCEL;
import static com.project.bot.BotMessages.BTN_LINKS_LIST;
import static com.project.bot.BotMessages.BTN_REFRESH_STATS;
import static com.project.bot.BotMessages.DEAD_LINK;
import static com.project.bot.BotMessages.GREETING;
import static com.project.bot.BotMessages.INVALID_URL;
import static com.project.bot.BotMessages.LINK_ADDED_TEMPLATE;
import static com.project.bot.BotMessages.LINKS_LIST_UNAVAILABLE;
import static com.project.bot.BotMessages.PROMPT_SEND_URL;
import static com.project.bot.BotMessages.REFRESH_STATS_UNAVAILABLE;
import static com.project.bot.BotMessages.UNSUPPORTED_PLATFORM;

// Главный обработчик Telegram-диалога: маршрутизация апдейтов и управление сценарием добавления ссылок.

public class VideoStatsBot {
    private final TelegramBot bot;
    private final UrlResolver urlResolver;
    /** Чаты, в которых после нажатия «Добавить ссылку» ждём следующее текстовое сообщение с URL. */
    private final Set<Long> chatsAwaitingUrl = ConcurrentHashMap.newKeySet();

    public VideoStatsBot(TelegramBot bot, UrlResolver urlResolver) {
        this.bot = bot;
        this.urlResolver = urlResolver;
    }

    public void start() {
        bot.setUpdatesListener(this::processUpdates);
    }

    // Единая точка обработки входящих апдейтов Telegram.
    private int processUpdates(java.util.List<Update> updates) {
        for (Update update : updates) {
            // /start всегда возвращает пользователя в корневое меню и сбрасывает режим ожидания URL.
            if (update.message() != null && "/start".equals(update.message().text())) {
                long chatId = update.message().chat().id();
                chatsAwaitingUrl.remove(chatId);
                sendStartDialog(chatId);
            } else if (update.message() != null
                    && update.message().text() != null
                    && !update.message().text().startsWith("/")
                    && chatsAwaitingUrl.contains(update.message().chat().id())) {
                long chatId = update.message().chat().id();
                // В этом состоянии любое текстовое сообщение трактуем как кандидат на URL.
                processSubmittedUrl(chatId, update.message().text().trim());
            } else if (update.callbackQuery() != null) {
                String data = update.callbackQuery().data();
                long chatId = update.callbackQuery().message().chat().id();
                String callbackQueryId = update.callbackQuery().id();

                if (ADD_LINK.equals(data)) {
                    handleAddLinkClick(chatId, callbackQueryId);
                } else if (LINKS_LIST.equals(data)) {
                    handleLinksListClick(chatId, callbackQueryId);
                } else if (REFRESH_STATS.equals(data)) {
                    handleRefreshStatsClick(chatId, callbackQueryId);
                } else if (CANCEL.equals(data)) {
                    chatsAwaitingUrl.remove(chatId);
                    bot.execute(new AnswerCallbackQuery(callbackQueryId));
                    bot.execute(new SendMessage(chatId, ADD_LINK_CANCELLED));
                    sendStartDialog(chatId);
                } else if (BACK.equals(data)) {
                    chatsAwaitingUrl.remove(chatId);
                    bot.execute(new AnswerCallbackQuery(callbackQueryId));
                    sendStartDialog(chatId);
                }
            }
        }

        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    // Проверка и обработка ссылки, отправленной пользователем в режиме "ожидания URL".
    private void processSubmittedUrl(long chatId, String rawUrl) {
        if (!urlResolver.isValidUrl(rawUrl)) {
            bot.execute(new SendMessage(chatId, INVALID_URL).replyMarkup(buildCancelKeyboard()));
            return;
        }

        UrlResolver.Platform platform = urlResolver.resolvePlatform(rawUrl);
        if (platform == UrlResolver.Platform.UNKNOWN) {
            chatsAwaitingUrl.remove(chatId);
            bot.execute(new SendMessage(chatId, UNSUPPORTED_PLATFORM));
            return;
        }

        if (!urlResolver.pointsToExistingVideo(rawUrl)) {
            bot.execute(new SendMessage(chatId, DEAD_LINK).replyMarkup(buildCancelKeyboard()));
            return;
        }

        chatsAwaitingUrl.remove(chatId);
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(BTN_ADD_LINK).callbackData(ADD_LINK);
        InlineKeyboardButton backBtn = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
        InlineKeyboardMarkup successKeyboard = new InlineKeyboardMarkup(addLinkBtn, backBtn);
        bot.execute(new SendMessage(chatId, LINK_ADDED_TEMPLATE.formatted(platform)).replyMarkup(successKeyboard));
    }

    // Переводит чат в режим ожидания URL и показывает кнопку отмены.
    private void handleAddLinkClick(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.add(chatId);
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        InlineKeyboardMarkup cancelKeyboard = new InlineKeyboardMarkup(cancelBtn);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, PROMPT_SEND_URL).replyMarkup(cancelKeyboard));
    }

    // Заглушка для будущего вывода списка сохраненных ссылок.
    private void handleLinksListClick(long chatId, String callbackQueryId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        // TODO: добавить логику показа списка сохранённых ссылок.
        bot.execute(new SendMessage(chatId, LINKS_LIST_UNAVAILABLE));
    }

    // Заглушка для будущего пересчета статистики по сохраненным ссылкам.
    private void handleRefreshStatsClick(long chatId, String callbackQueryId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        // TODO: добавить логику обновления статистики по сохранённым ссылкам.
        bot.execute(new SendMessage(chatId, REFRESH_STATS_UNAVAILABLE));
    }

    // Отправляет стартовый экран с основными действиями пользователя.
    private void sendStartDialog(long chatId) {
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(BTN_ADD_LINK).callbackData(ADD_LINK);
        InlineKeyboardButton linksListBtn = new InlineKeyboardButton(BTN_LINKS_LIST).callbackData(LINKS_LIST);
        InlineKeyboardButton refreshStatsBtn = new InlineKeyboardButton(BTN_REFRESH_STATS).callbackData(REFRESH_STATS);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(addLinkBtn, linksListBtn, refreshStatsBtn);

        bot.execute(new SendMessage(chatId, GREETING).replyMarkup(keyboard));
    }

    // Унифицированная клавиатура отмены для сценария добавления ссылки.
    private InlineKeyboardMarkup buildCancelKeyboard() {
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        return new InlineKeyboardMarkup(cancelBtn);
    }
}
