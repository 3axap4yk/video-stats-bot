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
import com.project.service.YouTubeClient;
import com.project.service.VKVideoClient;
import com.project.service.YouTubeException;
import com.project.service.VKVideoException;
import com.project.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;
import static com.project.utils.FormatUtils.formatViews;

public class RefreshStatsLinks {

    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public RefreshStatsLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    public void onClick(long chatId, String callbackQueryId, int messageId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));

        SendResponse response = bot.execute(
                new SendMessage(chatId, "🔄 Обновляю статистику всех видео...")
        );
        int loadingMessageId = response.message().messageId();

        new Thread(() -> {
            try {
                performBatchUpdate(chatId, loadingMessageId);
            } catch (Exception e) {
                Logger.error("Ошибка в фоновом обновлении: " + e.getMessage(), e);
                bot.execute(new DeleteMessage(chatId, loadingMessageId));
                bot.execute(new SendMessage(chatId, "❌ Произошла ошибка при обновлении статистики. Попробуйте позже."));
            }
        }).start();
    }

    private void performBatchUpdate(long chatId, int loadingMessageId) {
        Logger.info("Начинаю обновление статистики для чата: " + chatId);

        List<VideoStats> videos = videoRepository.findAll();

        if (videos.isEmpty()) {
            bot.execute(new DeleteMessage(chatId, loadingMessageId));
            bot.execute(new SendMessage(chatId, "📭 Список ссылок пуст. Сначала добавьте видео через 'Добавить ссылку'."));
            return;
        }

        // Нормализуем платформы перед обработкой
        for (VideoStats video : videos) {
            if (video.getPlatform() != null && video.getPlatform().equalsIgnoreCase("VK Video")) {
                video.setPlatform("VK");
            }
        }

        List<VideoStats> youtubeVideos = videos.stream()
                .filter(v -> "YouTube".equalsIgnoreCase(v.getPlatform()))
                .collect(Collectors.toList());

        List<VideoStats> vkVideos = videos.stream()
                .filter(v -> "VK".equalsIgnoreCase(v.getPlatform()))
                .collect(Collectors.toList());

        int youtubeUpdated = 0;
        int vkUpdated = 0;

        // Счётчики ошибок
        int youtubeApiErrors = 0;
        int vkApiErrors = 0;
        int youtubeUnavailable = 0;
        int vkUnavailable = 0;

        // === YOUTUBE ===
        if (!youtubeVideos.isEmpty()) {
            Logger.info("Обновляю " + youtubeVideos.size() + " YouTube видео (batch-режим, до 50 за запрос)");
            try {
                YouTubeClient youTubeClient = new YouTubeClient();
                youtubeUpdated = youTubeClient.updateVideoStatsBatch(youtubeVideos);

                // Подсчитываем недоступные видео
                for (VideoStats video : youtubeVideos) {
                    if (video.isHostingUnavailable()) {
                        youtubeUnavailable++;
                    }
                }

                // Batch сохранение в БД
                videoRepository.saveAll(youtubeVideos);
                videoRepository.saveYouTubeIdsAll(youtubeVideos);

                Logger.success("YouTube batch обновлён: " + youtubeUpdated + "/" + youtubeVideos.size());
            } catch (YouTubeException e) {
                Logger.error("Ошибка batch-обновления YouTube: " + e.getMessage());
                youtubeApiErrors = youtubeVideos.size();
                for (VideoStats video : youtubeVideos) {
                    video.setHostingUnavailable(true);
                }
                videoRepository.saveAll(youtubeVideos);
            }
        }

        // === VK ===
        if (!vkVideos.isEmpty()) {
            Logger.info("Обновляю " + vkVideos.size() + " VK видео (batch-режим, до 25 за запрос)");
            try {
                VKVideoClient vkClient = new VKVideoClient();
                vkUpdated = vkClient.updateVideoStatsBatch(vkVideos);

                // Подсчитываем недоступные видео
                for (VideoStats video : vkVideos) {
                    if (video.isHostingUnavailable()) {
                        vkUnavailable++;
                    }
                }

                videoRepository.saveAll(vkVideos);
                videoRepository.saveVkIdsAll(vkVideos);

                Logger.success("VK batch обновлён: " + vkUpdated + "/" + vkVideos.size());
            } catch (VKVideoException e) {
                Logger.error("Ошибка batch-обновления VK: " + e.getMessage());
                vkApiErrors = vkVideos.size();
                for (VideoStats video : vkVideos) {
                    video.setHostingUnavailable(true);
                }
                videoRepository.saveAll(vkVideos);
            }
        }

        int totalVideos = videos.size();
        long totalViews = videoRepository.getTotalViews();

        int totalApiErrors = youtubeApiErrors + vkApiErrors;
        int totalUnavailable = youtubeUnavailable + vkUnavailable;

        StringBuilder resultMessage = new StringBuilder();
        resultMessage.append("✅ Обновление завершено!\n\n");
        resultMessage.append("📊 Статистика:\n");
        resultMessage.append("• YouTube: ").append(youtubeUpdated).append("/").append(youtubeVideos.size()).append("\n");
        resultMessage.append("• VK: ").append(vkUpdated).append("/").append(vkVideos.size()).append("\n");
        resultMessage.append("• Ошибок: ").append(totalApiErrors + totalUnavailable).append("\n\n");

        // ⚠️ Ошибка обновления (API)
        if (totalApiErrors > 0) {
            resultMessage.append("⚠️ Ошибка обновления (").append(totalApiErrors).append("):\n");
            if (youtubeApiErrors > 0) {
                resultMessage.append("• YouTube: ").append(youtubeApiErrors).append(" видео (ошибка API)\n");
            }
            if (vkApiErrors > 0) {
                resultMessage.append("• VK: ").append(vkApiErrors).append(" видео (ошибка API)\n");
            }
            resultMessage.append("\n");
        }

        // ⚠️ Видео недоступно
        if (totalUnavailable > 0) {
            resultMessage.append("⚠️ Видео недоступно (").append(totalUnavailable).append("):\n");
            if (youtubeUnavailable > 0) {
                resultMessage.append("• YouTube: ").append(youtubeUnavailable).append(" видео\n");
            }
            if (vkUnavailable > 0) {
                resultMessage.append("• VK: ").append(vkUnavailable).append(" видео\n");
            }
            resultMessage.append("\n");
        }

        resultMessage.append("• Всего видео: ").append(totalVideos).append("\n");
        resultMessage.append("• Суммарные просмотры: ").append(formatViews(totalViews)).append("\n\n");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton(BTN_BACK).callbackData(BACK)
        );

        bot.execute(new DeleteMessage(chatId, loadingMessageId));
        bot.execute(new SendMessage(chatId, resultMessage.toString()).replyMarkup(keyboard));
        Logger.success("Обновление завершено для чата: " + chatId);
    }
}