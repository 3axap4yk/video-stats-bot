package com.project.bot;

// Централизованные тексты бота, чтобы не дублировать строки в обработчиках.
public final class BotMessages {
    // Тексты сообщений пользователю.
    public static final String ACCESS_DENIED = "У вас нет доступа к этому боту.";
    public static final String GREETING = "Приветствую!";
    public static final String PROMPT_SEND_URL = "Отправьте URL.";
    public static final String DEAD_LINK = "Отправленная Вами ссылка никуда не ведет. Проверьте и попробуйте добавить снова";
    public static final String INVALID_URL = "Некорректная ссылка. Проверьте формат URL и попробуйте снова.";
    public static final String UNSUPPORTED_PLATFORM = "Платформа не поддерживается. Доступны YouTube и VK. Проверьте ссылку и попробуйте ещё раз.";
    public static final String VIDEO_STATS_TEMPLATE = "Название: %s\nПросмотры: %s\nПлатформа: %s";
    /** Временное сообщение на время запроса к YouTube; затем удаляется из чата. */
    public static final String REQUEST_IN_PROGRESS = "Выполняю запрос…";
    public static final String YOUTUBE_API_FAILED = "Не удалось получить данные с YouTube (API или сеть). Попробуйте ещё раз позже.";
    public static final String VK_STATS_NOT_SUPPORTED = "Для VK пока нет загрузки названия и просмотров через API. Используйте ссылку на YouTube.";
    public static final String ADD_LINK_CANCELLED = "Добавление ссылки отменено.";
    public static final String LINKS_LIST_UNAVAILABLE = "Список ссылок пока недоступен.";
    public static final String REFRESH_STATS_UNAVAILABLE = "Обновление статистики пока недоступно.";

    // Подписи inline-кнопок.
    public static final String BTN_ADD_LINK = "Добавить ссылку";
    public static final String BTN_LINKS_LIST = "Список ссылок";
    public static final String BTN_REFRESH_STATS = "Обновить статистику";
    public static final String BTN_CANCEL = "Отмена";
    public static final String BTN_BACK = "Вернуться";

    // Утилитарный класс: создание экземпляров запрещено.
    private BotMessages() {
    }
}
