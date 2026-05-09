package com.project.model;

import java.time.LocalDateTime;

public class VideoStats {
    private Long id;  // ← НОВОЕ: ID из таблицы videos
    private String videoUrl;
    private String platform;
    private String title;
    private long viewCount;
    private LocalDateTime lastUpdated;
    private boolean hostingUnavailable;
    private String platformVideoId;  // ← НОВОЕ: id_youtube или id_vk из соответствующих таблиц

    // Конструкторы
    public VideoStats() {
    }

    public VideoStats(String videoUrl, String platform, String title, long viewCount, LocalDateTime lastUpdated) {
        this.videoUrl = videoUrl;
        this.platform = platform;
        this.title = title;
        this.viewCount = viewCount;
        this.lastUpdated = lastUpdated;
        this.hostingUnavailable = false;
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVideoUrl() { return videoUrl; }
    public String getVideoId() { return videoUrl; }  // Алиас для совместимости с batch-клиентами

    public String getPlatform() { return platform; }
    public String getTitle() { return title; }
    public long getViewCount() { return viewCount; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public boolean isHostingUnavailable() { return hostingUnavailable; }

    public String getPlatformVideoId() { return platformVideoId; }
    public void setPlatformVideoId(String platformVideoId) { this.platformVideoId = platformVideoId; }

    // Сеттеры существующих полей
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public void setPlatform(String platform) { this.platform = platform; }
    public void setTitle(String title) { this.title = title; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    public void setHostingUnavailable(boolean hostingUnavailable) { this.hostingUnavailable = hostingUnavailable; }
}