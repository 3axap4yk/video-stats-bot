package com.project.bot;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

public class UrlResolver {

    public enum Platform {
        YOUTUBE,
        VK,
        UNKNOWN
    }

    public boolean isValidUrl(String rawUrl) {
        return extractHost(rawUrl).isPresent();
    }

    public Platform resolvePlatform(String rawUrl) {
        Optional<String> host = extractHost(rawUrl);
        if (host.isEmpty()) {
            return Platform.UNKNOWN;
        }

        String normalizedHost = host.get();
        if (isYouTubeHost(normalizedHost)) {
            return Platform.YOUTUBE;
        }

        if (isVkHost(normalizedHost)) {
            return Platform.VK;
        }

        return Platform.UNKNOWN;
    }

    private Optional<String> extractHost(String rawUrl) {
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

            return Optional.of(host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private boolean isYouTubeHost(String host) {
        return "youtube.com".equals(host)
                || host.endsWith(".youtube.com")
                || "youtu.be".equals(host)
                || host.endsWith(".youtu.be");
    }

    private boolean isVkHost(String host) {
        return "vk.com".equals(host)
                || host.endsWith(".vk.com")
                || "vkvideo.ru".equals(host)
                || host.endsWith(".vkvideo.ru");
    }
}
