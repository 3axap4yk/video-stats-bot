package com.project.service;

// Бизнес-логика: получение статистики с YouTube,
// сохранение в БД, расчёт суммарных просмотров

public class StatisticsService {
    private final String videoUrl;
    private final String videoId;
    private final YouTubeClient youTubeClient;
    private final VKVideoClient vkVideoClient;
    private final HostingType hostingType;

    public enum HostingType {
        YOUTUBE,
        VK
    }

    public StatisticsService(String videoUrl) throws VideoException {
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            throw new VideoException("URL видео не может быть пустым");
        }

        this.videoUrl = videoUrl;
        this.hostingType = detectedHostingType(videoUrl);
        this.videoId = extractVideoId(videoUrl);

        if (this.videoId == null || this.videoId.trim().isEmpty()) {
            throw new VideoException("Не удалось извлечь ID видео из URL: " + videoUrl);
        }

        // Инициализируем только нужного клиента
        if (this.hostingType == HostingType.YOUTUBE) {
            this.youTubeClient = new YouTubeClient();
            this.vkVideoClient = null;
        } else {
            this.vkVideoClient = new VKVideoClient();
            this.youTubeClient = null;
        }
    }

    // Определение YouTube или VK
    private HostingType detectedHostingType(String videoUrl) {
        String lowerUrl = videoUrl.toLowerCase();
        if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")) {
            return HostingType.YOUTUBE;
        } else if (lowerUrl.contains("vk.com") || lowerUrl.contains("vkvideo") || lowerUrl.contains("?z=video")) {
            return HostingType.VK;
        }
        throw new IllegalArgumentException("Неизвестный видеохостинг. Поддерживаются YouTube и VK Video");
    }

    //Методы извлечения ID из ссылки
    private String extractVideoId(String videoUrl) {
        if (hostingType == HostingType.YOUTUBE) {
            return extractYouTubeId(videoUrl);
        } else {
            return extractVKId(videoUrl);
        }
    }

    private String extractYouTubeId(String videoUrl) {
        String videoId = null;

        if (videoUrl.contains("youtu.be/")) {
            videoId = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
            if (videoId.contains("?")) {
                videoId = videoId.split("\\?")[0];
            }
        } else if (videoUrl.contains("v=")) {
            videoId = videoUrl.split("v=")[1];
            if (videoId.contains("&")) {
                videoId = videoId.split("&")[0];
            }
        }

        return videoId;
    }

    private String extractVKId(String videoUrl) {
        String videoId = null;

        if (videoUrl.contains("/video")) {
            String[] parts = videoUrl.split("/video");
            if (parts.length > 1) {
                String idPart = parts[1];
                if (idPart.contains("?")) idPart = idPart.split("\\?")[0];
                if (idPart.contains("&")) idPart = idPart.split("&")[0];
                videoId = idPart;
            }
        }

        if (videoId == null && videoUrl.contains("?z=video")) {
            String[] parts = videoUrl.split("\\?z=video");
            if (parts.length > 1) {
                String rawId = parts[1];
                if (rawId.contains("%2F")) {
                    rawId = rawId.split("%2F")[0];
                }
                if (rawId.contains("&")) {
                    rawId = rawId.split("&")[0];
                }
                videoId = rawId;
            }
        }

        if (videoId == null && videoUrl.contains("video?z=video")) {
            String[] parts = videoUrl.split("video\\?z=video");
            if (parts.length > 1) {
                String idPart = parts[1];
                if (idPart.contains("%2F")) idPart = idPart.split("%2F")[0];
                if (idPart.contains("&")) idPart = idPart.split("&")[0];
                videoId = idPart;
            }
        }

        if (videoId != null && !videoId.contains("_")) {
            videoId = null;
        }

        return videoId;
    }

    // Получение просмотров
    public long getViewCount() throws VideoException {
        try {
            if (hostingType == HostingType.YOUTUBE) {
                return youTubeClient.getViewCountByVideoId(videoId);
            } else {
                return vkVideoClient.getViewCountByVideoId(videoId);
            }
        } catch (YouTubeException | VKVideoException e) {
            throw new VideoException("Ошибка получения просмотров: " + e.getMessage(), e);
        }
    }

    // Получение названия видео
    public String getTitle() throws VideoException {
        try {
            if (hostingType == HostingType.YOUTUBE) {
                return youTubeClient.getTitleByVideoId(videoId);
            } else {
                return vkVideoClient.getTitleByVideoId(videoId);
            }
        } catch (YouTubeException | VKVideoException e) {
            throw new VideoException("Ошибка получения названия: " + e.getMessage(), e);
        }
    }
}
