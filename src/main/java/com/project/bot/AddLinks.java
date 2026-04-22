package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.project.model.VideoStats;
import com.project.service.StatisticsService;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import static com.project.bot.BotCallbacks.ADD_LINK;
import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotCallbacks.CANCEL;
import static com.project.bot.BotMessages.ADD_LINK_CANCELLED;
import static com.project.bot.BotMessages.BTN_ADD_LINK;
import static com.project.bot.BotMessages.BTN_BACK;
import static com.project.bot.BotMessages.BTN_CANCEL;
import static com.project.bot.BotMessages.DEAD_LINK;
import static com.project.bot.BotMessages.INVALID_URL;
import static com.project.bot.BotMessages.PROMPT_SEND_URL;
import static com.project.bot.BotMessages.REQUEST_IN_PROGRESS;
import static com.project.bot.BotMessages.UNSUPPORTED_PLATFORM;
import static com.project.bot.BotMessages.VIDEO_STATS_TEMPLATE;
import static com.project.bot.BotMessages.VK_STATS_NOT_SUPPORTED;
import static com.project.bot.BotMessages.YOUTUBE_API_FAILED;

public class AddLinks {
    private final TelegramBot bot;
    private final UrlResolver urlResolver;
    private final StatisticsService statisticsService;
    private final LongConsumer showStartDialog;

    /** Чаты, в которых после нажатия «Добавить ссылку» ждём следующее текстовое сообщение с URL. */
    private final Set<Long> chatsAwaitingUrl = ConcurrentHashMap.newKeySet();

    public AddLinks(
            TelegramBot bot,
            UrlResolver urlResolver,
            StatisticsService statisticsService,
            LongConsumer showStartDialog) {
        this.bot = bot;
        this.urlResolver = urlResolver;
        this.statisticsService = statisticsService;
        this.showStartDialog = showStartDialog;
    }

    public boolean isAwaitingUrl(long chatId) {
        return chatsAwaitingUrl.contains(chatId);
    }

    public void resetChat(long chatId) {
        chatsAwaitingUrl.remove(chatId);
    }

    // Переводит чат в режим ожидания URL и показывает кнопку отмены.
    public void onAddLinkClick(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.add(chatId);
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        InlineKeyboardMarkup cancelKeyboard = new InlineKeyboardMarkup(cancelBtn);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, PROMPT_SEND_URL).replyMarkup(cancelKeyboard));
    }

    public void onCancel(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.remove(chatId);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, ADD_LINK_CANCELLED));
        showStartDialog.accept(chatId);
    }

    public void onBack(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.remove(chatId);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        showStartDialog.accept(chatId);
    }

    // Проверка и обработка ссылки, отправленной пользователем в режиме "ожидания URL".
    public void onSubmittedUrl(long chatId, String rawUrl) {
        if (!urlResolver.isValidUrl(rawUrl)) {
            bot.execute(new SendMessage(chatId, INVALID_URL).replyMarkup(buildCancelKeyboard()));
            return;
        }

        UrlResolver.Platform platform = urlResolver.resolvePlatform(rawUrl);
        if (platform == UrlResolver.Platform.UNKNOWN) {
            bot.execute(new SendMessage(chatId, UNSUPPORTED_PLATFORM).replyMarkup(buildCancelKeyboard()));
            return;
        }

        if (!urlResolver.pointsToExistingVideo(rawUrl)) {
            bot.execute(new SendMessage(chatId, DEAD_LINK).replyMarkup(buildCancelKeyboard()));
            return;
        }

        if (platform == UrlResolver.Platform.VK) {
            bot.execute(new SendMessage(chatId, VK_STATS_NOT_SUPPORTED).replyMarkup(buildCancelKeyboard()));
            return;
        }

        Integer progressMessageId = sendProgressMessage(chatId);
        try {
            Optional<VideoStats> fetched = statisticsService.fetchYoutubeStats(rawUrl);
            if (fetched.isEmpty()) {
                bot.execute(new SendMessage(chatId, YOUTUBE_API_FAILED).replyMarkup(buildCancelKeyboard()));
                return;
            }

            chatsAwaitingUrl.remove(chatId);
            VideoStats stats = fetched.get();
            InlineKeyboardButton addLinkBtn = new InlineKeyboardButton(BTN_ADD_LINK).callbackData(ADD_LINK);
            InlineKeyboardButton backBtn = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
            InlineKeyboardMarkup successKeyboard = new InlineKeyboardMarkup(addLinkBtn, backBtn);
            String text = VIDEO_STATS_TEMPLATE.formatted(stats.getTitle(), stats.getViewCount(), stats.getPlatform());
            bot.execute(new SendMessage(chatId, text).replyMarkup(successKeyboard));
        } finally {
            deleteMessageIfPresent(chatId, progressMessageId);
        }
    }

    /** Отправляет служебное «Выполняю запрос…»; возвращает id сообщения для последующего удаления. */
    private Integer sendProgressMessage(long chatId) {
        SendResponse response = bot.execute(new SendMessage(chatId, REQUEST_IN_PROGRESS));
        if (response.isOk() && response.message() != null) {
            return response.message().messageId();
        }
        return null;
    }

    private void deleteMessageIfPresent(long chatId, Integer messageId) {
        if (messageId == null) {
            return;
        }
        bot.execute(new DeleteMessage(chatId, messageId));
    }

    // Унифицированная клавиатура отмены для сценария добавления ссылки.
    private InlineKeyboardMarkup buildCancelKeyboard() {
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        return new InlineKeyboardMarkup(cancelBtn);
    }
}

