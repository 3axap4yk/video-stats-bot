package com.project.bot;

import java.util.HashSet;
import java.util.Set;

/**
 * Whitelist по числовому Telegram user id.
 * Если переменная окружения не задана или после разбора список пуст — ограничений нет (все пользователи допускаются).
 */
public final class TelegramUserWhitelist {
    private final Set<Long> allowedIds;
    private final boolean restrictionEnabled;

    private TelegramUserWhitelist(Set<Long> allowedIds, boolean restrictionEnabled) {
        this.allowedIds = Set.copyOf(allowedIds);
        this.restrictionEnabled = restrictionEnabled;
    }

    /**
     * @param csv список id через запятую, например {@code "111,222,333"}
     */
    public static TelegramUserWhitelist fromCommaSeparatedIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return new TelegramUserWhitelist(Set.of(), false);
        }
        Set<Long> parsed = new HashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                parsed.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                System.err.println("Whitelist: пропущен некорректный id: " + trimmed);
            }
        }
        if (parsed.isEmpty()) {
            return new TelegramUserWhitelist(Set.of(), false);
        }
        return new TelegramUserWhitelist(parsed, true);
    }

    public boolean isRestrictionEnabled() {
        return restrictionEnabled;
    }

    public boolean allows(Long telegramUserId) {
        if (!restrictionEnabled) {
            return true;
        }
        return telegramUserId != null && allowedIds.contains(telegramUserId);
    }
}
