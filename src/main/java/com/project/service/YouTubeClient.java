package com.project.service;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// HTTP-запросы к YouTube Data API v3
// Парсинг JSON-ответа и извлечение количества просмотров

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

}
