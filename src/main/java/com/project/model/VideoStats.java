package com.project.model;

import java.time.LocalDateTime;

public class VideoStats {
    private String videoUrl;
    private String platform;
    private String title;
    private long viewCount;
    private LocalDateTime lastUpdated;
    private boolean hostingUnavailable;  // НОВОЕ ПОЛЕ

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
    public String getVideoUrl() { return videoUrl; }
    public String getPlatform() { return platform; }
    public String getTitle() { return title; }
    public long getViewCount() { return viewCount; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public boolean isHostingUnavailable() { return hostingUnavailable; }

    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public void setPlatform(String platform) { this.platform = platform; }
    public void setTitle(String title) { this.title = title; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    public void setHostingUnavailable(boolean hostingUnavailable) { this.hostingUnavailable = hostingUnavailable; }
}