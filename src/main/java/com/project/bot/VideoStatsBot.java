package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.project.utils.Logger;

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

/**
 * Главный класс бота: точка входа для обработки всех входящих обновлений Telegram
 */
public class VideoStatsBot {

    private final TelegramBot bot;
    private final TelegramUserWhitelist userWhitelist;
    private final AddLinks addLinks;
    private final RefreshStatsLinks updateLinks;
    private final ListLinks listLinks;

    public VideoStatsBot(
            TelegramBot bot,
            UrlResolver urlResolver,
            TelegramUserWhitelist userWhitelist) {
        this.bot = bot;
        this.userWhitelist = userWhitelist;
        this.addLinks = new AddLinks(bot, urlResolver, this::sendStartDialog);
        this.updateLinks = new RefreshStatsLinks(bot);
        this.listLinks = new ListLinks(bot);
    }

    /**
     * Запускает бота: сбрасывает webhook и переключается в режим long polling
     */
    public void start() {
        Logger.info("Удаляем webhook и запускаем long polling...");
        bot.execute(new com.pengrad.telegrambot.request.DeleteWebhook());
        bot.setUpdatesListener(this::processUpdates);
    }

    /**
     * Основной цикл обработки пакета обновлений от Telegram
     */
    private int processUpdates(java.util.List<Update> updates) {
        Logger.info("Получено обновлений: " + updates.size());

        for (Update update : updates) {
            Long userId = extractTelegramUserId(update);
            Logger.info("User ID: " + userId);

            if (!userWhitelist.allows(userId)) {
                Logger.warn("Доступ запрещён для user: " + userId);
                handleDenied(update, userId);
                continue;
            }

            if (update.message() != null && "/start".equals(update.message().text())) {
                long chatId = update.message().chat().id();
                Logger.info("Команда /start от чата: " + chatId);
                addLinks.resetChat(chatId);
                sendStartDialog(chatId);

            } else if (update.message() != null
                    && update.message().text() != null
                    && !update.message().text().startsWith("/")
                    && addLinks.isAwaitingUrl(update.message().chat().id())) {
                long chatId = update.message().chat().id();
                Logger.info("Получена ссылка от чата: " + chatId + " -> " + update.message().text());
                addLinks.onSubmittedUrl(chatId, update.message().text().trim());

            } else if (update.callbackQuery() != null) {
                String data = update.callbackQuery().data();
                long chatId = update.callbackQuery().message().chat().id();
                String callbackQueryId = update.callbackQuery().id();
                int messageId = update.callbackQuery().message().messageId();
                Logger.info("Callback получен: data=" + data + ", chatId=" + chatId);

                if (ADD_LINK.equals(data)) {
                    Logger.info("Обработка ADD_LINK");
                    addLinks.onAddLinkClick(chatId, callbackQueryId);

                } else if (LINKS_LIST.equals(data)) {
                    Logger.info("Обработка LINKS_LIST");
                    listLinks.onClick(chatId, callbackQueryId);

                } else if (REFRESH_STATS.equals(data)) {
                    Logger.info("Обработка REFRESH_STATS");
                    updateLinks.onClick(chatId, callbackQueryId, messageId);

                } else if (CANCEL.equals(data)) {
                    Logger.info("Обработка CANCEL");
                    addLinks.onCancel(chatId, callbackQueryId);

                } else if (BACK.equals(data)) {
                    Logger.info("Обработка BACK");
                    addLinks.onBack(chatId, callbackQueryId);

                } else {
                    Logger.warn("Неизвестный callback: " + data);
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
            Logger.error("Доступ запрещён (user id=" + userId + "), chat id определить не удалось.");
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

    private void sendStartDialog(long chatId) {
        Logger.info("Отправляем стартовое меню в чат: " + chatId);
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(BTN_ADD_LINK).callbackData(ADD_LINK);
        InlineKeyboardButton linksListBtn = new InlineKeyboardButton(BTN_LINKS_LIST).callbackData(LINKS_LIST);
        InlineKeyboardButton refreshStatsBtn = new InlineKeyboardButton(BTN_REFRESH_STATS).callbackData(REFRESH_STATS);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(addLinkBtn, linksListBtn, refreshStatsBtn);

        bot.execute(new SendMessage(chatId, GREETING).replyMarkup(keyboard));
    }
}