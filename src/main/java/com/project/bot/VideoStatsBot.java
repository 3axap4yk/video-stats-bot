package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.project.service.StatisticsService;

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

// Главный обработчик Telegram-диалога: маршрутизация апдейтов и управление сценарием добавления ссылок.

public class VideoStatsBot {
    private final TelegramBot bot;
    private final TelegramUserWhitelist userWhitelist;
    private final AddLinks addLinks;
    private final RefreshStatsLinks updateLinks;
    private final ListLinks listLinks;

    public VideoStatsBot(
            TelegramBot bot,
            UrlResolver urlResolver,
            StatisticsService statisticsService,
            TelegramUserWhitelist userWhitelist) {
        this.bot = bot;
        this.userWhitelist = userWhitelist;
        this.addLinks = new AddLinks(bot, urlResolver, statisticsService, this::sendStartDialog);
        this.updateLinks = new RefreshStatsLinks(bot);
        this.listLinks = new ListLinks(bot);
    }

    public void start() {
        bot.setUpdatesListener(this::processUpdates);
    }

    // Единая точка обработки входящих апдейтов Telegram.
    private int processUpdates(java.util.List<Update> updates) {
        for (Update update : updates) {
            Long userId = extractTelegramUserId(update);
            if (!userWhitelist.allows(userId)) {
                handleDenied(update, userId);
                continue;
            }

            // /start всегда возвращает пользователя в корневое меню и сбрасывает режим ожидания URL.
            if (update.message() != null && "/start".equals(update.message().text())) {
                long chatId = update.message().chat().id();
                addLinks.resetChat(chatId);
                sendStartDialog(chatId);
            } else if (update.message() != null
                    && update.message().text() != null
                    && !update.message().text().startsWith("/")
                    && addLinks.isAwaitingUrl(update.message().chat().id())) {
                long chatId = update.message().chat().id();
                // В этом состоянии любое текстовое сообщение трактуем как кандидат на URL.
                addLinks.onSubmittedUrl(chatId, update.message().text().trim());
            } else if (update.callbackQuery() != null) {
                String data = update.callbackQuery().data();
                long chatId = update.callbackQuery().message().chat().id();
                String callbackQueryId = update.callbackQuery().id();

                if (ADD_LINK.equals(data)) {
                    addLinks.onAddLinkClick(chatId, callbackQueryId);
                } else if (LINKS_LIST.equals(data)) {
                    listLinks.onClick(chatId, callbackQueryId);
                } else if (REFRESH_STATS.equals(data)) {
                    updateLinks.onClick(chatId, callbackQueryId);
                } else if (CANCEL.equals(data)) {
                    addLinks.onCancel(chatId, callbackQueryId);
                } else if (BACK.equals(data)) {
                    addLinks.onBack(chatId, callbackQueryId);
                }
            }
        }

        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private static Long extractTelegramUserId(Update update) {
        if (update.message() != null && update.message().from() != null) {
            return update.message().from().id();
        }
        if (update.callbackQuery() != null && update.callbackQuery().from() != null) {
            return update.callbackQuery().from().id();
        }
        return null;
    }

    private void handleDenied(Update update, Long userId) {
        if (update.callbackQuery() != null) {
            bot.execute(new AnswerCallbackQuery(update.callbackQuery().id()));
        }
        long chatId = resolveChatId(update);
        if (chatId != 0L) {
            bot.execute(new SendMessage(chatId, ACCESS_DENIED));
        } else {
            System.err.println("Доступ запрещён (user id=" + userId + "), chat id определить не удалось.");
        }
    }

    private static long resolveChatId(Update update) {
        if (update.message() != null) {
            return update.message().chat().id();
        }
        if (update.callbackQuery() != null && update.callbackQuery().message() != null) {
            return update.callbackQuery().message().chat().id();
        }
        return 0L;
    }

    // Отправляет стартовый экран с основными действиями пользователя.
    private void sendStartDialog(long chatId) {
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(BTN_ADD_LINK).callbackData(ADD_LINK);
        InlineKeyboardButton linksListBtn = new InlineKeyboardButton(BTN_LINKS_LIST).callbackData(LINKS_LIST);
        InlineKeyboardButton refreshStatsBtn = new InlineKeyboardButton(BTN_REFRESH_STATS).callbackData(REFRESH_STATS);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(addLinkBtn, linksListBtn, refreshStatsBtn);

        bot.execute(new SendMessage(chatId, GREETING).replyMarkup(keyboard));
    }
}
