package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
import com.project.service.StatisticsService;
import com.project.service.YouTubeException;
import com.project.utils.Logger;
import com.project.utils.ViewFormatter;

import java.util.List;
import java.util.Locale;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;

/**
 * Обработчик callback-кнопки "Обновить статистику всех ссылок"
 * Обновляет просмотры для всех видео в БД через YouTube API
 */
public class RefreshStatsLinks {

    private static final int YOUTUBE_API_RATE_LIMIT_DELAY_MS = 200;
    private static final int MAX_VIDEOS_IN_PREVIEW = 10;

    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public RefreshStatsLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    public void onClick(long chatId, String callbackQueryId, int messageId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, "🔄 Обновляю статистику всех видео... Это может занять несколько секунд."));

        List<VideoStats> videos = videoRepository.findAll();

        if (videos.isEmpty()) {
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
                "✅ Обновление завершено!\n\n📊 Статистика:\n• Обновлено успешно: %d\n• С ошибками: %d\n• Всего видео: %d\n• Суммарные просмотры: %s",
                updatedCount, errorCount, videos.size(), ViewFormatter.formatViews(totalViews)
        );

        bot.execute(new SendMessage(chatId, resultMessage));
        sendUpdatedList(chatId);
    }

    private void sendUpdatedList(long chatId) {
        List<VideoStats> videos = videoRepository.findAll();

        if (videos.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("📋 <b>Обновлённый список видео:</b>\n\n");

        for (int i = 0; i < Math.min(videos.size(), MAX_VIDEOS_IN_PREVIEW); i++) {
            VideoStats video = videos.get(i);
            message.append(i + 1).append(". ");
            message.append("<b>").append(ViewFormatter.escapeHtml(video.getTitle())).append("</b>").append("\n");
            message.append("   👁️ Просмотров: ").append(ViewFormatter.formatViews(video.getViewCount()));

            if (video.isHostingUnavailable()) {
                message.append(" ⚠️ Платформа временно недоступна");
            }
            message.append("\n\n");
        }

        if (videos.size() > MAX_VIDEOS_IN_PREVIEW) {
            message.append("И ещё ").append(videos.size() - MAX_VIDEOS_IN_PREVIEW).append(" видео...");
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);

        bot.execute(new SendMessage(chatId, message.toString())
                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML)
                .disableWebPagePreview(true)
                .replyMarkup(keyboard));
    }

    private static boolean isYouTube(VideoStats video) {
        if (video == null) {
            return false;
        }
        String platform = video.getPlatform();
        if (platform == null) {
            return false;
        }
        return platform.toLowerCase(Locale.ROOT).contains("youtube");
    }
}