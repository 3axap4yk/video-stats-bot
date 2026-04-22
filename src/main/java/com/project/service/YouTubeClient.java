package com.project.service;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// HTTP-запросы к YouTube Data API v3
// Парсинг JSON-ответа

public class YouTubeClient {
    private final String apiKey;
    private final HttpClient httpClient;

    public YouTubeClient() throws YouTubeException {
            this.apiKey = loadApiKey();
            this.httpClient = HttpClient.newHttpClient();
    }

    //Получение просмотров
    public long getViewCountByVideoId(String videoId) throws YouTubeException {
        String jsonResponse = sendRequest(videoId);
        return parseViewCount(jsonResponse, videoId);
    }

    //Получение названия
    public String getTitleByVideoId(String videoId) throws YouTubeException {
        String jsonResponse = sendRequest(videoId);
        return parseTitle(jsonResponse, videoId);
    }

    //Загрузка API из .env
    private String loadApiKey() throws YouTubeException {
        try {
            Dotenv dotenv = Dotenv.load();
            String key = dotenv.get("YOUTUBE_API_KEY");

            if (key == null || key.trim().isEmpty()) {
                throw new YouTubeException("YOUTUBE_API_KEY не найден в .env файле");
            }

            return key;

        } catch (Exception e) {
            throw new YouTubeException("Ошибка загрузки API ключа из .env файла", e);
        }
    }

    //Отправка HTTPS запроса
    private String sendRequest(String videoId) throws YouTubeException {
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new YouTubeException("ID видео не может быть пустым");
        }

        String requestUrl = String.format("https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=%s&key=%s", videoId, apiKey);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            //Проверка на недействительный ключ или не включённое API
            if (response.statusCode() == 400 && (response.body().contains("API_KEY_INVALID") || response.body().contains("keyInvalid"))) {
                throw new YouTubeException("API ключ недействителен. Проверьте YOUTUBE_API_KEY в .env");
            }
            if (response.statusCode() == 403 && response.body().contains("accessNotConfigured")) {
                throw new YouTubeException("YouTube Data API v3 не включена в Google Cloud Console");
            }

            //Проверка что видео существует
            if (response.body().contains("\"items\":[]") || response.body().contains("\"items\": []")) {
                throw new YouTubeException("Видео с ID '" + videoId + "' не найдено");
            }

            //Проверка на превышение квоты
            if (response.statusCode() == 403 && response.body().contains("quotaExceeded")) {
                throw new YouTubeException("Превышен дневной лимит запросов YouTube API (10 000 запросов в день)");
            }

            if (response.body().contains("quotaExceeded")) {
                throw new YouTubeException("Превышен лимит квоты YouTube API. Лимит: 10 000 запросов в день");
            }

            //Любой другой статус, кроме 200
            if (response.statusCode() != 200) {
                throw new YouTubeException(String.format(
                        "YouTube API вернул ошибку %d: %s",
                        response.statusCode(),
                        response.body()
                ));
            }

            return response.body();

        } catch (ConnectException e) {
            throw new YouTubeException("Не удалось подключиться к YouTube API. Проверьте интернет соединение", e);
        } catch (SocketTimeoutException e) {
            throw new YouTubeException("Превышено время ожидания ответа от YouTube API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YouTubeException("Запрос был прерван", e);
        } catch (YouTubeException e) {
            throw e;
        } catch (Exception e) {
            throw new YouTubeException("Неизвестная ошибка" + e.getMessage(), e);
        }
    }

    // Парсинг просмотров из JSON
    private long parseViewCount(String json, String videoId) throws YouTubeException {
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

        throw new YouTubeException("Не удалось найти viewCount в ответе API для видео " + videoId);
    }

    //Парсинг названия из JSON
    private String parseTitle(String json, String videoId) throws YouTubeException {
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

        throw new YouTubeException("Не удалось найти title в ответе API для видео " + videoId);
    }
}
