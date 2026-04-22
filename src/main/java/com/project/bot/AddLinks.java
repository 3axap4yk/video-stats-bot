package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
import com.project.service.StatisticsService;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotCallbacks.CANCEL;
import static com.project.bot.BotMessages.ADD_LINK_CANCELLED;
import static com.project.bot.BotMessages.BTN_BACK;
import static com.project.bot.BotMessages.BTN_CANCEL;
import static com.project.bot.BotMessages.DEAD_LINK;
import static com.project.bot.BotMessages.INVALID_URL;
import static com.project.bot.BotMessages.PROMPT_SEND_URL;
import static com.project.bot.BotMessages.REQUEST_IN_PROGRESS;
import static com.project.bot.BotMessages.UNSUPPORTED_PLATFORM;
import static com.project.bot.BotMessages.VIDEO_STATS_TEMPLATE;
import static com.project.bot.BotMessages.VK_STATS_NOT_SUPPORTED;
import static com.project.bot.BotMessages.YOUTUBE_API_FAILED;

public class AddLinks {
    private final TelegramBot bot;
    private final UrlResolver urlResolver;
    private final StatisticsService statisticsService;
    private final LongConsumer showStartDialog;
    private final VideoRepository videoRepository = new VideoRepository();

    /**
     * Чаты, находящиеся в “диалоге добавления ссылки”.
     * <p>
     * Храним только признак состояния (ожидаем следующее текстовое сообщение с URL), без привязки к пользователю:
     * в Telegram в рамках одного чата у бота ровно один поток сообщений, а переключение режима происходит
     * через callback-кнопки.
     */
    private final Set<Long> chatsAwaitingUrl = ConcurrentHashMap.newKeySet();

    public AddLinks(
            TelegramBot bot,
            UrlResolver urlResolver,
            StatisticsService statisticsService,
            LongConsumer showStartDialog) {
        this.bot = bot;
        this.urlResolver = urlResolver;
        this.statisticsService = statisticsService;
        this.showStartDialog = showStartDialog;
    }

    public boolean isAwaitingUrl(long chatId) {
        return chatsAwaitingUrl.contains(chatId);
    }

    /** Сбрасывает “режим ожидания URL” для чата (напр. после отмены/успеха). */
    public void resetChat(long chatId) {
        chatsAwaitingUrl.remove(chatId);
    }

    /**
     * Переводит чат в режим ожидания URL и показывает кнопку отмены.
     * <p>
     * Важно отвечать на callback (`AnswerCallbackQuery`), иначе у пользователя в клиенте Telegram может
     * продолжать крутиться “часики” после нажатия кнопки.
     */
    public void onAddLinkClick(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.add(chatId);
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        InlineKeyboardMarkup cancelKeyboard = new InlineKeyboardMarkup(cancelBtn);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, PROMPT_SEND_URL).replyMarkup(cancelKeyboard));
    }

    /** Отменяет добавление ссылки и возвращает пользователя в стартовый диалог. */
    public void onCancel(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.remove(chatId);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, ADD_LINK_CANCELLED));
        showStartDialog.accept(chatId);
    }

    /** Выход “назад” из сценария добавления ссылки без сообщения об отмене. */
    public void onBack(long chatId, String callbackQueryId) {
        chatsAwaitingUrl.remove(chatId);
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        showStartDialog.accept(chatId);
    }

    /**
     * Обрабатывает ссылку, присланную пользователем в режиме ожидания URL.
     * <p>
     * Последовательность проверок намеренно “дешёвая → дорогая”:
     * сначала синтаксис/платформа, затем проверка существования видео, и только после этого сетевой запрос
     * в API статистики.
     */
    public void onSubmittedUrl(long chatId, String rawUrl) {
        /*
         * Нормализуем ввод из Telegram:
         * - trim() убирает пробелы/переносы по краям;
         * - remove internal whitespace защищает от copy/paste, когда ссылка “ломается” переносами строк.
         * Такие пробелы не являются валидной частью URL, поэтому их безопасно вычищать до валидации.
         */
        String normalizedUrl = rawUrl == null ? "" : rawUrl.trim();
        normalizedUrl = normalizedUrl.replaceAll("\\s+", "");

        if (!urlResolver.isValidUrl(normalizedUrl)) {
            bot.execute(new SendMessage(chatId, INVALID_URL).replyMarkup(buildCancelKeyboard()));
            return;
        }

        UrlResolver.Platform platform = urlResolver.resolvePlatform(normalizedUrl);
        if (platform == UrlResolver.Platform.UNKNOWN) {
            bot.execute(new SendMessage(chatId, UNSUPPORTED_PLATFORM).replyMarkup(buildCancelKeyboard()));
            return;
        }

        if (!urlResolver.pointsToExistingVideo(normalizedUrl)) {
            bot.execute(new SendMessage(chatId, DEAD_LINK).replyMarkup(buildCancelKeyboard()));
            return;
        }

        if (platform == UrlResolver.Platform.VK) {
            bot.execute(new SendMessage(chatId, VK_STATS_NOT_SUPPORTED).replyMarkup(buildCancelKeyboard()));
            return;
        }

        // Отправляем “прогресс”, чтобы пользователь видел, что бот не завис, пока идёт сетевой запрос.
        Integer progressMessageId = sendProgressMessage(chatId);
        try {
            Optional<VideoStats> fetched = statisticsService.fetchYoutubeStats(normalizedUrl);
            if (fetched.isEmpty()) {
                bot.execute(new SendMessage(chatId, YOUTUBE_API_FAILED).replyMarkup(buildCancelKeyboard()));
                return;
            }

            chatsAwaitingUrl.remove(chatId);
            VideoStats stats = fetched.get();
            InlineKeyboardButton backBtn = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
            InlineKeyboardMarkup backKeyboard = new InlineKeyboardMarkup(backBtn);

            // Дедупликация по нормализованному URL из результата парсинга/клиента, а не по raw-вводу пользователя.
            VideoStats existing = videoRepository.findByUrl(stats.getVideoUrl());
            if (existing != null) {
                String text = VIDEO_STATS_TEMPLATE.formatted(stats.getTitle(), stats.getViewCount(), stats.getPlatform())
                        + "\n\nЭта ссылка уже добавлена.";
                bot.execute(new SendMessage(chatId, text).replyMarkup(backKeyboard));
                return;
            }

            videoRepository.save(stats);
            String text = VIDEO_STATS_TEMPLATE.formatted(stats.getTitle(), stats.getViewCount(), stats.getPlatform())
                    + "\n\nСсылка добавлена.";
            bot.execute(new SendMessage(chatId, text).replyMarkup(backKeyboard));
        } finally {
            // Прогресс-сообщение удаляем всегда (и при успехе, и при ошибках/ранних return).
            deleteMessageIfPresent(chatId, progressMessageId);
        }
    }

    /** Отправляет служебное «Выполняю запрос…»; возвращает id сообщения для последующего удаления. */
    private Integer sendProgressMessage(long chatId) {
        SendResponse response = bot.execute(new SendMessage(chatId, REQUEST_IN_PROGRESS));
        if (response.isOk() && response.message() != null) {
            return response.message().messageId();
        }
        return null;
    }

    private void deleteMessageIfPresent(long chatId, Integer messageId) {
        if (messageId == null) {
            return;
        }
        bot.execute(new DeleteMessage(chatId, messageId));
    }

    // Унифицированная клавиатура отмены для сценария добавления ссылки.
    private InlineKeyboardMarkup buildCancelKeyboard() {
        InlineKeyboardButton cancelBtn = new InlineKeyboardButton(BTN_CANCEL).callbackData(CANCEL);
        return new InlineKeyboardMarkup(cancelBtn);
    }
}

