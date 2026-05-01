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

        // Сортируем платформы для стабильного порядка
        List<String> sortedPlatforms = new ArrayList<>(groupedByPlatform.keySet());
        sortedPlatforms.sort((p1, p2) -> {
            int order1 = getPlatformOrderIndex(p1);
            int order2 = getPlatformOrderIndex(p2);
            return Integer.compare(order1, order2);
        });

        // Строим одно большое сообщение
        StringBuilder fullMessage = new StringBuilder();
        fullMessage.append("📋 <b>Ваши видео:</b>\n\n");

        int totalLinks = videos.size();
        // Исправлено: используем getViewCount(), а не getTotalViews()
        long totalViews = videos.stream().mapToLong(VideoStats::getViewCount).sum();

        int counter = 1;
        for (String platformKey : sortedPlatforms) {
            List<VideoStats> platformVideos = groupedByPlatform.get(platformKey);
            String platformHeader = getPlatformHeader(platformKey, platformVideos.size());
            fullMessage.append(platformHeader).append("\n");

            for (VideoStats video : platformVideos) {
                String title = ViewFormatter.escapeHtml(video.getTitle());
                String url = video.getVideoUrl();
                String platform = video.getPlatform();

                String platformLabel = getPlatformLabel(platform, title);
                String viewsFormatted = ViewFormatter.formatViews(video.getViewCount());

                fullMessage.append(counter++).append(". ")
                        .append("<b>").append(title).append("</b>\n")
                        .append("   ▶️ <a href=\"").append(url).append("\">Смотреть на ")
                        .append(platformLabel).append("</a>\n")
                        .append("   📊 Просмотров: ").append(viewsFormatted);

                if (video.isHostingUnavailable()) {
                    fullMessage.append(" ⚠️ Платформа временно недоступна");
                }
                fullMessage.append("\n\n");
            }
        }

        // Добавляем итоговую статистику
        fullMessage.append("\n━━━━━━━━━━━━━━━━━━\n");
        fullMessage.append("📊 <b>Итоговая статистика:</b>\n");
        fullMessage.append("• Всего видео: ").append(totalLinks).append("\n");
        fullMessage.append("• Всего просмотров: ").append(ViewFormatter.formatViews(totalViews));

        String fullMessageStr = fullMessage.toString();

        // Исправлено: правильный способ создания кнопки
        InlineKeyboardButton backButton = new InlineKeyboardButton(BotMessages.BTN_BACK)
                .callbackData(BotCallbacks.BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);

        // Отправляем сообщение с разбиением на части
        sendLongMessage(chatId, fullMessageStr, keyboard);
        answerCallback(callbackQueryId);
    }

    private void sendLongMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int length = text.length();

        if (length <= MAX_MESSAGE_LENGTH) {
            sendMessage(chatId, text, keyboard);
            return;
        }

        // Разбиваем на части
        List<String> parts = new ArrayList<>();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + MAX_MESSAGE_LENGTH, length);

            // Ищем последний перенос строки для красивого разбиения
            if (end < length) {
                int lastNewLine = text.lastIndexOf('\n', end);
                if (lastNewLine > start) {
                    end = lastNewLine + 1;
                }
            }

            parts.add(text.substring(start, end));
            start = end;
        }

        // Отправляем части
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            String header = (parts.size() > 1) ? "📄 Часть " + (i + 1) + "/" + parts.size() + "\n\n" : "";
            String messageText = header + part;

            // Кнопку возврата добавляем только к последней части
            InlineKeyboardMarkup partKeyboard = (i == parts.size() - 1) ? keyboard : null;
            sendMessage(chatId, messageText, partKeyboard);
        }
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