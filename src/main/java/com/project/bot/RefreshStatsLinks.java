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

import java.util.List;
import java.util.stream.Collectors;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;
import static com.project.utils.FormatUtils.formatViews;

/**
 * Обработчик фонового обновления статистики всех сохранённых видео.
 * Использует batch-запросы к API для экономии квоты и ускорения работы.
 */
public class RefreshStatsLinks {

    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public RefreshStatsLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    // Обрабатывает клик по кнопке "Обновить статистику"
    public void onClick(long chatId, String callbackQueryId, int messageId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));

        // Отправляем сообщение о начале обновления и сохраняем его ID
        SendResponse response = bot.execute(
                new SendMessage(chatId, "🔄 Обновляю статистику всех видео... Использую batch-режим для экономии API запросов.")
        );
        int loadingMessageId = response.message().messageId();

        // Запускаем обновление в фоновом потоке
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

    /**
     * Выполняет batch-обновление всех видео.
     * YouTube: до 50 видео за 1 запрос
     * VK: до 25 видео за 1 запрос
     */
    private void performBatchUpdate(long chatId, int loadingMessageId) {
        Logger.info("Начинаю BATCH-обновление статистики для чата: " + chatId);

        List<VideoStats> videos = videoRepository.findAll();

        if (videos.isEmpty()) {
            bot.execute(new DeleteMessage(chatId, loadingMessageId));
            bot.execute(new SendMessage(chatId, "📭 Список ссылок пуст. Сначала добавьте видео через 'Добавить ссылку'."));
            return;
        }

        // Разделяем видео по платформам
        List<VideoStats> youtubeVideos = videos.stream()
                .filter(v -> "YouTube".equalsIgnoreCase(v.getPlatform()))
                .collect(Collectors.toList());

        List<VideoStats> vkVideos = videos.stream()
                .filter(v -> "VK".equalsIgnoreCase(v.getPlatform()) || "VK Video".equalsIgnoreCase(v.getPlatform()))
                .collect(Collectors.toList());

        int youtubeUpdated = 0;
        int vkUpdated = 0;
        int errorCount = 0;

        // === BATCH-ОБНОВЛЕНИЕ YOUTUBE (до 50 видео за запрос) ===
        if (!youtubeVideos.isEmpty()) {
            Logger.info("Обновляю " + youtubeVideos.size() + " YouTube видео (batch-режим, до 50 за запрос)");
            try {
                YouTubeClient youTubeClient = new YouTubeClient();
                youtubeUpdated = youTubeClient.updateVideoStatsBatch(youtubeVideos);

                // Сохраняем обновленные видео в БД
                for (VideoStats video : youtubeVideos) {
                    videoRepository.save(video);

                    // ✅ НОВОЕ: обновляем YouTube ID в таблице youtube
                    if (video.getPlatformVideoId() != null && !video.getPlatformVideoId().isEmpty()) {
                        videoRepository.saveYouTubeId(video.getVideoUrl(), video.getPlatformVideoId());
                        Logger.info("Обновлён YouTube ID для: " + video.getVideoUrl() + " -> " + video.getPlatformVideoId());
                    }
                }
                Logger.success("YouTube batch обновлён: " + youtubeUpdated + "/" + youtubeVideos.size());
            } catch (YouTubeException e) {
                Logger.error("Ошибка batch-обновления YouTube: " + e.getMessage());
                errorCount += youtubeVideos.size();
                // Помечаем видео как недоступные
                for (VideoStats video : youtubeVideos) {
                    video.setHostingUnavailable(true);
                    videoRepository.save(video);
                }
            }
        }

        // === BATCH-ОБНОВЛЕНИЕ VK (до 25 видео за запрос) ===
        if (!vkVideos.isEmpty()) {
            Logger.info("Обновляю " + vkVideos.size() + " VK видео (batch-режим, до 25 за запрос)");
            try {
                VKVideoClient vkClient = new VKVideoClient();
                vkUpdated = vkClient.updateVideoStatsBatch(vkVideos);

                // Сохраняем обновленные видео в БД
                for (VideoStats video : vkVideos) {
                    videoRepository.save(video);

                    // ✅ НОВОЕ: обновляем VK ID в таблице vk
                    if (video.getPlatformVideoId() != null && !video.getPlatformVideoId().isEmpty()) {
                        videoRepository.saveVkId(video.getVideoUrl(), video.getPlatformVideoId(), null);
                        Logger.info("Обновлён VK ID для: " + video.getVideoUrl() + " -> " + video.getPlatformVideoId());
                    }
                }
                Logger.success("VK batch обновлён: " + vkUpdated + "/" + vkVideos.size());
            } catch (VKVideoException e) {
                Logger.error("Ошибка batch-обновления VK: " + e.getMessage());
                errorCount += vkVideos.size();
                // Помечаем видео как недоступные
                for (VideoStats video : vkVideos) {
                    video.setHostingUnavailable(true);
                    videoRepository.save(video);
                }
            }
        }

        int totalUpdated = youtubeUpdated + vkUpdated;
        int totalVideos = videos.size();
        long totalViews = videoRepository.getTotalViews();

        // Формируем детальное сообщение о результате
        StringBuilder resultMessage = new StringBuilder();
        resultMessage.append("✅ Batch-обновление завершено!\n\n");
        resultMessage.append("📊 Статистика:\n");
        resultMessage.append("• YouTube: ").append(youtubeUpdated).append("/").append(youtubeVideos.size()).append("\n");
        resultMessage.append("• VK: ").append(vkUpdated).append("/").append(vkVideos.size()).append("\n");
        resultMessage.append("• Ошибок: ").append(errorCount).append("\n");
        resultMessage.append("• Всего видео: ").append(totalVideos).append("\n");
        resultMessage.append("• Суммарные просмотры: ").append(formatViews(totalViews)).append("\n\n");

        // Добавляем информацию об экономии API запросов
        int youtubeRequests = (youtubeVideos.size() + 49) / 50; // округление вверх
        int vkRequests = (vkVideos.size() + 24) / 25;
        int totalRequests = youtubeRequests + vkRequests;
        int oldRequests = totalVideos; // было: по 1 запросу на видео
        if (totalVideos > 0) {
            resultMessage.append("💡 Экономия API: ").append(oldRequests - totalRequests)
                    .append(" запросов (было ").append(oldRequests).append(", стало ").append(totalRequests).append(")");
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton(BTN_BACK).callbackData(BACK)
        );

        // Удаляем загрузочное сообщение и отправляем итоговый результат
        bot.execute(new DeleteMessage(chatId, loadingMessageId));
        bot.execute(new SendMessage(chatId, resultMessage.toString()).replyMarkup(keyboard));
        Logger.success("Batch-обновление завершено для чата: " + chatId);
    }
}