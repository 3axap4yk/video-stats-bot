package com.project.service;

import com.project.App;
import io.github.cdimascio.dotenv.Dotenv;

public class VKTest {
    public static void main(String[] args) {
        // Загружаем .env
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("VK_ACCESS_TOKEN");
        String videoUrl = "https://vkvideo.ru/video-111905078_456248997";

        System.out.println("=== ТЕСТ VK API ===\n");

        // 1. Проверка токена
        System.out.println("1. Проверка VK_ACCESS_TOKEN:");
        if (token == null || token.isEmpty()) {
            System.out.println("   ❌ Токен не найден в .env!");
            return;
        }
        System.out.println("   ✅ Токен загружен: " + token.substring(0, 10) + "...");

        // 2. Проверка парсинга ID
        System.out.println("\n2. Извлечение videoId из URL:");
        String videoId = extractVKId(videoUrl);
        if (videoId == null) {
            System.out.println("   ❌ Не удалось извлечь ID из URL: " + videoUrl);
            return;
        }
        System.out.println("   ✅ Video ID: " + videoId);

        // 3. Прямой вызов VKVideoClient
        System.out.println("\n3. Запрос к VK API...");
        try {
            VKVideoClient client = new VKVideoClient();
            String title = client.getTitleByVideoId(videoId);
            long views = client.getViewCountByVideoId(videoId);

            System.out.println("   ✅ Название: " + title);
            System.out.println("   ✅ Просмотры: " + views);
            System.out.println("\n🎉 VK API работает корректно!");

        } catch (VKVideoException e) {
            System.out.println("   ❌ Ошибка VK API: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Копируем метод из StatisticsService
    private static String extractVKId(String videoUrl) {
        if (videoUrl.contains("/video")) {
            String[] parts = videoUrl.split("/video");
            if (parts.length > 1) {
                String idPart = parts[1];
                if (idPart.contains("?")) idPart = idPart.split("\\?")[0];
                if (idPart.contains("&")) idPart = idPart.split("&")[0];
                return idPart;
            }
        }
        return null;
    }
}