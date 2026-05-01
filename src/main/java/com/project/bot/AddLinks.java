package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
import com.project.service.StatisticsService;
import com.project.service.YouTubeException;
import com.project.utils.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotCallbacks.CANCEL;
import static com.project.bot.BotMessages.*;
import static com.project.utils.FormatUtils.formatViews;

/**
 * Обработчик добавления новых ссылок на видео
 */
public class AddLinks {

    private final TelegramBot bot;
    private final UrlResolver urlResolver;
    private final LongConsumer showStartDialog;
    private final VideoRepository videoRepository = new VideoRepository();
    private final Set<Long> chatsAwaitingUrl = ConcurrentHashMap.newKeySet();

    public AddLinks(TelegramBot bot, UrlResolver urlResolver, LongConsumer showStartDialog) {
        this.bot = bot;
        this.urlResolver = urlResolver;
        this.showStartDialog = showStartDialog;
    }

    public boolean isAwaitingUrl(long chatId) {
        return chatsAwaitingUrl.contains(chatId);
    }

    public void resetChat(long chatId) {
        chatsAwaitingUrl.remove(chatId);
    }

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

    public void onSubmittedUrl(long chatId, String rawUrl) {
        String normalizedUrl = rawUrl == null ? "" : rawUrl.trim();
        normalizedUrl = normalizedUrl.replaceAll("\\s+", "");

        if (!urlResolver.isValidUrl(normalizedUrl)) {
            bot.execute(new SendMessage(chatId, INVALID_URL).replyMarkup(buildCancelKeyboard()));
            return;
        }

        UrlResolver.Platform platform = urlResolver.resolvePlatform(normalizedUrl);
        if (platform == UrlResolver.Platform.UNKNOWN) {
            bot.execute(new SendMessage(chatId, UNSUPPORTED_PLATFORM).replyMarkup(buildCancelKeyboard()));
            return;
        }

        if (!urlResolver.pointsToExistingVideo(normalizedUrl)) {
            bot.execute(new SendMessage(chatId, DEAD_LINK).replyMarkup(buildCancelKeyboard()));
            return;
        }

        if (platform == UrlResolver.Platform.VK) {
            bot.execute(new SendMessage(chatId, VK_STATS_NOT_SUPPORTED).replyMarkup(buildCancelKeyboard()));
            return;
        }

        Integer progressMessageId = sendProgressMessage(chatId);
        try {
            StatisticsService statsService;
            try {
                statsService = new StatisticsService(normalizedUrl);
            } catch (YouTubeException e) {
                Logger.error("Ошибка создания StatisticsService: " + e.getMessage());
                bot.execute(new SendMessage(chatId, YOUTUBE_API_FAILED).replyMarkup(buildCancelKeyboard()));
                return;
            }

            String title;
            long viewCount;
            try {
                title = statsService.getTitle();
                viewCount = statsService.getViewCount();
            } catch (YouTubeException e) {
                Logger.error("Ошибка получения данных с YouTube: " + e.getMessage());
                bot.execute(new SendMessage(chatId, YOUTUBE_API_FAILED).replyMarkup(buildCancelKeyboard()));
                return;
            }

            chatsAwaitingUrl.remove(chatId);

            VideoStats stats = new VideoStats();
            stats.setVideoUrl(normalizedUrl);
            stats.setPlatform("YouTube");
            stats.setTitle(title);
            stats.setViewCount(viewCount);
            stats.setHostingUnavailable(false);

            InlineKeyboardButton backBtn = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
            InlineKeyboardMarkup backKeyboard = new InlineKeyboardMarkup(backBtn);

            VideoStats existing = videoRepository.findByUrl(stats.getVideoUrl());
            if (existing != null) {
                String text = VIDEO_STATS_TEMPLATE.formatted(stats.getTitle(), formatViews(stats.getViewCount()), stats.getPlatform())
                        + "\n\nЭта ссылка уже добавлена.";
                bot.execute(new SendMessage(chatId, text).replyMarkup(backKeyboard));
                return;
            }

            videoRepository.save(stats);
            String text = VIDEO_STATS_TEMPLATE.formatted(stats.getTitle(), formatViews(stats.getViewCount()), stats.getPlatform())
                    + "\n\nСсылка добавлена.";
            bot.execute(new SendMessage(chatId, text).replyMarkup(backKeyboard));
        } finally {
            deleteMessageIfPresent(chatId, progressMessageId);
        }
    }

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

    private InlineKeyboardMarkup buildCancelKeyboard() {
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        return new InlineKeyboardMarkup(cancelBtn);
    }
}