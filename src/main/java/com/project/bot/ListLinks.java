package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
import com.project.utils.Logger;
import com.project.utils.ViewFormatter;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Обработчик постраничного просмотра списка сохранённых ссылок.
 * Поддерживает навигацию, группировку по платформам и итоговую статистику.
 */
public class ListLinks {

    private static final int PAGE_SIZE = 5; // Количество видео на одной странице
    // Префикс callbackData для навигации: "LIST_PAGE:N"
    public static final String PAGE_CALLBACK_PREFIX = "LIST_PAGE:";

    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public ListLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    // ───── Первый показ (кнопка "Список ссылок") ─────────────────────────────

    // Обрабатывает клик по кнопке "Список ссылок" в главном меню
    public void onClick(long chatId, String callbackQueryId) {
        Logger.info("ListLinks.onClick chatId=" + chatId);

        List<VideoStats> videos = loadSortedVideos();

        // Если список пуст — выводим сообщение без клавиатуры
        if (videos.isEmpty()) {
            sendMessage(chatId, "📭 Список ссылок пуст. Добавьте первую ссылку!", null);
            answerCallback(callbackQueryId);
            return;
        }

        // Формируем первую страницу и клавиатуру с навигацией
        String text = buildPage(videos, 0);
        InlineKeyboardMarkup keyboard = buildKeyboard(videos.size(), 0);

        sendMessage(chatId, text, keyboard);
        answerCallback(callbackQueryId);
    }

    // ───── Навигация по страницам ─────────────────────────────────────────────

    // Обрабатывает переключение страниц через инлайн-кнопки
    public void onPageChange(long chatId, int messageId, int page, String callbackQueryId) {
        List<VideoStats> videos = loadSortedVideos();

        int totalPages = totalPages(videos.size());
        // Защита от выхода за границы
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        String text = buildPage(videos, page);
        InlineKeyboardMarkup keyboard = buildKeyboard(videos.size(), page);

        // Редактируем существующее сообщение вместо отправки нового
        EditMessageText request = new EditMessageText(chatId, messageId, text)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true)
                .replyMarkup(keyboard);

        BaseResponse response = bot.execute(request);
        if (!response.isOk()) {
            Logger.error("EditMessageText error: " + response.description());
        }

        answerCallback(callbackQueryId);
    }

    // ───── Построение текста страницы ─────────────────────────────────────────

    // Формирует текст страницы с группировкой по платформам
    private String buildPage(List<VideoStats> videos, int page) {
        int totalPages = totalPages(videos.size());
        int from = page * PAGE_SIZE;                          // включительно
        int to = Math.min(from + PAGE_SIZE, videos.size());  // исключительно

        StringBuilder sb = new StringBuilder();
        sb.append("📋 <b>Ваши видео</b> (стр. ")
                .append(page + 1).append("/").append(totalPages).append("):\n\n");

        String prevPlatform = null;
        for (int i = from; i < to; i++) {
            VideoStats v = videos.get(i);

            // Заголовок платформы — выводим при смене платформы на странице
            if (!v.getPlatform().equals(prevPlatform)) {
                if (prevPlatform != null) sb.append("\n"); // отступ между группами
                sb.append(getPlatformHeader(v.getPlatform())).append("\n\n");
                prevPlatform = v.getPlatform();
            }

            String title = ViewFormatter.escapeHtml(v.getTitle());
            String views = ViewFormatter.formatViews(v.getViewCount());
            String platformLabel = getPlatformLabel(v.getPlatform());

            sb.append(i + 1).append(". <b>").append(title).append("</b>\n")
                    .append("   ▶️ <a href=\"").append(v.getVideoUrl())
                    .append("\">Смотреть на ").append(platformLabel).append("</a>\n")
                    .append("   📊 Просмотров: ").append(views);

            if (v.isHostingUnavailable()) {
                sb.append(" ⚠️ Видео недоступно");
            }
            if (v.getLastUpdated() != null) {
                sb.append("\n   🕐 Обновлено: ")
                        .append(v.getLastUpdated().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")));
            }
            sb.append("\n\n");
        }

        // Итоговая статистика — только на последней странице
        if (page == totalPages - 1) {
            long totalViews = videos.stream().mapToLong(VideoStats::getViewCount).sum();
            sb.append("━━━━━━━━━━━━━━━━━━\n")
                    .append("📊 <b>Итого:</b> ").append(videos.size()).append(" видео · ")
                    .append(ViewFormatter.formatViews(totalViews)).append(" просмотров");
        }

        return sb.toString();
    }

    // ───── Клавиатура ─────────────────────────────────────────────────────────

    // Строит клавиатуру с кнопками навигации и "Назад"
    private InlineKeyboardMarkup buildKeyboard(int totalVideos, int currentPage) {
        int totalPages = totalPages(totalVideos);

        List<InlineKeyboardButton> navRow = new ArrayList<>();

        // Кнопка "Назад" — если не первая страница
        if (currentPage > 0) {
            navRow.add(new InlineKeyboardButton("◀️ Назад")
                    .callbackData(PAGE_CALLBACK_PREFIX + (currentPage - 1)));
        }

        // Индикатор текущей страницы (неактивная кнопка)
        navRow.add(new InlineKeyboardButton(
                (currentPage + 1) + " / " + totalPages)
                .callbackData("LIST_PAGE_NOOP"));

        // Кнопка "Вперёд" — если не последняя страница
        if (currentPage < totalPages - 1) {
            navRow.add(new InlineKeyboardButton("Вперёд ▶️")
                    .callbackData(PAGE_CALLBACK_PREFIX + (currentPage + 1)));
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton(BotMessages.BTN_BACK)
                .callbackData(BotCallbacks.BACK);

        // Первый ряд — навигация, второй — кнопка "Назад"
        return new InlineKeyboardMarkup(
                navRow.toArray(new InlineKeyboardButton[0]),
                new InlineKeyboardButton[]{backButton}
        );
    }

    // ───── Загрузка и сортировка ──────────────────────────────────────────────

    /**
     * Загружает все видео и сортирует по платформе: YouTube → VK → прочие.
     * Внутри платформы порядок сохраняется из БД.
     */
    private List<VideoStats> loadSortedVideos() {
        List<VideoStats> all = videoRepository.findAll();

        // Группируем по платформе с сохранением порядка через LinkedHashMap
        Map<String, List<VideoStats>> grouped = all.stream()
                .collect(Collectors.groupingBy(
                        VideoStats::getPlatform,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Сортируем ключи (платформы) в нужном порядке
        List<String> sorted = new ArrayList<>(grouped.keySet());
        sorted.sort((a, b) -> Integer.compare(
                getPlatformOrderIndex(a),
                getPlatformOrderIndex(b)));

        // Собираем плоский список
        List<VideoStats> result = new ArrayList<>();
        for (String platform : sorted) {
            result.addAll(grouped.get(platform));
        }
        return result;
    }

    // ───── Вспомогательные ────────────────────────────────────────────────────

    // Вычисляет общее количество страниц
    private int totalPages(int totalVideos) {
        return (int) Math.ceil((double) totalVideos / PAGE_SIZE);
    }

    // Отправляет новое сообщение с HTML-разметкой
    private void sendMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage request = new SendMessage(chatId, text)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true);
        if (keyboard != null) request.replyMarkup(keyboard);

        SendResponse response = bot.execute(request);
        if (!response.isOk()) {
            Logger.error("SendMessage error: " + response.description());
        }
    }

    // Подтверждает обработку callback-запроса (убирает "часики")
    private void answerCallback(String callbackQueryId) {
        if (callbackQueryId != null && !callbackQueryId.isEmpty()) {
            bot.execute(new AnswerCallbackQuery(callbackQueryId));
        }
    }

    // Возвращает индекс для сортировки платформ
    private int getPlatformOrderIndex(String platform) {
        switch (platform) {
            case "YouTube": return 1;
            case "VK":      return 2;
            default:        return 999;
        }
    }

    // Возвращает заголовок группы платформы с иконкой
    private String getPlatformHeader(String platform) {
        switch (platform) {
            case "YouTube": return "▶️ <b>YouTube</b>";
            case "VK":      return "📱 <b>VK</b>";
            default:        return "🌐 <b>" + platform + "</b>";
        }
    }

    // Возвращает читаемое название платформы для ссылки
    private String getPlatformLabel(String platform) {
        switch (platform) {
            case "YouTube": return "YouTube";
            case "VK":      return "VK Video";
            default:        return platform;
        }
    }
}