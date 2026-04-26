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

import java.util.List;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;

public class RefreshStatsLinks {
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
                StatisticsService statsService = new StatisticsService(video.getVideoUrl());
                long newViewCount = statsService.getViewCount();
                String newTitle = statsService.getTitle();

                video.setViewCount(newViewCount);
                video.setTitle(newTitle);
                video.setHostingUnavailable(false);  // СБРАСЫВАЕМ флаг при успешном обновлении
                videoRepository.save(video);

                updatedCount++;
                totalViews += newViewCount;
                Thread.sleep(200);

            } catch (YouTubeException | InterruptedException e) {
                errorCount++;
                System.err.println("Ошибка обновления: " + video.getVideoUrl() + " - " + e.getMessage());
                totalViews += video.getViewCount();

                // ОТМЕЧАЕМ видео как недоступное
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
                        "• Суммарные просмотры: %,d",
                updatedCount, errorCount, videos.size(), totalViews
        );

        bot.execute(new SendMessage(chatId, resultMessage));
        sendUpdatedList(chatId);
    }

    private void sendUpdatedList(long chatId) {
        List<VideoStats> videos = videoRepository.findAll();

        if (videos.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("📋 **Обновлённый список видео:**\n\n");

        for (int i = 0; i < Math.min(videos.size(), 10); i++) {
            VideoStats video = videos.get(i);
            message.append(i + 1).append(". ");
            message.append(escapeMarkdown(video.getTitle())).append("\n");
            message.append("   👁️ Просмотров: ").append(String.format("%,d", video.getViewCount()));

            // ДОБАВЛЯЕМ индикатор недоступности
            if (video.isHostingUnavailable()) {
                message.append(" ⚠️ *Платформа временно недоступна*");
            }
            message.append("\n\n");
        }

        if (videos.size() > 10) {
            message.append("_И ещё ").append(videos.size() - 10).append(" видео..._");
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);

        bot.execute(new SendMessage(chatId, message.toString())
                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.Markdown)
                .replyMarkup(keyboard));
    }

    private String escapeMarkdown(String text) {
        return text.replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }
}