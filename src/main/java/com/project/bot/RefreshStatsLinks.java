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

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;

// Обработчик callback-кнопки "Обновить статистику всех ссылок"
public class RefreshStatsLinks {

    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public RefreshStatsLinks(TelegramBot bot) {
        this.bot = bot;
        // Инициализируем репозиторий для работы с хранилищем видео
        this.videoRepository = new VideoRepository();
    }

    // Точка входа при нажатии кнопки: запускает массовое обновление статистики
    public void onClick(long chatId, String callbackQueryId, int messageId) {
        // Подтверждаем получение callback, чтобы убрать индикатор загрузки у кнопки
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, "🔄 Обновляю статистику всех видео... Это может занять несколько секунд."));

        List<VideoStats> videos = videoRepository.findAll();

        // Если список пуст — нет смысла продолжать
        if (videos.isEmpty()) {
            bot.execute(new SendMessage(chatId, "📭 Список ссылок пуст. Сначала добавьте видео через 'Добавить ссылку'."));
            return;
        }

        // Счётчики для итогового отчёта
        int updatedCount = 0;
        int errorCount = 0;
        long totalViews = 0;

        for (VideoStats video : videos) {
            try {
                // Пропускаем не-YouTube платформы — API для них не поддерживается
                if (!isYouTube(video)) {
                    errorCount++;
                    totalViews += video.getViewCount(); // Учитываем старые данные в суммарных просмотрах
                    video.setHostingUnavailable(true);
                    videoRepository.save(video);
                    continue;
                }

                // Запрашиваем актуальные данные через YouTube API
                StatisticsService statsService = new StatisticsService(video.getVideoUrl());
                long newViewCount = statsService.getViewCount();
                String newTitle = statsService.getTitle();

                // Обновляем данные и сбрасываем флаг недоступности
                video.setViewCount(newViewCount);
                video.setTitle(newTitle);
                video.setHostingUnavailable(false);
                videoRepository.save(video);

                updatedCount++;
                totalViews += newViewCount;

                // Задержка между запросами для соблюдения rate limit YouTube API
                Thread.sleep(200);

            } catch (YouTubeException | InterruptedException e) {
                errorCount++;
                System.err.println("Ошибка обновления: " + video.getVideoUrl() + " - " + e.getMessage());

                // Фиксируем старые просмотры — данные не потеряны, но устарели
                totalViews += video.getViewCount();

                // Помечаем видео как недоступное, чтобы отобразить предупреждение в списке
                video.setHostingUnavailable(true);
                videoRepository.save(video);
            }
        }

        // Формируем итоговый отчёт об обновлении
        String resultMessage = String.format(
                "✅ Обновление завершено!\n\n" +
                        "📊 Статистика:\n" +
                        "• Обновлено успешно: %d\n" +
                        "• С ошибками: %d\n" +
                        "• Всего видео: %d\n" +
                        "• Суммарные просмотры: %s",
                updatedCount, errorCount, videos.size(), formatViews(totalViews)
        );

        bot.execute(new SendMessage(chatId, resultMessage));

        // Отправляем актуальный список видео после обновления
        sendUpdatedList(chatId);
    }

    // Формирует и отправляет список видео с текущей статистикой (не более 10 позиций)
    private void sendUpdatedList(long chatId) {
        List<VideoStats> videos = videoRepository.findAll();

        if (videos.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("📋 <b>Обновлённый список видео:</b>\n\n");

        // Выводим не более 10 видео, чтобы не превышать лимит длины сообщения Telegram (~4096 символов)
        for (int i = 0; i < Math.min(videos.size(), 10); i++) {
            VideoStats video = videos.get(i);
            message.append(i + 1).append(". ");
            message.append("<b>").append(escapeHtml(video.getTitle())).append("</b>").append("\n");
            message.append("   👁️ Просмотров: ").append(formatViews(video.getViewCount()));

            // Если обновление не удалось — предупреждаем пользователя
            if (video.isHostingUnavailable()) {
                message.append(" ⚠️ Платформа временно недоступна");
            }
            message.append("\n\n");
        }

        // Уведомляем, что часть видео скрыта из-за ограничения на вывод
        if (videos.size() > 10) {
            message.append("И ещё ").append(videos.size() - 10).append(" видео...");
        }

        // Кнопка "Назад" для возврата в главное меню
        InlineKeyboardButton backButton = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);

        bot.execute(new SendMessage(chatId, message.toString())
                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML)
                .disableWebPagePreview(true)  // Отключаем превью ссылок, чтобы не захламлять чат
                .replyMarkup(keyboard));
    }

    // Экранирует спецсимволы MarkdownV2 — метод оставлен для возможного переключения режима парсинга
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

    // Экранирует спецсимволы HTML для безопасной вставки пользовательских данных в HTML-разметку
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")   // Обязательно первым, иначе двойное экранирование
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Форматирует число просмотров с разделителями разрядов (например: 1 234 567)
    // Заменяет неразрывный пробел (U+00A0) на обычный для корректного отображения в Telegram
    private static String formatViews(long views) {
        String formatted = NumberFormat.getInstance(new Locale("ru", "RU")).format(views);
        return formatted.replace('\u00A0', ' ');
    }

    // Проверяет, является ли платформа видео YouTube — только для неё доступно API статистики
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