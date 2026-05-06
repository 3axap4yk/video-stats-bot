package com.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.model.VideoStats;
import com.project.repository.VideoRepository;
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
    private final VideoRepository videoRepository;
    private static final int VK_API_MAX_IDS = 25;

    public VKVideoClient() {
        this.accessToken = loadAccessToken();
        this.apiVersion = loadApiVersion();
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.videoRepository = new VideoRepository();
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
            return "5.131";
        }
        return version.trim();
    }

    public long getViewCountByVideoId(String videoId) throws VKVideoException {
        Logger.info("📊 VKVideoClient.getViewCountByVideoId: videoId = " + videoId);

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
            long result = views.asLong();
            Logger.info("📊 VKVideoClient.getViewCountByVideoId: views = " + result);
            return result;
        } catch (Exception e) {
            throw new VKVideoException("Ошибка парсинга views count: " + e.getMessage(), e);
        }
    }

    public String getTitleByVideoId(String videoId) throws VKVideoException {
        Logger.info("📝 VKVideoClient.getTitleByVideoId: videoId = " + videoId);

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
            String result = title.asText();
            Logger.info("📝 VKVideoClient.getTitleByVideoId: title = " + result);
            return result;
        } catch (Exception e) {
            throw new VKVideoException("Ошибка парсинга title: " + e.getMessage(), e);
        }
    }

    public int updateVideoStatsBatch(List<VideoStats> videoStatsList) throws VKVideoException {
        Logger.info("🔄 VKVideoClient.updateVideoStatsBatch: список из " + (videoStatsList != null ? videoStatsList.size() : 0) + " видео");

        if (videoStatsList == null || videoStatsList.isEmpty()) {
            return 0;
        }

        // Фильтруем видео с валидными platformVideoId
        List<VideoStats> validVideos = videoStatsList.stream()
                .filter(vs -> vs.getPlatformVideoId() != null && !vs.getPlatformVideoId().isEmpty())
                .collect(Collectors.toList());

        if (validVideos.isEmpty()) {
            Logger.warn("Нет видео с валидным platformVideoId для обновления VK");
            return 0;
        }

        Logger.info("Валидных VK видео для обновления: " + validVideos.size());

        int totalUpdated = 0;

        for (int i = 0; i < validVideos.size(); i += VK_API_MAX_IDS) {
            int end = Math.min(validVideos.size(), i + VK_API_MAX_IDS);
            List<VideoStats> batch = validVideos.subList(i, end);
            Logger.info("Обработка пачки VK видео: " + batch.size() + " шт. (с " + i + " по " + end + ")");
            totalUpdated += updateBatch(batch);
        }

        Logger.info("🔄 VKVideoClient.updateVideoStatsBatch: обновлено " + totalUpdated + " из " + validVideos.size());
        return totalUpdated;
    }

    private int updateBatch(List<VideoStats> batch) throws VKVideoException {
        // Формируем полные ID (с access_key если есть)
        List<String> fullIds = new java.util.ArrayList<>();
        Map<String, String> videoUrlToFullId = new HashMap<>();

        for (VideoStats video : batch) {
            String internalId = video.getPlatformVideoId();
            // Получаем externalId из БД
            String externalId = videoRepository.findVkExternalIdByUrl(video.getVideoUrl());
            String fullId;
            if (externalId != null && !externalId.isEmpty()) {
                fullId = internalId + "_" + externalId;
                Logger.info("Используем полный ID с access_key для " + video.getVideoUrl() + ": " + fullId);
            } else {
                fullId = internalId;
            }
            fullIds.add(fullId);
            videoUrlToFullId.put(video.getVideoUrl(), fullId);
        }

        String videoIds = String.join(",", fullIds);
        Logger.info("VK Batch запрос для ID: " + videoIds);

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

            Logger.info("VK API ответ: статус " + statusCode);

            if (statusCode != 200) {
                handleErrorResponse(statusCode, responseBody);
            }

            JsonNode rootNode = objectMapper.readTree(responseBody);

            if (rootNode.has("error")) {
                handleVKError(rootNode.get("error"));
            }

            JsonNode responseNode = rootNode.get("response");
            if (responseNode == null) {
                throw new VKVideoException("Некорректный ответ VK API: отсутствует поле 'response'");
            }

            JsonNode items = responseNode.get("items");

            Map<String, JsonNode> videoDataMap = new HashMap<>();
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    String ownerId = item.get("owner_id").asText();
                    String videoId = item.get("id").asText();
                    String internalId = ownerId + "_" + videoId;
                    // Проверяем наличие access_key в ответе
                    String accessKey = item.has("access_key") ? item.get("access_key").asText() : null;
                    if (accessKey != null) {
                        videoDataMap.put(internalId + "_" + accessKey, item);
                    }
                    videoDataMap.put(internalId, item);
                }
            }

            Logger.info("VK API вернул данных для " + videoDataMap.size() + " видео");

            int updatedCount = 0;
            for (VideoStats videoStats : batch) {
                String fullId = videoUrlToFullId.get(videoStats.getVideoUrl());
                JsonNode videoData = videoDataMap.get(fullId);

                // Если не нашли по полному ID, пробуем по внутреннему
                if (videoData == null && videoStats.getPlatformVideoId() != null) {
                    videoData = videoDataMap.get(videoStats.getPlatformVideoId());
                }

                if (videoData != null) {
                    if (videoData.has("title")) {
                        videoStats.setTitle(videoData.get("title").asText());
                    }

                    if (videoData.has("views")) {
                        videoStats.setViewCount(videoData.get("views").asLong());
                    }

                    // Сохраняем access_key если он есть в ответе и ещё не сохранён
                    if (videoData.has("access_key")) {
                        String accessKey = videoData.get("access_key").asText();
                        String currentExternal = videoRepository.findVkExternalIdByUrl(videoStats.getVideoUrl());
                        if (currentExternal == null || currentExternal.isEmpty()) {
                            videoRepository.saveVkIdFull(videoStats.getVideoUrl(),
                                    videoStats.getPlatformVideoId(), accessKey);
                            Logger.info("Сохранён access_key для " + videoStats.getVideoUrl() + ": " + accessKey);
                        }
                    }

                    videoStats.setLastUpdated(LocalDateTime.now());
                    videoStats.setHostingUnavailable(false);
                    updatedCount++;

                    responseCache.put(videoStats.getPlatformVideoId(), rootNode);
                } else {
                    videoStats.setHostingUnavailable(true);
                    Logger.warn("Видео не найдено в VK API: " + videoStats.getPlatformVideoId());
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
        Logger.info("🔍 VKVideoClient.getVideoInfo: videoId = " + videoId);

        if (videoId == null || videoId.trim().isEmpty()) {
            throw new VKVideoException("ID видео не может быть пустым");
        }

        JsonNode cached = responseCache.getIfPresent(videoId);
        if (cached != null) {
            Logger.info("VKVideoClient.getVideoInfo: возвращаем из кэша для " + videoId);
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

            if (rootNode.has("error")) {
                handleVKError(rootNode.get("error"));
            }

            JsonNode responseNode = rootNode.get("response");
            if (responseNode == null) {
                throw new VKVideoException("Некорректный ответ VK API: отсутствует поле 'response'");
            }

            responseCache.put(videoId, responseNode);
            Logger.info("VKVideoClient.getVideoInfo: успешно получены данные для " + videoId);

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