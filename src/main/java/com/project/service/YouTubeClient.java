package com.project.service;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// HTTP-запросы к YouTube Data API v3
// Парсинг JSON-ответа

public class YouTubeClient {
    private final String apiKey;
    private final HttpClient httpClient;

    public YouTubeClient() {
        this.apiKey = loadApiKey();
        this.httpClient = HttpClient.newHttpClient();
    }

    //Получение просмотров
    public long getViewCountByVideoId(String videoId) throws Exception {
        String jsonResponse = sendRequest(videoId);
        return parseViewCount(jsonResponse, videoId);
    }

    //Получение названия
    public String getTitleByVideoId(String videoId) throws Exception {
        String jsonResponse = sendRequest(videoId);
        return parseTitle(jsonResponse, videoId);
    }

    //Загрузка API из .env
    private String loadApiKey() {
        Dotenv dotenv = Dotenv.load();
        return dotenv.get("YOUTUBE_API_KEY");
    }

    //Отправка HTTPS запроса
    private String sendRequest(String videoId) throws Exception {
        String requestUrl = String.format("https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=%s&key=%s", videoId, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    // Парсинг просмотров из JSON
    private long parseViewCount(String json, String videoId) {
        // Ищем viewCount в JSON ответе
        String searchPattern = "\"viewCount\":";
        int startIndex = json.indexOf(searchPattern);

        if (startIndex != -1) {
            startIndex += searchPattern.length();

            // Пропускаем пробелы
            while (startIndex < json.length() && json.charAt(startIndex) == ' ') {
                startIndex++;
            }

            // Если значение в кавычках
            if (json.charAt(startIndex) == '"') {
                startIndex++;
                int endIndex = json.indexOf("\"", startIndex);
                return Long.parseLong(json.substring(startIndex, endIndex));
            }
            // Если значение без кавычек
            else {
                int endIndex = json.indexOf(",", startIndex);
                if (endIndex == -1) {
                    endIndex = json.indexOf("}", startIndex);
                }
                return Long.parseLong(json.substring(startIndex, endIndex).trim());
            }
        }

        throw new RuntimeException("Не удалось найти viewCount в ответе API для видео " + videoId);
    }

    //Парсинг названия из JSON
    private String parseTitle(String json, String videoId) {
        // Ищем title в JSON ответе
        String searchPattern = "\"title\":";
        int startIndex = json.indexOf(searchPattern);

        if (startIndex != -1) {
            startIndex += searchPattern.length();

            // Пропускаем пробелы
            while (startIndex < json.length() && json.charAt(startIndex) == ' ') {
                startIndex++;
            }

            // Название всегда в кавычках
            if (json.charAt(startIndex) == '"') {
                startIndex++;
                int endIndex = json.indexOf("\"", startIndex);
                return json.substring(startIndex, endIndex);
            }
        }

        throw new RuntimeException("Не удалось найти title в ответе API для видео " + videoId);
    }
}
