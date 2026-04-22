package com.project.service;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// HTTP-запросы к YouTube Data API v3
// Парсинг JSON-ответа: название ролика и количество просмотров

public class YouTubeClient {
    private final String apiKey;
    private final String videoUrl;
    private final String videoId;
    private final HttpClient httpClient;

    public YouTubeClient(String videoUrl) {
        this.apiKey = loadApiKey();
        this.videoUrl = videoUrl;
        this.videoId = extractVideoId(videoUrl);
        this.httpClient = HttpClient.newHttpClient();
    }

    //Загрузка API из .env
    private String loadApiKey() {
        Dotenv dotenv = Dotenv.load();
        return dotenv.get("YOUTUBE_API_KEY");
    }

    //Метод извлечения ID из ссылки
    private String extractVideoId(String videoUrl) {
        String videoId = null;

        if (videoUrl.contains("youtu.be/")) {
            videoId = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
            if (videoId.contains("?")) {
                videoId = videoId.split("\\?") [0];
            }
        } else if (videoUrl.contains("v=")) {
            videoId = videoUrl.split("v=") [1];
            if (videoId.contains("&")) {
                videoId = videoId.split("&") [0];
            }
        }

        return videoId;
    }

    /** Название и число просмотров из одного ответа videos.list. */
    public record VideoInfo(String title, long viewCount) {
    }

    //Отправка HTTPS запроса
    public String sendRequest() throws Exception {
        String requestUrl = String.format("https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=%s&key=%s", videoId, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    /** Загружает snippet+statistics и возвращает название и просмотры. */
    public VideoInfo fetchVideoInfo() throws Exception {
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("Не удалось извлечь id видео из URL");
        }
        String jsonResponse = sendRequest();
        if (jsonResponse.contains("\"error\"")) {
            throw new RuntimeException("Ответ YouTube API содержит ошибку");
        }
        if (jsonResponse.contains("\"items\":[]") || jsonResponse.contains("\"items\": []")) {
            throw new RuntimeException("Видео не найдено в YouTube API (пустой items)");
        }
        return new VideoInfo(parseTitle(jsonResponse), parseViewCount(jsonResponse));
    }

    // Получить количество просмотров
    public long getViewCount() throws Exception {
        String jsonResponse = sendRequest();
        return parseViewCount(jsonResponse);
    }

    private String parseTitle(String json) {
        int itemsIdx = json.indexOf("\"items\"");
        if (itemsIdx < 0) {
            throw new RuntimeException("В ответе API нет поля items");
        }
        int snippetIdx = json.indexOf("\"snippet\":", itemsIdx);
        if (snippetIdx < 0) {
            throw new RuntimeException("В ответе API нет snippet");
        }
        // Ключ именно "title" в snippet (не channelTitle). Двоеточие после ключа — сразу после закрывающей
        // кавычки имени поля; нельзя искать ':' от позиции после первого ':', иначе для названий с
        // двоеточием внутри текста возьмётся ':' из значения, а не разделитель ключ/значение.
        int titleKeyStart = json.indexOf("\"title\"", snippetIdx);
        if (titleKeyStart < 0) {
            throw new RuntimeException("В ответе API нет title");
        }
        int p = titleKeyStart + "\"title\"".length();
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) {
            p++;
        }
        if (p >= json.length() || json.charAt(p) != ':') {
            throw new RuntimeException("Некорректный формат title в JSON (ожидалось : после ключа)");
        }
        int i = p + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            throw new RuntimeException("Ожидалась строка в title");
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                break;
            }
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"', '\\', '/' -> sb.append(next);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append(next);
                }
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    // Парсинг просмотров из JSON
    private long parseViewCount(String json) {
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
}
