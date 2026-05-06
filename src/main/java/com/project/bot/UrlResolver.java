package com.project.bot;

import com.project.utils.Logger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// Разбор пользовательских URL, определение платформы и базовая проверка доступности видео.
public class UrlResolver {
    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    public enum Platform {
        YOUTUBE,
        VK,
        UNKNOWN
    }

    // Класс для хранения VK ID (внутренний и внешний)
    public static class VkVideoIds {
        private final String internalId;   // ownerId_videoId (например: -167789771_456239595)
        private final String externalId;   // access_key (например: 220df2876123d3542f)

        public VkVideoIds(String internalId, String externalId) {
            this.internalId = internalId;
            this.externalId = externalId;
        }

        public String getInternalId() { return internalId; }
        public String getExternalId() { return externalId; }

        // Полный ID для API (с access_key если есть)
        public String getFullId() {
            if (externalId != null && !externalId.isEmpty()) {
                return internalId + "_" + externalId;
            }
            return internalId;
        }

        public boolean hasExternalId() {
            return externalId != null && !externalId.isEmpty();
        }
    }

    // Проверяет только общий формат URL (http/https + host), без проверки существования видео.
    public boolean isValidUrl(String rawUrl) {
        return extractHost(rawUrl).isPresent();
    }

    // Проверяет, что ссылка похожа на ссылку на видео и что ресурс отвечает успешным статусом.
    public boolean pointsToExistingVideo(String rawUrl) {
        Logger.info("pointsToExistingVideo: начало для " + rawUrl);

        Optional<URI> uriOptional = extractUri(rawUrl);
        if (uriOptional.isEmpty()) {
            Logger.warn("pointsToExistingVideo: URI пустой");
            return false;
        }

        URI uri = uriOptional.get();
        Platform platform = resolvePlatform(rawUrl);
        Logger.info("pointsToExistingVideo: платформа = " + platform);

        if (platform == Platform.UNKNOWN || !hasVideoMarker(uri, platform)) {
            Logger.warn("pointsToExistingVideo: нет маркера видео");
            return false;
        }

        if (platform == Platform.YOUTUBE) {
            Logger.info("pointsToExistingVideo: проверяем YouTube oEmbed...");
            return respondsWithYouTubeOEmbed(uri.toString());
        }

        if (platform == Platform.VK) {
            Logger.info("pointsToExistingVideo: проверяем VK через HTTP...");
            return respondsWithSuccessStatus(uri.toString());
        }

        return false;
    }

    public Platform resolvePlatform(String rawUrl) {
        Optional<String> host = extractHost(rawUrl);
        if (host.isEmpty()) {
            Logger.warn("resolvePlatform: host не найден для: " + rawUrl);
            return Platform.UNKNOWN;
        }

        String normalizedHost = host.get();
        Logger.info("resolvePlatform: хост = " + normalizedHost);

        if (isYouTubeHost(normalizedHost)) {
            Logger.info("resolvePlatform: определён YouTube");
            return Platform.YOUTUBE;
        }

        if (isVkHost(normalizedHost)) {
            Logger.info("resolvePlatform: определён VK");
            return Platform.VK;
        }

        Logger.warn("resolvePlatform: неизвестный хост = " + normalizedHost);
        return Platform.UNKNOWN;
    }

    // Извлекает YouTube ID из URL
    public String extractYouTubeIdFromUrl(String url) {
        if (url == null) return null;

        if (url.contains("youtu.be/")) {
            String id = url.substring(url.lastIndexOf("/") + 1);
            if (id.contains("?")) {
                id = id.split("\\?")[0];
            }
            return id;
        } else if (url.contains("v=")) {
            String id = url.split("v=")[1];
            if (id.contains("&")) {
                id = id.split("&")[0];
            }
            return id;
        }
        return null;
    }

    // Извлекает VK ID из URL (внутренний и внешний)
    public VkVideoIds extractVkIdsFromUrl(String url) {
        if (url == null) return null;

        Logger.info("Извлекаем VK ID из URL: " + url);

        // Извлекаем внутренний ID (ownerId_videoId)
        Pattern pattern = Pattern.compile("video(-?\\d+_\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (!matcher.find()) {
            Logger.warn("Не удалось извлечь VK внутренний ID из: " + url);
            return null;
        }

        String internalId = matcher.group(1);  // например: -167789771_456239595 или 167789771_456239595
        // Для сообществ ownerId должен быть с минусом
        if (!internalId.startsWith("-") && url.contains("video-")) {
            internalId = "-" + internalId;
        }
        Logger.info("Внутренний VK ID: " + internalId);

        // Извлекаем access_key (внешний ID) из query-параметров
        String externalId = null;
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();
            if (query != null && query.contains("access_key=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("access_key=")) {
                        externalId = param.split("=")[1];
                        break;
                    }
                }
            }
        } catch (URISyntaxException e) {
            Logger.warn("Не удалось разобрать URL для извлечения access_key: " + url);
        }

        if (externalId != null) {
            Logger.info("Внешний VK ID (access_key): " + externalId);
        }

        return new VkVideoIds(internalId, externalId);
    }

    // Безопасно извлекает host в нижнем регистре.
    private Optional<String> extractHost(String rawUrl) {
        Optional<URI> uriOptional = extractUri(rawUrl);
        if (uriOptional.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(uriOptional.get().getHost().toLowerCase(Locale.ROOT));
    }

    // Нормализует и валидирует URI: обязательно http/https и непустой host.
    private Optional<URI> extractUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return Optional.empty();
            }

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return Optional.empty();
            }

            return Optional.of(uri);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    // Быстрая эвристика: содержит ли URL признаки ссылки именно на видео.
    private boolean hasVideoMarker(URI uri, Platform platform) {
        String path = Optional.ofNullable(uri.getPath()).orElse("");
        String query = Optional.ofNullable(uri.getQuery()).orElse("");

        if (platform == Platform.YOUTUBE) {
            if (path.startsWith("/watch")) {
                return getQueryParameter(query, "v")
                        .filter(this::isValidYouTubeVideoId)
                        .isPresent();
            }

            if (path.startsWith("/shorts/") || path.startsWith("/live/")) {
                return extractLastPathSegment(path)
                        .filter(this::isValidYouTubeVideoId)
                        .isPresent();
            }

            if ("youtu.be".equalsIgnoreCase(uri.getHost()) || uri.getHost().endsWith(".youtu.be")) {
                return extractLastPathSegment(path)
                        .filter(this::isValidYouTubeVideoId)
                        .isPresent();
            }
        }

        if (platform == Platform.VK) {
            return path.contains("video");
        }

        return false;
    }

    private Optional<String> extractLastPathSegment(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return Optional.empty();
        }

        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex < 0 || lastSlashIndex == path.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(path.substring(lastSlashIndex + 1));
    }

    private boolean isValidYouTubeVideoId(String videoId) {
        return videoId != null && YOUTUBE_VIDEO_ID_PATTERN.matcher(videoId).matches();
    }

    // Извлекает значение query-параметра, например v=... для YouTube watch-ссылок.
    private Optional<String> getQueryParameter(String query, String key) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }

            String decodedKey = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
            if (!key.equals(decodedKey)) {
                continue;
            }

            String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            if (!value.isBlank()) {
                return Optional.of(value);
            }
        }

        return Optional.empty();
    }

    // Сетевой чек доступности: сначала HEAD, затем GET как fallback.
    private boolean respondsWithSuccessStatus(String rawUrl) {
        Logger.info("respondsWithSuccessStatus: запрос к " + rawUrl);
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(rawUrl).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "video-stats-bot");
            int statusCode = connection.getResponseCode();
            connection.disconnect();
            Logger.info("respondsWithSuccessStatus: HEAD статус = " + statusCode);

            if (statusCode >= 200 && statusCode < 400) {
                return true;
            }

            HttpURLConnection fallbackConnection = (HttpURLConnection) URI.create(rawUrl).toURL().openConnection();
            fallbackConnection.setRequestMethod("GET");
            fallbackConnection.setInstanceFollowRedirects(true);
            fallbackConnection.setConnectTimeout(5000);
            fallbackConnection.setReadTimeout(5000);
            fallbackConnection.setRequestProperty("User-Agent", "video-stats-bot");
            int fallbackStatusCode = fallbackConnection.getResponseCode();
            fallbackConnection.disconnect();
            Logger.info("respondsWithSuccessStatus: GET статус = " + fallbackStatusCode);

            return fallbackStatusCode >= 200 && fallbackStatusCode < 400;
        } catch (Exception e) {
            Logger.error("respondsWithSuccessStatus: ошибка = " + e.getMessage());
            return false;
        }
    }

    // Для YouTube проверяем существование ролика через oEmbed, а не только через HTTP 200 страницы watch.
    private boolean respondsWithYouTubeOEmbed(String rawUrl) {
        try {
            String encodedUrl = URLEncoder.encode(rawUrl, StandardCharsets.UTF_8);
            String oEmbedUrl = "https://www.youtube.com/oembed?url=" + encodedUrl + "&format=json";
            HttpURLConnection connection = (HttpURLConnection) URI.create(oEmbedUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "video-stats-bot");
            int statusCode = connection.getResponseCode();
            connection.disconnect();

            return statusCode >= 200 && statusCode < 300 || statusCode == 401;
        } catch (Exception e) {
            return false;
        }
    }

    // Хосты YouTube, которые поддерживает бот.
    private boolean isYouTubeHost(String host) {
        return "youtube.com".equals(host)
                || host.endsWith(".youtube.com")
                || "youtu.be".equals(host)
                || host.endsWith(".youtu.be");
    }

    // Хосты VK, которые поддерживает бот.
    private boolean isVkHost(String host) {
        return "vk.com".equals(host)
                || host.endsWith(".vk.com")
                || "vkvideo.ru".equals(host)
                || host.endsWith(".vkvideo.ru");
    }
}