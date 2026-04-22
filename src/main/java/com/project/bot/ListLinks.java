package com.project.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.SendMessage;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;

import java.util.List;

import static com.project.bot.BotCallbacks.BACK;
import static com.project.bot.BotMessages.BTN_BACK;

public class ListLinks {
    private final TelegramBot bot;
    private final VideoRepository videoRepository;

    public ListLinks(TelegramBot bot) {
        this.bot = bot;
        this.videoRepository = new VideoRepository();
    }

    public void onClick(long chatId, String callbackQueryId) {
        System.out.println("🔘 ListLinks.onClick ВЫЗВАН!");
        System.out.println("   chatId: " + chatId);

        bot.execute(new AnswerCallbackQuery(callbackQueryId));

        List<VideoStats> videos = videoRepository.findAll();
        System.out.println("📊 Получено видео из БД: " + videos.size());

        if (videos.isEmpty()) {
            bot.execute(new SendMessage(chatId, "📭 Список ссылок пуст. Добавьте первую ссылку!"));
            return;
        }

        // Формируем сообщение со списком
        StringBuilder message = new StringBuilder("📋 СПИСОК ССЫЛОК:\n\n");

        for (int i = 0; i < videos.size(); i++) {
            VideoStats video = videos.get(i);
            message.append(i + 1).append(". ");
            message.append(video.getTitle()).append("\n");
            message.append("   📊 Просмотров: ").append(video.getViewCount()).append("\n");
            message.append("   🔗 Ссылка: ").append(video.getVideoUrl()).append("\n");
            message.append("   🕐 Обновлено: ").append(video.getLastUpdated()).append("\n\n");
        }

        // Добавляем кнопку "Вернуться"
        InlineKeyboardButton backButton = new InlineKeyboardButton(BTN_BACK).callbackData(BACK);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(backButton);

        System.out.println("📨 Отправляем сообщение со списком и кнопкой возврата...");
        bot.execute(new SendMessage(chatId, message.toString()).replyMarkup(keyboard));
        System.out.println("✅ Сообщение отправлено");
    }
}