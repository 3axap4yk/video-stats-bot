package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
import com.project.utils.Logger;
import com.project.utils.ViewFormatter;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListLinks {
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public ListLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    public void onClick(long chatId, String callbackQueryId) {
        Logger.info("ListLinks.onClick ВЫЗВАН! chatId: " + chatId);

        List<VideoStats> videos = videoRepository.findAll();

        if (videos.isEmpty()) {
            String emptyMessage = "📭 Список ссылок пуст. Добавьте первую ссылку!";
            sendMessage(chatId, emptyMessage, null);
            answerCallback(callbackQueryId);
            return;
        }

        // Группируем по платформе
        Map<String, List<VideoStats>> groupedByPlatform = videos.stream()
                .collect(Collectors.groupingBy(VideoStats::getPlatform));

        // Сортируем платформы
        List<String> sortedPlatforms = new ArrayList<>(groupedByPlatform.keySet());
        sortedPlatforms.sort((p1, p2) -> {
            int order1 = getPlatformOrderIndex(p1);
            int order2 = getPlatformOrderIndex(p2);
            return Integer.compare(order1, order2);
        });

        // Строим все части сообщения
        List<String> allParts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        currentPart.append("📋 <b>Ваши видео:</b>\n\n");

        int totalLinks = videos.size();
        long totalViews = videos.stream().mapToLong(VideoStats::getViewCount).sum();
        int counter = 1;

        for (String platformKey : sortedPlatforms) {
            List<VideoStats> platformVideos = groupedByPlatform.get(platformKey);
            String platformHeader = getPlatformHeader(platformKey, platformVideos.size());
            String headerWithNewlines = platformHeader + "\n\n";

            // Проверяем, влезет ли заголовок платформы
            if (currentPart.length() + headerWithNewlines.length() > MAX_MESSAGE_LENGTH && currentPart.length() > 0) {
                allParts.add(currentPart.toString());
                currentPart = new StringBuilder();
                currentPart.append("📋 <b>Ваши видео:</b>\n\n");
            }
            currentPart.append(headerWithNewlines);

            for (VideoStats video : platformVideos) {
                String title = ViewFormatter.escapeHtml(video.getTitle());
                String url = video.getVideoUrl();
                String platform = video.getPlatform();
                String platformLabel = getPlatformLabel(platform, title);
                String viewsFormatted = ViewFormatter.formatViews(video.getViewCount());

                // Время обновления
                String timeStr = "";
                if (video.getLastUpdated() != null) {
                    timeStr = "\n   🕐 Обновлено: " + video.getLastUpdated()
                            .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"));
                }

                String unavailableStr = "";
                if (video.isHostingUnavailable()) {
                    unavailableStr = " ⚠️ Платформа временно недоступна";
                }

                String videoEntry = counter++ + ". <b>" + title + "</b>\n" +
                        "   ▶️ <a href=\"" + url + "\">Смотреть на " + platformLabel + "</a>\n" +
                        "   📊 Просмотров: " + viewsFormatted + unavailableStr + timeStr + "\n\n";

                // Если текущая запись не влезает - создаём новую часть
                if (currentPart.length() + videoEntry.length() > MAX_MESSAGE_LENGTH) {
                    // Добавляем текущую часть в список
                    allParts.add(currentPart.toString());
                    // Начинаем новую часть
                    currentPart = new StringBuilder();
                    currentPart.append("📋 <b>Ваши видео:</b>\n\n");
                    currentPart.append(platformHeader + "\n\n");
                }
                currentPart.append(videoEntry);
            }
        }

        // Добавляем итоговую статистику
        String footer = "\n━━━━━━━━━━━━━━━━━━\n" +
                "📊 <b>Итоговая статистика:</b>\n" +
                "• Всего видео: " + totalLinks + "\n" +
                "• Всего просмотров: " + ViewFormatter.formatViews(totalViews);

        if (currentPart.length() + footer.length() > MAX_MESSAGE_LENGTH) {
            allParts.add(currentPart.toString());
            currentPart = new StringBuilder();
            currentPart.append("📋 <b>Ваши видео:</b>\n\n");
        }
        currentPart.append(footer);
        allParts.add(currentPart.toString());

        // Отправляем все части
        InlineKeyboardButton backButton = new InlineKeyboardButton(BotMessages.BTN_BACK)
                .callbackData(BotCallbacks.BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);

        for (int i = 0; i < allParts.size(); i++) {
            String part = allParts.get(i);
            String header = (allParts.size() > 1) ? "📄 Часть " + (i + 1) + "/" + allParts.size() + "\n\n" : "";
            String messageText = header + part;

            // Кнопку возврата добавляем только к последней части
            InlineKeyboardMarkup partKeyboard = (i == allParts.size() - 1) ? keyboard : null;
            sendMessage(chatId, messageText, partKeyboard);
        }

        answerCallback(callbackQueryId);
    }

    private void sendMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage request = new SendMessage(chatId, text)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true);

        if (keyboard != null) {
            request.replyMarkup(keyboard);
        }

        SendResponse response = bot.execute(request);
        if (!response.isOk()) {
            Logger.error("Ошибка отправки сообщения: " + response.description());
        }
    }

    private void answerCallback(String callbackQueryId) {
        if (callbackQueryId != null && !callbackQueryId.isEmpty()) {
            AnswerCallbackQuery answer = new AnswerCallbackQuery(callbackQueryId);
            bot.execute(answer);
        }
    }

    private int getPlatformOrderIndex(String platform) {
        switch (platform) {
            case "YouTube": return 1;
            case "VK": return 2;
            default: return 999;
        }
    }

    private String getPlatformHeader(String platform, int count) {
        String icon;
        switch (platform) {
            case "YouTube": icon = "▶️"; break;
            case "VK": icon = "📱"; break;
            default: icon = "🌐"; break;
        }
        return icon + " <b>" + platform + "</b> (" + count + " видео)";
    }

    private String getPlatformLabel(String platform, String title) {
        switch (platform) {
            case "YouTube": return "YouTube";
            case "VK": return "VK Video";
            default: return platform;
        }
    }
}