package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;

import static com.project.bot.BotMessages.LINKS_LIST_UNAVAILABLE;

public class ListLinks {
    private final TelegramBot bot;

    public ListLinks(TelegramBot bot) {
        this.bot = bot;
    }

    public void onClick(long chatId, String callbackQueryId) {
        bot.execute(new AnswerCallbackQuery(callbackQueryId));
        bot.execute(new SendMessage(chatId, LINKS_LIST_UNAVAILABLE));
    }
}

