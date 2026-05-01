package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.project.utils.Logger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VideoStatsBot {
    private final TelegramBot bot;
    private final TelegramUserWhitelist userWhitelist;
    private final AddLinks addLinks;
    private final RefreshStatsLinks refreshStatsLinks;
    private final ListLinks listLinks;
    private final ExecutorService executorService;

    public VideoStatsBot(TelegramBot bot, UrlResolver urlResolver, TelegramUserWhitelist userWhitelist) {
        this.bot = bot;
        this.userWhitelist = userWhitelist;
        this.addLinks = new AddLinks(bot, urlResolver, this::sendStartDialog);
        this.refreshStatsLinks = new RefreshStatsLinks(bot);
        this.listLinks = new ListLinks(bot);
        // Пул из 5 потоков для фоновых задач
        this.executorService = Executors.newFixedThreadPool(5);
    }

    public void start() {
        Logger.info("Удаляем webhook и запускаем long polling...");
        BaseResponse response = bot.execute(new DeleteWebhook());
        if (!response.isOk()) {
            Logger.warn("Ошибка удаления webhook: " + response.description());
        }

        bot.setUpdatesListener(updates -> {
            processUpdates(updates);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        Logger.info("Бот запущен и слушает сообщения...");
        Logger.info("Нажмите Ctrl+C для остановки");

        // Добавляем хук для корректного завершения
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Завершение работы бота...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            Logger.info("Бот остановлен");
        }));
    }

    private void processUpdates(List<Update> updates) {
        Logger.info("Получено обновлений: " + updates.size() + " | " + Thread.currentThread().getName());

        for (Update update : updates) {
            long chatId = resolveChatId(update);
            Long userId = extractTelegramUserId(update);

            if (userId == null) {
                Logger.warn("Доступ запрещён (user id=null), chat id определить не удалось.");
                continue;
            }

            if (!userWhitelist.allows(userId)) {
                Logger.warn("Доступ запрещён для user: " + userId);
                sendAccessDenied(update, chatId);
                continue;
            }

            if (update.message() != null) {
                handleMessage(update, chatId);
            } else if (update.callbackQuery() != null) {
                handleCallbackQuery(update, chatId);
            }
        }
    }

    private void sendAccessDenied(Update update, long chatId) {
        SendMessage request = new SendMessage(chatId, BotMessages.ACCESS_DENIED);
        bot.execute(request);

        if (update.callbackQuery() != null) {
            AnswerCallbackQuery answer = new AnswerCallbackQuery(update.callbackQuery().id());
            bot.execute(answer);
        }
    }

    private void handleMessage(Update update, long chatId) {
        Message message = update.message();
        String text = message.text();

        if (text == null) return;

        if (text.equals("/start")) {
            Logger.info("Команда /start от чата: " + chatId);
            sendStartDialog(chatId);
        } else if (text.startsWith("/")) {
            // Игнорируем другие команды
            return;
        } else if (addLinks.isAwaitingUrl(chatId)) {
            Logger.info("Получена ссылка от чата: " + chatId + " -> " + text);
            addLinks.onSubmittedUrl(chatId, text.trim());
        } else {
            Logger.info("Получена ссылка от чата: " + chatId + " -> " + text);
            addLinks.onSubmittedUrl(chatId, text.trim());
        }
    }

    private void handleCallbackQuery(Update update, long chatId) {
        CallbackQuery callbackQuery = update.callbackQuery();
        String data = callbackQuery.data();
        String callbackQueryId = callbackQuery.id();
        Integer messageId = callbackQuery.message() != null ? callbackQuery.message().messageId() : null;

        Logger.info("Callback получен: data=" + data + ", chatId=" + chatId);

        switch (data) {
            case BotCallbacks.ADD_LINK:
                Logger.info("Обработка ADD_LINK");
                addLinks.onAddLinkClick(chatId, callbackQueryId);
                break;
            case BotCallbacks.LINKS_LIST:
                Logger.info("Обработка LINKS_LIST");
                listLinks.onClick(chatId, callbackQueryId);
                break;
            case BotCallbacks.REFRESH_STATS:
                Logger.info("Обработка REFRESH_STATS");
                // Используем пул потоков для фоновой задачи
                int finalMessageId = messageId != null ? messageId : -1;
                executorService.submit(() -> refreshStatsLinks.onClick(chatId, callbackQueryId, finalMessageId));
                break;
            case BotCallbacks.CANCEL:
                Logger.info("Обработка CANCEL");
                addLinks.onCancel(chatId, callbackQueryId);
                break;
            case BotCallbacks.BACK:
                Logger.info("Обработка BACK");
                addLinks.onBack(chatId, callbackQueryId);
                break;
            default:
                Logger.warn("Неизвестный callback: " + data);
                break;
        }
    }

    private void resetChat(long chatId) {
        // Сброс состояния ожидания URL для чата
    }

    private void sendStartDialog(long chatId) {
        Logger.info("Отправляем стартовое меню в чат: " + chatId);

        // Исправлено: правильный способ создания кнопок
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(BotMessages.BTN_ADD_LINK)
                .callbackData(BotCallbacks.ADD_LINK);
        InlineKeyboardButton linksListBtn = new InlineKeyboardButton(BotMessages.BTN_LINKS_LIST)
                .callbackData(BotCallbacks.LINKS_LIST);
        InlineKeyboardButton refreshStatsBtn = new InlineKeyboardButton(BotMessages.BTN_REFRESH_STATS)
                .callbackData(BotCallbacks.REFRESH_STATS);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[][]{
                        {addLinkBtn, linksListBtn},
                        {refreshStatsBtn}
                }
        );

        SendMessage request = new SendMessage(chatId, BotMessages.GREETING)
                .replyMarkup(keyboard);
        bot.execute(request);
    }

    private Long extractTelegramUserId(Update update) {
        if (update.message() != null && update.message().from() != null) {
            return update.message().from().id();
        }
        if (update.callbackQuery() != null && update.callbackQuery().from() != null) {
            return update.callbackQuery().from().id();
        }
        return null;
    }

    private long resolveChatId(Update update) {
        if (update.message() != null) {
            return update.message().chat().id();
        }
        if (update.callbackQuery() != null) {
            return update.callbackQuery().message().chat().id();
        }
        return -1L;
    }
}