package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;

public class ListLinks {
    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    /**
     * Форматирование просмотров с разделителем тысяч точкой (пример: 378465 -> 378.465).
     * Явно задаём символы, чтобы формат не зависел от локали/настроек ОС.
     */
    private static final DecimalFormat VIEW_COUNT_FORMAT;

    /** Формат даты для отображения пользователю (пример: 22-04-2026 20:34). */
    private static final DateTimeFormatter UPDATED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        VIEW_COUNT_FORMAT = new DecimalFormat("#,###", symbols);
        VIEW_COUNT_FORMAT.setGroupingUsed(true);
    }

    public ListLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    public void onClick(long chatId, String callbackQueryId) {
        System.out.println("🔘 ListLinks.onClick ВЫЗВАН!");
        System.out.println("   chatId: " + chatId);

        bot.execute(new AnswerCallbackQuery(callbackQueryId));

        List<VideoStats> videos = videoRepository.findAll();
        System.out.println("📊 Получено видео из БД: " + videos.size());

        if (videos.isEmpty()) {
            bot.execute(new SendMessage(chatId, "📭 Список ссылок пуст. Добавьте первую ссылку!")
                    // Telegram пытается построить web page preview для URL в тексте; здесь это не нужно.
                    .disableWebPagePreview(true));
            return;
        }

        // Формируем сообщение со списком
        StringBuilder message = new StringBuilder("📋 СПИСОК ССЫЛОК:\n\n");

        for (int i = 0; i < videos.size(); i++) {
            VideoStats video = videos.get(i);
            message.append(i + 1).append(". ");
            message.append(video.getTitle()).append("\n");
            message.append("   📊 Просмотров: ").append(formatViewCount(video.getViewCount())).append("\n");
            message.append("   🔗 Ссылка: ").append(video.getVideoUrl()).append("\n");
            message.append("   🕐 Обновлено: ").append(formatUpdatedAt(video.getLastUpdated())).append("\n\n");
        }

        // Добавляем кнопку "Вернуться"
        InlineKeyboardButton backButton = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);

        System.out.println("📨 Отправляем сообщение со списком и кнопкой возврата...");
        bot.execute(new SendMessage(chatId, message.toString())
                // Отключаем автогенерацию карточек/превью для ссылок в списке (иначе Telegram выберет одну из URL).
                .disableWebPagePreview(true)
                .replyMarkup(keyboard));
        System.out.println("✅ Сообщение отправлено");
    }

    private static String formatViewCount(long viewCount) {
        return VIEW_COUNT_FORMAT.format(viewCount);
    }

    private static String formatUpdatedAt(LocalDateTime lastUpdated) {
        if (lastUpdated == null) {
            return "—";
        }
        return UPDATED_AT_FORMAT.format(lastUpdated);
    }
}