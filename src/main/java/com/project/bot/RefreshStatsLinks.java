package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteMessage; // Запрос на удаление сообщения из чата
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse; // Ответ от Telegram после отправки сообщения (содержит messageId)
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
import com.project.service.StatisticsService;
import com.project.service.YouTubeException;
import com.project.utils.Logger;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;
import static com.project.utils.FormatUtils.formatViews;

public class RefreshStatsLinks {

    private static final int YOUTUBE_API_RATE_LIMIT_DELAY_MS = 200;

    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public RefreshStatsLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    public void onClick(long chatId, String callbackQueryId, int messageId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));

        // Сохраняем ответ, чтобы получить messageId загрузочного сообщения
        SendResponse response = bot.execute(
                new SendMessage(chatId, "🔄 Обновляю статистику всех видео... Это может занять несколько секунд.")
        );
        // messageId нужен для последующего удаления этого сообщения
        int loadingMessageId = response.message().messageId();

        CompletableFuture.runAsync(() -> {
            try {
                // Передаём loadingMessageId в фоновый поток
                performUpdate(chatId, loadingMessageId);
            } catch (Exception e) {
                Logger.error("Ошибка в фоновом обновлении: " + e.getMessage());
                // Удаляем загрузочное сообщение при ошибке, чтобы оно не зависало
                bot.execute(new DeleteMessage(chatId, loadingMessageId));
                bot.execute(new SendMessage(chatId, "❌ Произошла ошибка при обновлении статистики. Попробуйте позже."));
            }
        });
    }

    // Принимаем loadingMessageId для управления загрузочным сообщением
    private void performUpdate(long chatId, int loadingMessageId) {
        Logger.info("Начинаю фоновое обновление статистики для чата: " + chatId);

        List<VideoStats> videos = videoRepository.findAll();

        if (videos.isEmpty()) {
            // Удаляем загрузочное сообщение перед отправкой ответа о пустом списке
            bot.execute(new DeleteMessage(chatId, loadingMessageId));
            bot.execute(new SendMessage(chatId, "📭 Список ссылок пуст. Сначала добавьте видео через 'Добавить ссылку'."));
            return;
        }

        int updatedCount = 0;
        int errorCount = 0;
        long totalViews = 0;

        for (VideoStats video : videos) {
            try {
                if (!isYouTube(video)) {
                    errorCount++;
                    totalViews += video.getViewCount();
                    video.setHostingUnavailable(true);
                    videoRepository.save(video);
                    continue;
                }

                StatisticsService statsService = new StatisticsService(video.getVideoUrl());
                long newViewCount = statsService.getViewCount();
                String newTitle = statsService.getTitle();

                video.setViewCount(newViewCount);
                video.setTitle(newTitle);
                video.setHostingUnavailable(false);
                videoRepository.save(video);

                updatedCount++;
                totalViews += newViewCount;
                Thread.sleep(YOUTUBE_API_RATE_LIMIT_DELAY_MS);

            } catch (YouTubeException | InterruptedException e) {
                errorCount++;
                Logger.error("Ошибка обновления: " + video.getVideoUrl() + " - " + e.getMessage());
                totalViews += video.getViewCount();
                video.setHostingUnavailable(true);
                videoRepository.save(video);
            }
        }

        String resultMessage = String.format(
                "✅ Обновление завершено!\n\n" +
                        "📊 Статистика:\n" +
                        "• Обновлено успешно: %d\n" +
                        "• С ошибками: %d\n" +
                        "• Всего видео: %d\n" +
                        "• Суммарные просмотры: %s",
                updatedCount, errorCount, videos.size(), formatViews(totalViews)
        );

        // Кнопка "Вернуться" с callback BACK для возврата в главное меню
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton(BTN_BACK).callbackData(BACK)
        );

        // Удаляем загрузочное сообщение перед отправкой итогового результата
        bot.execute(new DeleteMessage(chatId, loadingMessageId));
        // Отправляем итоговое сообщение с кнопкой "Вернуться"
        bot.execute(new SendMessage(chatId, resultMessage).replyMarkup(keyboard));
        Logger.success("Фоновое обновление завершено для чата: " + chatId);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static boolean isYouTube(VideoStats video) {
        if (video == null) return false;
        String platform = video.getPlatform();
        if (platform == null) return false;
        return platform.toLowerCase(Locale.ROOT).contains("youtube");
    }
}