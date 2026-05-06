package com.project.bot;

public final class BotMessages {
   // public static final String ACCESS_DENIED = "У вас нет доступа к этому боту.";
    public static final String GREETING = "Доступные команды:";
    public static final String PROMPT_SEND_URL = "Отправьте URL.";
    public static final String DEAD_LINK = "Отправленная Вами ссылка никуда не ведет. Проверьте и попробуйте добавить снова";
    public static final String INVALID_URL = "Некорректная ссылка. Проверьте формат URL и попробуйте снова.";
    public static final String UNSUPPORTED_PLATFORM = "Платформа не поддерживается. Доступны YouTube и VK. Проверьте ссылку и попробуйте ещё раз.";
    public static final String VIDEO_STATS_TEMPLATE = "Название: %s\nПросмотры: %s\nПлатформа: %s";
    public static final String REQUEST_IN_PROGRESS = "Выполняю запрос…";
    public static final String YOUTUBE_API_FAILED = "Не удалось получить данные с YouTube (API или сеть). Попробуйте ещё раз позже.";
    public static final String VK_STATS_NOT_SUPPORTED = "Для VK пока нет загрузки названия и просмотров через API. Используйте ссылку на YouTube.";
    public static final String ADD_LINK_CANCELLED = "Добавление ссылки отменено.";
    public static final String LINKS_LIST_UNAVAILABLE = "Список ссылок пока недоступен.";
    public static final String REFRESH_STATS_UNAVAILABLE = "Обновление статистики пока недоступно.";

    public static final String BTN_ADD_LINK = "\u2795 Добавить ссылку";
    public static final String BTN_LINKS_LIST = "\uD83D\uDCCB Список ссылок";
    public static final String BTN_REFRESH_STATS = "\uD83D\uDD04 Обновить статистику";
    public static final String BTN_CANCEL = "\u274C Отмена";
    public static final String BTN_BACK = "\uD83C\uDFE0 Вернуться";
    public static final String BTN_STATS = "\uD83D\uDCCA Статистика";

    public static final String WELCOME = """
        <b>VideoStats Bot</b>

        Бот для отслеживания статистики видео по ссылкам.

        <b>Функции:</b>
        — Добавление ссылок на видео
        — Просмотр списка отслеживаемых ссылок
        — Обновление статистики
        — Просмотр сводной статистики
        """;


    private BotMessages() {
    }
}