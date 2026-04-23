package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;

import static com.project.bot.BotMessages.REFRESH_STATS_UNAVAILABLE;

public class RefreshStatsLinks {
    private final TelegramBot bot;

    public RefreshStatsLinks(TelegramBot bot) {
        this.bot = bot;
    }

    public void onClick(long chatId, String callbackQueryId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, REFRESH_STATS_UNAVAILABLE));
    }
}

