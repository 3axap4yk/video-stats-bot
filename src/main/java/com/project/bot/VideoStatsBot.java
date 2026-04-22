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
        System.out.println("🔄 Удаляем webhook и запускаем long polling...");
        bot.execute(new com.pengrad.telegrambot.request.DeleteWebhook());
        bot.setUpdatesListener(this::processUpdates);
    }

    private int processUpdates(java.util.List<Update> updates) {
        System.out.println("📨 Получено обновлений: " + updates.size());

        for (Update update : updates) {
            System.out.println("Обработка update: " + update);

            Long userId = extractTelegramUserId(update);
            System.out.println("User ID: " + userId);

            if (!userWhitelist.allows(userId)) {
                System.out.println("❌ Доступ запрещён для user: " + userId);
                handleDenied(update, userId);
                continue;
            }

            if (update.message() != null && "/start".equals(update.message().text())) {
                long chatId = update.message().chat().id();
                System.out.println("📌 Команда /start от чата: " + chatId);
                addLinks.resetChat(chatId);
                sendStartDialog(chatId);
            } else if (update.message() != null
                    && update.message().text() != null
                    && !update.message().text().startsWith("/")
                    && addLinks.isAwaitingUrl(update.message().chat().id())) {
                long chatId = update.message().chat().id();
                System.out.println("🔗 Получена ссылка от чата: " + chatId + " -> " + update.message().text());
                addLinks.onSubmittedUrl(chatId, update.message().text().trim());
            } else if (update.callbackQuery() != null) {
                String data = update.callbackQuery().data();
                long chatId = update.callbackQuery().message().chat().id();
                String callbackQueryId = update.callbackQuery().id();
                System.out.println("🔘 Callback получен: data=" + data + ", chatId=" + chatId);

                if (ADD_LINK.equals(data)) {
                    System.out.println("➕ Обработка ADD_LINK");
                    addLinks.onAddLinkClick(chatId, callbackQueryId);
                } else if (LINKS_LIST.equals(data)) {
                    System.out.println("📋 Обработка LINKS_LIST");
                    listLinks.onClick(chatId, callbackQueryId);
                } else if (REFRESH_STATS.equals(data)) {
                    System.out.println("🔄 Обработка REFRESH_STATS");
                    updateLinks.onClick(chatId, callbackQueryId);
                } else if (CANCEL.equals(data)) {
                    System.out.println("❌ Обработка CANCEL");
                    addLinks.onCancel(chatId, callbackQueryId);
                } else if (BACK.equals(data)) {
                    System.out.println("🔙 Обработка BACK");
                    addLinks.onBack(chatId, callbackQueryId);
                } else {
                    System.out.println("⚠️ Неизвестный callback: " + data);
                }
            } else {
                System.out.println("ℹ️ Другое обновление (не сообщение и не callback)");
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

    private void sendStartDialog(long chatId) {
        System.out.println("📤 Отправляем стартовое меню в чат: " + chatId);
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(BTN_ADD_LINK).callbackData(ADD_LINK);
        InlineKeyboardButton linksListBtn = new InlineKeyboardButton(BTN_LINKS_LIST).callbackData(LINKS_LIST);
        InlineKeyboardButton refreshStatsBtn = new InlineKeyboardButton(BTN_REFRESH_STATS).callbackData(REFRESH_STATS);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(addLinkBtn, linksListBtn, refreshStatsBtn);

        bot.execute(new SendMessage(chatId, GREETING).replyMarkup(keyboard));
    }
}