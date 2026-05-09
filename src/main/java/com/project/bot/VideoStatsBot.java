package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.project.utils.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VideoStatsBot {
    // Основной экземпляр Telegram бота
    private final TelegramBot bot;
    // Белый список пользователей
    private final TelegramUserWhitelist userWhitelist;
    // Обработчик добавления ссылок
    private final AddLinks addLinks;
    // Обработчик обновления статистики
    private final RefreshStatsLinks refreshStatsLinks;
    // Обработчик списка ссылок
    private final ListLinks listLinks;
    // Обработчик статистики
    private final StatsHandler statsHandler;
    // Пул потоков для асинхронных задач
    private final ExecutorService executorService;
    // Множество ID чатов, которые уже получили приветствие
    private final Set<Long> greetedChats = ConcurrentHashMap.newKeySet();

    // Конструктор бота
    public VideoStatsBot(TelegramBot bot, UrlResolver urlResolver, TelegramUserWhitelist userWhitelist) {
        this.bot = bot;
        this.userWhitelist = userWhitelist;
        this.addLinks = new AddLinks(bot, urlResolver, this::sendStartDialog);
        this.refreshStatsLinks = new RefreshStatsLinks(bot);
        this.listLinks = new ListLinks(bot);
        this.statsHandler = new StatsHandler();
        this.executorService = Executors.newFixedThreadPool(5);
    }

    // Запуск бота с long polling режимом
    public void start() {
        Logger.info("Удаляем webhook и запускаем long polling...");
        BaseResponse response = bot.execute(new DeleteWebhook());
        if (!response.isOk()) {
            Logger.warn("Ошибка удаления webhook: " + response.description());
        }

        // Установка обработчика обновлений
        bot.setUpdatesListener(updates -> {
            processUpdates(updates);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        Logger.info("Бот запущен и слушает сообщения...");
        Logger.info("Нажмите Ctrl+C для остановки");

        // Хук для корректного завершения работы
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

    // Обработка списка полученных обновлений
    private void processUpdates(List<Update> updates) {
        Logger.info("Получено обновлений: " + updates.size() + " | " + Thread.currentThread().getName());

        for (Update update : updates) {
            Long userId = extractTelegramUserId(update);

            if (userId == null) {
                Logger.warn("Пропуск обновления без user id");
                continue;
            }

            // Проверка прав доступа пользователя
            if (!userWhitelist.allows(userId)) {
                Logger.warn("Доступ запрещён для user: " + userId + " — сообщение игнорируется");
                long deniedChatId = resolveChatId(update);
                if (deniedChatId != -1L) {
                    bot.execute(new SendMessage(deniedChatId, BotMessages.ACCESS_DENIED));
                }
                continue;
            }

            long chatId = resolveChatId(update);
            if (chatId == -1L) continue;

            // Маршрутизация: сообщение или callback-запрос
            if (update.message() != null) {
                handleMessage(update, chatId);
            } else if (update.callbackQuery() != null) {
                handleCallbackQuery(update, chatId);
            }
        }
    }

    // Обработка текстовых сообщений
    private void handleMessage(Update update, long chatId) {
        Message message = update.message();
        String text = message.text();

        if (text == null) return;

        if (text.equals("/start")) {
            Logger.info("Команда /start от чата: " + chatId);
            addLinks.resetChat(chatId);
            if (greetedChats.add(chatId)) {
                bot.execute(new SendMessage(chatId, BotMessages.WELCOME).parseMode(ParseMode.HTML));
            }
            sendStartDialog(chatId);
        } else if (text.startsWith("/")) {
            return;
        } else if (addLinks.isAwaitingUrl(chatId)) {
            // Обработка введённой URL-ссылки
            Logger.info("Получена ссылка от чата: " + chatId + " -> " + text);
            addLinks.onSubmittedUrl(chatId, text.trim());
        }
    }

    // Обработка callback-запросов от инлайн-кнопок
    private void handleCallbackQuery(Update update, long chatId) {
        CallbackQuery callbackQuery = update.callbackQuery();
        String data = callbackQuery.data();
        String callbackQueryId = callbackQuery.id();
        Integer messageId = callbackQuery.message() != null ? callbackQuery.message().messageId() : null;

        Logger.info("Callback получен: data=" + data + ", chatId=" + chatId);

        // Обработка пагинации списка ссылок
        if (data.startsWith(ListLinks.PAGE_CALLBACK_PREFIX)) {
            int page = Integer.parseInt(data.substring(ListLinks.PAGE_CALLBACK_PREFIX.length()));
            int msgId = messageId != null ? messageId : -1;
            listLinks.onPageChange(chatId, msgId, page, callbackQueryId);
            return;
        }
        // NOOP - пустая операция для кнопок без действия
        if (data.equals(BotCallbacks.LIST_PAGE_NOOP)) {
            bot.execute(new AnswerCallbackQuery(callbackQueryId));
            return;
        }

        // Обработка основных команд по callback data
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
            case BotCallbacks.STATS:
                Logger.info("Обработка STATS");
                showStats(chatId, callbackQueryId);
                break;
            default:
                Logger.warn("Неизвестный callback: " + data);
                break;
        }
    }

    // Отображение статистики бота
    private void showStats(long chatId, String callbackQueryId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        String stats = statsHandler.getStats();
        InlineKeyboardButton backButton = new InlineKeyboardButton(BotMessages.BTN_BACK)
                .callbackData(BotCallbacks.BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);
        bot.execute(new SendMessage(chatId, stats)
                .parseMode(ParseMode.HTML)
                .replyMarkup(keyboard));
    }

    // Отправка стартового меню с кнопками
    private void sendStartDialog(long chatId) {
        Logger.info("Отправляем стартовое меню в чат: " + chatId);

        // Создание кнопок главного меню
        InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(BotMessages.BTN_ADD_LINK)
                .callbackData(BotCallbacks.ADD_LINK);
        InlineKeyboardButton linksListBtn = new InlineKeyboardButton(BotMessages.BTN_LINKS_LIST)
                .callbackData(BotCallbacks.LINKS_LIST);
        InlineKeyboardButton refreshStatsBtn = new InlineKeyboardButton(BotMessages.BTN_REFRESH_STATS)
                .callbackData(BotCallbacks.REFRESH_STATS);
        InlineKeyboardButton statsBtn = new InlineKeyboardButton(BotMessages.BTN_STATS)
                .callbackData(BotCallbacks.STATS);

        // Расположение кнопок в сетке 2x2
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton[][]{
                        {addLinkBtn},          // Одна кнопка на ряд
                        {linksListBtn},
                        {refreshStatsBtn},
                        {statsBtn}
                }
        );

        bot.execute(new SendMessage(chatId, BotMessages.GREETING).replyMarkup(keyboard));
    }

    // Извлечение Telegram user ID из обновления
    private Long extractTelegramUserId(Update update) {
        if (update.message() != null && update.message().from() != null) {
            return update.message().from().id();
        }
        if (update.callbackQuery() != null && update.callbackQuery().from() != null) {
            return update.callbackQuery().from().id();
        }
        return null;
    }

    // Определение chat ID из обновления
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