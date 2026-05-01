package com.project.service;

// Бизнес-логика: получение статистики с YouTube,
// сохранение в БД, расчёт суммарных просмотров

public class StatisticsService {
    private final String videoUrl;
    private final String videoId;
    private final YouTubeClient youTubeClient;

    public StatisticsService(String videoUrl) throws YouTubeException {
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            throw new YouTubeException("URL видео не может быть пустым");
        }
        this.videoUrl = videoUrl;
        this.videoId = extractVideoId(videoUrl);

        if (this.videoId == null || this.videoId.trim().isEmpty()) {
            throw new YouTubeException("Не удалось извлечь ID видео из URL: " + videoUrl);
        }

        this.youTubeClient = new YouTubeClient();
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

    public long getViewCount() throws YouTubeException {
        return youTubeClient.getViewCountByVideoId(videoId);
    }

    public String getTitle() throws YouTubeException {
        return youTubeClient.getTitleByVideoId(videoId);
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getVideoId() {
        return videoId;
    }
}
