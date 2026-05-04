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
import com.project.service.VideoException;
import com.project.utils.Logger;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;
import static com.project.utils.FormatUtils.formatViews;

/**
 * Обработчик фонового обновления статистики всех сохранённых видео.
 * Запускается асинхронно, показывает загрузочное сообщение и итоговый результат.
 */
public class RefreshStatsLinks {

    // Задержка между запросами к YouTube API для соблюдения rate limit
    private static final int YOUTUBE_API_RATE_LIMIT_DELAY_MS = 200;

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
                new SendMessage(chatId, "🔄 Обновляю статистику всех видео... Это может занять несколько секунд.")
        );
        // messageId нужен для последующего удаления этого сообщения
        int loadingMessageId = response.message().messageId();

        // Запускаем обновление в фоновом потоке, чтобы не блокировать бота
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

    // Выполняет обновление всех видео: запрос к API, сохранение, подсчёт статистики
    private void performUpdate(long chatId, int loadingMessageId) {
        Logger.info("Начинаю фоновое обновление статистики для чата: " + chatId);

        List<VideoStats> videos = videoRepository.findAll();

        // Если список пуст — удаляем загрузочное и выводим сообщение
        if (videos.isEmpty()) {
            bot.execute(new DeleteMessage(chatId, loadingMessageId));
            bot.execute(new SendMessage(chatId, "📭 Список ссылок пуст. Сначала добавьте видео через 'Добавить ссылку'."));
            return;
        }

        int updatedCount = 0;  // Счётчик успешно обновлённых
        int errorCount = 0;    // Счётчик ошибок
        long totalViews = 0;   // Суммарное количество просмотров

        for (VideoStats video : videos) {
            try {
                // Пропускаем не-YouTube видео, помечаем как недоступные
                if (!isYouTube(video)) {
                    errorCount++;
                    totalViews += video.getViewCount();
                    video.setHostingUnavailable(true);
                    videoRepository.save(video);
                    continue;
                }

                // Запрашиваем свежие данные через StatisticsService
                StatisticsService statsService = new StatisticsService(video.getVideoUrl());
                long newViewCount = statsService.getViewCount();
                String newTitle = statsService.getTitle();

                // Обновляем и сохраняем
                video.setViewCount(newViewCount);
                video.setTitle(newTitle);
                video.setHostingUnavailable(false);
                videoRepository.save(video);

                updatedCount++;
                totalViews += newViewCount;
                Thread.sleep(YOUTUBE_API_RATE_LIMIT_DELAY_MS); // Пауза между запросами

            } catch (VideoException | InterruptedException e) {
                errorCount++;
                Logger.error("Ошибка обновления: " + video.getVideoUrl() + " - " + e.getMessage());
                totalViews += video.getViewCount();
                video.setHostingUnavailable(true); // Помечаем как недоступное
                videoRepository.save(video);
            }
        }

        // Формируем итоговое сообщение с результатами
        String resultMessage = String.format(
                "✅ Обновление завершено!\n\n" +
                        "📊 Статистика:\n" +
                        "• Обновлено успешно: %d\n" +
                        "• С ошибками: %d\n" +
                        "• Всего видео: %d\n" +
                        "• Суммарные просмотры: %s",
                updatedCount, errorCount, videos.size(), formatViews(totalViews)
        );

        // Кнопка "Вернуться" для возврата в главное меню
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton(BTN_BACK).callbackData(BACK)
        );

        // Удаляем загрузочное сообщение и отправляем итоговый результат
        bot.execute(new DeleteMessage(chatId, loadingMessageId));
        bot.execute(new SendMessage(chatId, resultMessage).replyMarkup(keyboard));
        Logger.success("Фоновое обновление завершено для чата: " + chatId);
    }

    // Экранирует спецсимволы HTML (оставлено для совместимости, не используется в текущей логике)
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Проверяет, относится ли видео к платформе YouTube
    private static boolean isYouTube(VideoStats video) {
        if (video == null) return false;
        String platform = video.getPlatform();
        if (platform == null) return false;
        return platform.toLowerCase(Locale.ROOT).contains("youtube");
    }
}