package com.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.model.VideoStats;
import com.project.utils.Logger;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class VKVideoClient {
    private final String accessToken;
    private final String apiVersion;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, JsonNode> responseCache;
    private static final int VK_API_MAX_IDS = 25; // VK API ограничение на количество видео в одном запросе

    public VKVideoClient() {
        this.accessToken = loadAccessToken();
        this.apiVersion = loadApiVersion();
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.responseCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    private String loadAccessToken() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String token = dotenv.get("VK_ACCESS_TOKEN");
        if (token == null || token.trim().isEmpty()) {
            Logger.error("VK_ACCESS_TOKEN не найден в .env файле");
            throw new IllegalStateException("VK_ACCESS_TOKEN не найден в .env файле");
        }
        return token.trim();
    }

    private String loadApiVersion() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String version = dotenv.get("VK_API_VERSION");
        if (version == null || version.trim().isEmpty()) {
            return "5.131"; // версия по умолчанию
        }
        return version.trim();
    }

    public long getViewCountByVideoId(String videoId) throws VKVideoException {
        JsonNode response = getVideoInfo(videoId);

        JsonNode items = response.get("items");
        if (items == null || items.isEmpty()) {
            throw new VKVideoException("Видео с ID '" + videoId + "' не найдено");
        }

        JsonNode views = items.get(0).get("views");
        if (views == null) {
            throw new VKVideoException("views count не найден для видео " + videoId);
        }

        try {
            return views.asLong();
        } catch (Exception e) {
            throw new VKVideoException("Ошибка парсинга views count: " + e.getMessage(), e);
        }
    }

    public String getTitleByVideoId(String videoId) throws VKVideoException {
        JsonNode response = getVideoInfo(videoId);

        JsonNode items = response.get("items");
        if (items == null || items.isEmpty()) {
            throw new VKVideoException("Видео с ID '" + videoId + "' не найдено");
        }

        JsonNode title = items.get(0).get("title");
        if (title == null) {
            throw new VKVideoException("title не найден для видео " + videoId);
        }

        try {
            return title.asText();
        } catch (Exception e) {
            throw new VKVideoException("Ошибка парсинга title: " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет title и viewCount для пачки VideoStats одним запросом к VK API
     *
     * @param videoStatsList список видео для обновления
     * @return количество успешно обновленных видео
     */
    public int updateVideoStatsBatch(List<VideoStats> videoStatsList) throws VKVideoException {
        if (videoStatsList == null || videoStatsList.isEmpty()) {
            return 0;
        }

        // Фильтруем видео с валидными ID
        List<VideoStats> validVideos = videoStatsList.stream()
                .filter(vs -> vs.getVideoId() != null && !vs.getVideoId().isEmpty())
                .collect(Collectors.toList());

        if (validVideos.isEmpty()) {
            return 0;
        }

        int totalUpdated = 0;

        // Разбиваем на пачки по 25 (ограничение VK API)
        for (int i = 0; i < validVideos.size(); i += VK_API_MAX_IDS) {
            int end = Math.min(validVideos.size(), i + VK_API_MAX_IDS);
            List<VideoStats> batch = validVideos.subList(i, end);
            totalUpdated += updateBatch(batch);
        }

        return totalUpdated;
    }

    // Обновляет одну пачку видео (до 25 штук) одним запросом
    private int updateBatch(List<VideoStats> batch) throws VKVideoException {
        // Формируем список видео ID в формате owner_id_video_id
        String videoIds = batch.stream()
                .map(VideoStats::getVideoId)
                .collect(Collectors.joining(","));

        // URL для массового запроса VK API
        String url = String.format(
                "https://api.vk.com/method/video.get?videos=%s&access_token=%s&v=%s",
                videoIds, accessToken, apiVersion
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

            // Проверяем наличие ошибки VK API
            if (rootNode.has("error")) {
                handleVKError(rootNode.get("error"));
            }

            JsonNode responseNode = rootNode.get("response");
            if (responseNode == null) {
                throw new VKVideoException("Некорректный ответ VK API: отсутствует поле 'response'");
            }

            JsonNode items = responseNode.get("items");

            // Создаем Map для быстрого поиска данных по videoId
            Map<String, JsonNode> videoDataMap = new HashMap<>();
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    String ownerId = item.get("owner_id").asText();
                    String videoId = item.get("id").asText();
                    String fullId = ownerId + "_" + videoId;
                    videoDataMap.put(fullId, item);
                }
            }

            // Обновляем каждый VideoStats в пачке
            int updatedCount = 0;
            for (VideoStats videoStats : batch) {
                JsonNode videoData = videoDataMap.get(videoStats.getVideoId());

                if (videoData != null) {
                    // Получаем title
                    if (videoData.has("title")) {
                        videoStats.setTitle(videoData.get("title").asText());
                    }

                    // Получаем views count
                    if (videoData.has("views")) {
                        videoStats.setViewCount(videoData.get("views").asLong());
                    }

                    videoStats.setLastUpdated(LocalDateTime.now());
                    videoStats.setHostingUnavailable(false);
                    updatedCount++;

                    // Кэшируем каждый полученный ответ
                    responseCache.put(videoStats.getVideoId(), rootNode);
                } else {
                    // Видео не найдено или удалено
                    videoStats.setHostingUnavailable(true);
                    Logger.warn("Видео не найдено в VK: " + videoStats.getVideoId());
                }
            }

            Logger.info("VK Batch обновлен: " + updatedCount + "/" + batch.size() + " видео");

            return updatedCount;
        } catch (VKVideoException e) {
            throw e;
        } catch (Exception e) {
            Logger.error("Ошибка при массовом обновлении VK видео: " + e.getMessage(), e);
            throw new VKVideoException("Ошибка при массовом обновлении VK: " + e.getMessage(), e);
        }
    }

    private JsonNode getVideoInfo(String videoId) throws VKVideoException {
        if (videoId == null || videoId.trim().isEmpty()) {
            throw new VKVideoException("ID видео не может быть пустым");
        }

        // Проверяем кэш
        JsonNode cached = responseCache.getIfPresent(videoId);
        if (cached != null) {
            return cached;
        }

        String url = String.format(
                "https://api.vk.com/method/video.get?videos=%s&access_token=%s&v=%s",
                videoId, accessToken, apiVersion
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

            // Проверяем наличие ошибки VK API
            if (rootNode.has("error")) {
                handleVKError(rootNode.get("error"));
            }

            JsonNode responseNode = rootNode.get("response");
            if (responseNode == null) {
                throw new VKVideoException("Некорректный ответ VK API: отсутствует поле 'response'");
            }

            // Сохраняем в кэш
            responseCache.put(videoId, responseNode);

            return responseNode;
        } catch (VKVideoException e) {
            throw e;
        } catch (Exception e) {
            Logger.error("Ошибка парсинга JSON ответа VK: " + e.getMessage(), e);
            throw new VKVideoException("Неизвестная ошибка VK API", e);
        }
    }

    private void handleErrorResponse(int statusCode, String responseBody) throws VKVideoException {
        if (statusCode == 403) {
            throw new VKVideoException("Ошибка авторизации VK API. Проверьте VK_ACCESS_TOKEN в .env");
        } else if (statusCode == 429) {
            throw new VKVideoException("Превышен лимит запросов к VK API");
        }
        throw new VKVideoException(String.format("VK API вернул ошибку %d: %s", statusCode, responseBody));
    }

    private void handleVKError(JsonNode errorNode) throws VKVideoException {
        int errorCode = errorNode.get("error_code").asInt();
        String errorMsg = errorNode.get("error_msg").asText();

        switch (errorCode) {
            case 5:
                throw new VKVideoException("Ошибка авторизации VK: неверный access_token");
            case 6:
                throw new VKVideoException("Слишком много запросов к VK API");
            case 10:
                throw new VKVideoException("Внутренняя ошибка сервера VK");
            case 100:
                throw new VKVideoException("Неверный параметр запроса VK API: " + errorMsg);
            case 113:
                throw new VKVideoException("Видео не найдено или удалено");
            default:
                throw new VKVideoException("Ошибка VK API " + errorCode + ": " + errorMsg);
        }
    }

    public void clearCache() {
        responseCache.invalidateAll();
        Logger.info("Кэш VK API очищен");
    }

    public void clearCacheForVideo(String videoId) {
        responseCache.invalidate(videoId);
        Logger.info("Кэш для VK видео " + videoId + " очищен");
    }
}
