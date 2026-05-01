package com.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.utils.Logger;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class YouTubeClient {
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, JsonNode> responseCache;

    public YouTubeClient() {
        this.apiKey = loadApiKey();
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.responseCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    private String loadApiKey() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String key = dotenv.get("YOUTUBE_API_KEY");
        if (key == null || key.trim().isEmpty()) {
            Logger.error("YOUTUBE_API_KEY не найден в .env файле");
            throw new IllegalStateException("YOUTUBE_API_KEY не найден в .env файле");
        }
        return key.trim();
    }

    public long getViewCountByVideoId(String videoId) throws YouTubeException {
        JsonNode root = getVideoInfo(videoId);

        JsonNode items = root.get("items");
        if (items == null || items.isEmpty()) {
            throw new YouTubeException("Видео с ID '" + videoId + "' не найдено");
        }

        JsonNode statistics = items.get(0).get("statistics");
        if (statistics == null || !statistics.has("viewCount")) {
            throw new YouTubeException("viewCount не найден для видео " + videoId);
        }

        try {
            return statistics.get("viewCount").asLong();
        } catch (Exception e) {
            throw new YouTubeException("Ошибка парсинга viewCount: " + e.getMessage(), e);
        }
    }

    public String getTitleByVideoId(String videoId) throws YouTubeException {
        JsonNode root = getVideoInfo(videoId);

        JsonNode items = root.get("items");
        if (items == null || items.isEmpty()) {
            throw new YouTubeException("Видео с ID '" + videoId + "' не найдено");
        }

        JsonNode snippet = items.get(0).get("snippet");
        if (snippet == null || !snippet.has("title")) {
            throw new YouTubeException("title не найден для видео " + videoId);
        }

        try {
            return snippet.get("title").asText();
        } catch (Exception e) {
            throw new YouTubeException("Ошибка парсинга title: " + e.getMessage(), e);
        }
    }

    private JsonNode getVideoInfo(String videoId) throws YouTubeException {
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new YouTubeException("ID видео не может быть пустым");
        }

        // Проверяем кэш
        JsonNode cached = responseCache.getIfPresent(videoId);
        if (cached != null) {
            return cached;
        }

        String url = String.format(
                "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=%s&key=%s",
                videoId, apiKey
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode != 200) {
                handleErrorResponse(statusCode, responseBody);
            }

            JsonNode rootNode = objectMapper.readTree(responseBody);

            // Сохраняем в кэш
            responseCache.put(videoId, rootNode);

            return rootNode;
        } catch (YouTubeException e) {
            throw e;
        } catch (Exception e) {
            Logger.error("Ошибка парсинга JSON ответа: " + e.getMessage(), e);
            throw new YouTubeException("Неизвестная ошибка", e);
        }
    }

    private void handleErrorResponse(int statusCode, String responseBody) throws YouTubeException {
        if (statusCode == 403) {
            if (responseBody.contains("accessNotConfigured")) {
                throw new YouTubeException("YouTube Data API v3 не включена в Google Cloud Console");
            } else if (responseBody.contains("quotaExceeded")) {
                throw new YouTubeException("Превышен дневной лимит запросов YouTube API (10 000 запросов в день)");
            } else if (responseBody.contains("keyInvalid")) {
                throw new YouTubeException("API ключ недействителен. Проверьте YOUTUBE_API_KEY в .env");
            }
        }
        throw new YouTubeException(String.format("YouTube API вернул ошибку %d: %s", statusCode, responseBody));
    }

    public void clearCache() {
        responseCache.invalidateAll();
        Logger.info("Кэш YouTube API очищен");
    }

    public void clearCacheForVideo(String videoId) {
        responseCache.invalidate(videoId);
        Logger.info("Кэш для видео " + videoId + " очищен");
    }
}