package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
import com.project.utils.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;
import static com.project.utils.FormatUtils.formatViews;

/**
 * Обработчик кнопки "Список ссылок" — формирует и отправляет список видео из БД
 */
public class ListLinks {

    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public ListLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    /**
     * Обрабатывает нажатие кнопки: загружает видео из БД и отправляет список в чат
     */
    public void onClick(long chatId, String callbackQueryId) {
        Logger.info("ListLinks.onClick ВЫЗВАН!");
        Logger.info("   chatId: " + chatId);

        // Убираем индикатор загрузки у кнопки на стороне клиента
        bot.execute(new AnswerCallbackQuery(callbackQueryId));

        List<VideoStats> videos = videoRepository.findAll();
        Logger.info("Получено видео из БД: " + videos.size());

        if (videos.isEmpty()) {
            bot.execute(new SendMessage(chatId, "📭 Список ссылок пуст. Добавьте первую ссылку!"));
            return;
        }

        StringBuilder message = new StringBuilder();

        // Группируем видео по платформе (YOUTUBE, VK VIDEO, ...)
        Map<String, List<VideoStats>> groupedByPlatform = videos.stream()
                .collect(Collectors.groupingBy(
                        v -> normalizePlatformKey(v.getPlatform()),
                        Collectors.toList()
                ));

        // Сортируем: YouTube первый, VK второй, остальные по алфавиту
        List<String> sortedPlatforms = groupedByPlatform.keySet().stream()
                .sorted(Comparator
                        .comparingInt(ListLinks::platformOrderIndex)
                        .thenComparing(String::compareToIgnoreCase))
                .collect(Collectors.toList());

        int counter = 1;

        for (String platformKey : sortedPlatforms) {
            List<VideoStats> platformVideos = groupedByPlatform.get(platformKey);
            if (platformVideos == null || platformVideos.isEmpty()) {
                continue;
            }

            message.append(platformHeader(platformKey, platformVideos.size())).append("\n\n");

            for (VideoStats video : platformVideos) {
                String title = escapeHtml(video.getTitle());
                String url = escapeHtml(video.getVideoUrl());
                String platformLabel = escapeHtml(platformLabel(platformKey, video.getPlatform()));

                message.append(counter++).append(". ").append("<b>").append(title).append("</b>").append("\n");
                message.append("   - ").append("▶️ ").append("<a href=\"").append(url).append("\">")
                        .append("Смотреть на ").append(platformLabel).append("</a>").append("\n");
                message.append("   - ").append("👁️ Просмотров: ").append(formatViews(video.getViewCount()));

                if (video.isHostingUnavailable()) {
                    message.append(" ⚠️ Платформа временно недоступна");
                }
                message.append("\n\n");
            }
        }

        int totalLinks = videos.size();
        long totalViews = videoRepository.getTotalViews();
        message.append("Общее количество видео: ").append(totalLinks).append("\n");
        message.append("Общее количество просмотров: ").append(formatViews(totalViews));

        InlineKeyboardButton backButton = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);

        Logger.info("Отправляем сообщение со списком и кнопкой возврата...");

        SendMessage request = new SendMessage(chatId, message.toString());
        request.parseMode(ParseMode.HTML);
        request.disableWebPagePreview(true);
        request.replyMarkup(keyboard);
        bot.execute(request);

        Logger.success("Сообщение отправлено");
    }

    private static String platformHeader(String platformKey, int count) {
        return platformKey.toUpperCase(Locale.ROOT) + " (" + count + " видео)";
    }

    private static int platformOrderIndex(String platformKey) {
        String key = platformKey == null ? "" : platformKey.trim().toUpperCase(Locale.ROOT);
        if ("YOUTUBE".equals(key)) {
            return 0;
        }
        if ("VK".equals(key) || "VK VIDEO".equals(key) || "VKVIDEO".equals(key)) {
            return 1;
        }
        return 2;
    }

    private static String normalizePlatformKey(String platform) {
        if (platform == null || platform.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = platform.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.contains("YOUTUBE")) {
            return "YOUTUBE";
        }
        if (upper.equals("VK") || upper.contains("VK")) {
            return "VK VIDEO";
        }
        return upper;
    }

    private static String platformLabel(String platformKey, String rawPlatform) {
        String key = platformKey == null ? "" : platformKey.trim().toUpperCase(Locale.ROOT);
        if ("YOUTUBE".equals(key)) {
            return "YouTube";
        }
        if ("VK".equals(key) || "VK VIDEO".equals(key) || "VKVIDEO".equals(key)) {
            return "VK Video";
        }
        if (rawPlatform == null || rawPlatform.trim().isEmpty()) {
            return "Unknown";
        }
        return rawPlatform.trim();
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}