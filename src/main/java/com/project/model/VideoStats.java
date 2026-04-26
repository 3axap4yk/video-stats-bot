package com.project.model;

import java.time.LocalDateTime;

// Модель данных: видео с платформы
// Хранит: ID видео, платформа, просмотры, дата обновления

public class VideoStats {
    private String videoUrl;
    private String platform;
    private String title;
    private long viewCount;
    private LocalDateTime lastUpdated;

    // Конструктор по умолчанию
    public VideoStats() {
    }

    // Конструктор со всеми параметрами
    public VideoStats(String videoUrl, String platform, String title, long viewCount, LocalDateTime lastUpdated) {
        this.videoUrl = videoUrl;
        this.platform = platform;
        this.title = title;
        this.viewCount = viewCount;
        this.lastUpdated = lastUpdated;
    }

    // Геттеры
    public String getVideoUrl() {
        return videoUrl;
    }

    public String getPlatform() {
        return platform;
    }

    public String getTitle() {
        return title;
    }

    public long getViewCount() {
        return viewCount;
    }

    //public long getID() {return }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    // Сеттеры
    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Для удобства - метод с String (если нужно)
    public void setLastUpdated(String lastUpdated) {
        if (lastUpdated != null) {
            this.lastUpdated = LocalDateTime.parse(lastUpdated.replace(" ", "T"));
        }
    }

    @Override
    public String toString() {
        return "VideoStats{" +
                "videoUrl='" + videoUrl + '\'' +
                ", platform='" + platform + '\'' +
                ", title='" + title + '\'' +
                ", viewCount=" + viewCount +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}