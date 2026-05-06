package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.project.App;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
import com.project.service.StatisticsService;
import com.project.service.VideoException;
import com.project.utils.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotCallbacks.CANCEL;
import static com.project.bot.BotMessages.*;
import static com.project.utils.FormatUtils.formatViews;

/**
 * Обработчик добавления новых ссылок на видео.
 * Управляет состоянием ожидания URL от пользователя и обрабатывает ввод.
 */
public class AddLinks {

    private final TelegramBot bot;
    private final UrlResolver urlResolver;
    private final LongConsumer showStartDialog; // Колбэк для возврата в главное меню
    private final VideoRepository videoRepository = new VideoRepository();
    // Хранит ID чатов, которые сейчас находятся в режиме ожидания ввода ссылки
    private final Set<Long> chatsAwaitingUrl = ConcurrentHashMap.newKeySet();

    public AddLinks(TelegramBot bot, UrlResolver urlResolver, LongConsumer showStartDialog) {
        this.bot = bot;
        this.urlResolver = urlResolver;
        this.showStartDialog = showStartDialog;
    }

    // Проверяет, ожидает ли чат ввода ссылки
    public boolean isAwaitingUrl(long chatId) {
        return chatsAwaitingUrl.contains(chatId);
    }

    // Принудительно выводит чат из режима ожидания
    public void resetChat(long chatId) {
        chatsAwaitingUrl.remove(chatId);
    }

    // Активирует режим ожидания URL и отправляет prompt с кнопкой отмены
    public void onAddLinkClick(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.add(chatId);
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        InlineKeyboardMarkup cancelKeyboard = new InlineKeyboardMarkup(cancelBtn);
        bot.execute(new AnswerCallbackQuery(callbackQueryId)); // Закрываем "часики" на кнопке
        bot.execute(new SendMessage(chatId, PROMPT_SEND_URL).replyMarkup(cancelKeyboard));
    }

    // Отмена добавления: выходит из режима ожидания и возвращает в меню
    public void onCancel(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.remove(chatId);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, ADD_LINK_CANCELLED));
        showStartDialog.accept(chatId);
    }

    // Возврат назад: аналогично отмене, но без сообщения об отмене
    public void onBack(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.remove(chatId);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        showStartDialog.accept(chatId);
    }

    // Возвращает сообщение об ошибке API в зависимости от платформы
    private String getApiFailedMessage(UrlResolver.Platform platform) {
        if (platform == UrlResolver.Platform.VK) {
            return "Не удалось получить данные с VK (API или сеть). Попробуйте ещё раз позже.";
        }
        return YOUTUBE_API_FAILED;
    }

    /**
     * Обрабатывает присланную пользователем ссылку.
     * Выполняет валидацию, проверку платформы, получение статистики и сохранение.
     */
    public void onSubmittedUrl(long chatId, String rawUrl) {
        // Нормализация: удаляем пробелы по краям и внутри URL
        String normalizedUrl = rawUrl == null ? "" : rawUrl.trim();
        normalizedUrl = normalizedUrl.replaceAll("\\s+", "");

        // Проверка формата URL
        if (!urlResolver.isValidUrl(normalizedUrl)) {
            bot.execute(new SendMessage(chatId, INVALID_URL).replyMarkup(buildCancelKeyboard()));
            return;
        }

        // Определение платформы (YouTube, VK и т.д.)
        UrlResolver.Platform platform = urlResolver.resolvePlatform(normalizedUrl);
        if (platform == UrlResolver.Platform.UNKNOWN) {
            bot.execute(new SendMessage(chatId, UNSUPPORTED_PLATFORM).replyMarkup(buildCancelKeyboard()));
            return;
        }

        // Проверка существования видео/ресурса по ссылке (для VK пропускаем, так как API сам проверит)
        Logger.info("Платформа перед проверкой существования: " + platform);
        if (platform != UrlResolver.Platform.VK && !urlResolver.pointsToExistingVideo(normalizedUrl)) {
            bot.execute(new SendMessage(chatId, DEAD_LINK).replyMarkup(buildCancelKeyboard()));
            return;
        }

// VK через VK API
        if (platform == UrlResolver.Platform.VK) {
            Logger.info("Обработка VK видео: " + normalizedUrl);
            // Продолжаем обработку VK видео
        }

        // Отправляем временное сообщение о процессе загрузки
        Integer progressMessageId = sendProgressMessage(chatId);
        try {
            StatisticsService statsService;
            try {
                statsService = new StatisticsService(normalizedUrl);
            } catch (VideoException e) {
                Logger.error("Ошибка создания StatisticsService: " + e.getMessage());
                bot.execute(new SendMessage(chatId, getApiFailedMessage(platform)).replyMarkup(buildCancelKeyboard()));
                return;
            }

            String title;
            long viewCount;
            try {
                title = statsService.getTitle();
                viewCount = statsService.getViewCount();
            } catch (VideoException e) {
                Logger.error("Ошибка получения данных с " + platform + ": " + e.getMessage());
                bot.execute(new SendMessage(chatId, getApiFailedMessage(platform)).replyMarkup(buildCancelKeyboard()));
                return;
            }

            // Данные получены — выходим из режима ожидания URL
            chatsAwaitingUrl.remove(chatId);

            // Заполняем объект статистики
            VideoStats stats = new VideoStats();
            stats.setVideoUrl(normalizedUrl);
            stats.setPlatform(platform.toString()); // Используем реальную платформу (YouTube или VK)
            stats.setTitle(title);
            stats.setViewCount(viewCount);
            stats.setHostingUnavailable(false);

            // Клавиатура с кнопкой "Назад"
            InlineKeyboardButton backBtn = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
            InlineKeyboardMarkup backKeyboard = new InlineKeyboardMarkup(backBtn);

            // Проверка на дубликат
            VideoStats existing = videoRepository.findByUrl(stats.getVideoUrl());
            if (existing != null) {
                String text = VIDEO_STATS_TEMPLATE.formatted(stats.getTitle(), formatViews(stats.getViewCount()), stats.getPlatform())
                        + "\n\nЭта ссылка уже добавлена.";
                bot.execute(new SendMessage(chatId, text).replyMarkup(backKeyboard));
                return;
            }

            // Сохранение в БД и вывод результата
            videoRepository.save(stats);
            String text = VIDEO_STATS_TEMPLATE.formatted(stats.getTitle(), formatViews(stats.getViewCount()), stats.getPlatform())
                    + "\n\nСсылка добавлена.";
            bot.execute(new SendMessage(chatId, text).replyMarkup(backKeyboard));
        } finally {
            // Удаляем сообщение о прогрессе в любом случае
            deleteMessageIfPresent(chatId, progressMessageId);
        }
    }

    // Отправляет сообщение "Запрос выполняется..." и возвращает его ID
    private Integer sendProgressMessage(long chatId) {
        SendResponse response = bot.execute(new SendMessage(chatId, REQUEST_IN_PROGRESS));
        if (response.isOk() && response.message() != null) {
            return response.message().messageId();
        }
        return null;
    }

    // Удаляет сообщение по ID, если оно существует
    private void deleteMessageIfPresent(long chatId, Integer messageId) {
        if (messageId == null) {
            return;
        }
        bot.execute(new DeleteMessage(chatId, messageId));
    }

    // Создаёт клавиатуру с единственной кнопкой "Отмена"
    private InlineKeyboardMarkup buildCancelKeyboard() {
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        return new InlineKeyboardMarkup(cancelBtn);
    }
}