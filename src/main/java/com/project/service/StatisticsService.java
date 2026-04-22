package com.project.service;

import com.project.model.VideoStats;

import java.util.Optional;

// Загрузка метаданных с YouTube через Data API (без сохранения в БД).

public class StatisticsService {

    /**
     * Запрашивает YouTube Data API: название и число просмотров.
     *
     * @return заполненный {@link VideoStats} (url, platform, title, views) или пусто при ошибке
     */
    public Optional<VideoStats> fetchYoutubeStats(String videoUrl) {
        try {
            YouTubeClient client = new YouTubeClient(videoUrl);
            YouTubeClient.VideoInfo info = client.fetchVideoInfo();

            VideoStats stats = new VideoStats();
            stats.setVideoUrl(videoUrl);
            stats.setPlatform("YouTube");
            stats.setTitle(info.title());
            stats.setViewCount(info.viewCount());
            return Optional.of(stats);
        } catch (Exception e) {
            System.err.println("Ошибка YouTube API: " + e.getMessage());
            return Optional.empty();
        }
    }
}
