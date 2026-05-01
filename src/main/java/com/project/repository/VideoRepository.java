package com.project.repository;

import com.project.model.VideoStats;
import com.project.utils.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VideoRepository {

    // INSERT/UPDATE с hosting_unavailable
    public void save(VideoStats stats) {
        // =============================================
        // ВАЛИДАЦИЯ ВХОДНЫХ ДАННЫХ
        // =============================================
        if (stats == null) {
            Logger.error("Cannot save: VideoStats is null");
            return;
        }

        if (stats.getVideoUrl() == null || stats.getVideoUrl().isBlank()) {
            Logger.error("Cannot save: video URL is null or blank");
            return;
        }

        if (stats.getPlatform() == null || stats.getPlatform().isBlank()) {
            Logger.error("Cannot save: platform is null or blank for URL: " + stats.getVideoUrl());
            return;
        }

        if (stats.getTitle() == null || stats.getTitle().isBlank()) {
            Logger.error("Cannot save: title is null or blank for URL: " + stats.getVideoUrl());
            return;
        }

        if (stats.getViewCount() < 0) {
            Logger.warn("Saving video with negative view count: " + stats.getViewCount() + " for URL: " + stats.getVideoUrl());
            // Не возвращаем, а просто логируем - можно сохранить и 0
        }

        // =============================================
        // СОХРАНЕНИЕ В БД
        // =============================================
        String sql = """
            INSERT INTO videos (link, platform, title, views_count, last_updated, hosting_unavailable)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
            ON CONFLICT (link) DO UPDATE SET
                views_count = EXCLUDED.views_count,
                title = EXCLUDED.title,
                last_updated = CURRENT_TIMESTAMP,
                hosting_unavailable = EXCLUDED.hosting_unavailable
        """;

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, stats.getVideoUrl());
            pstmt.setString(2, stats.getPlatform());
            pstmt.setString(3, stats.getTitle());
            pstmt.setLong(4, stats.getViewCount());
            pstmt.setBoolean(5, stats.isHostingUnavailable());

            pstmt.executeUpdate();
            Logger.info("Сохранено в БД: " + stats.getVideoUrl());

        } catch (SQLException e) {
            Logger.error("Ошибка сохранения: " + e.getMessage());
        }
    }

    // SELECT с hosting_unavailable
    public VideoStats findByUrl(String videoUrl) {
        // Валидация входного параметра
        if (videoUrl == null || videoUrl.isBlank()) {
            Logger.warn("findByUrl called with null or blank URL");
            return null;
        }

        String sql = "SELECT * FROM videos WHERE link = ?";

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                VideoStats stats = new VideoStats();
                stats.setVideoUrl(rs.getString("link"));
                stats.setPlatform(rs.getString("platform"));
                stats.setTitle(rs.getString("title"));
                stats.setViewCount(rs.getLong("views_count"));
                Timestamp timestamp = rs.getTimestamp("last_updated");
                if (timestamp != null) {
                    stats.setLastUpdated(timestamp.toLocalDateTime());
                }
                stats.setHostingUnavailable(rs.getBoolean("hosting_unavailable"));
                return stats;
            }

        } catch (SQLException e) {
            Logger.error("Ошибка поиска: " + e.getMessage());
        }
        return null;
    }

    // SELECT ALL с hosting_unavailable
    public List<VideoStats> findAll() {
        List<VideoStats> list = new ArrayList<>();
        String sql = "SELECT * FROM videos ORDER BY last_updated DESC";

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                VideoStats stats = new VideoStats();
                stats.setVideoUrl(rs.getString("link"));
                stats.setPlatform(rs.getString("platform"));
                stats.setTitle(rs.getString("title"));
                stats.setViewCount(rs.getLong("views_count"));
                Timestamp timestamp = rs.getTimestamp("last_updated");
                if (timestamp != null) {
                    stats.setLastUpdated(timestamp.toLocalDateTime());
                }
                stats.setHostingUnavailable(rs.getBoolean("hosting_unavailable"));
                list.add(stats);
            }

        } catch (SQLException e) {
            Logger.error("Ошибка получения списка: " + e.getMessage());
        }
        return list;
    }

    public long getTotalViews() {
        String sql = "SELECT SUM(views_count) as total FROM videos";

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong("total");
            }

        } catch (SQLException e) {
            Logger.error("Ошибка подсчёта: " + e.getMessage());
        }
        return 0;
    }

    public int getTotalLinks() {
        String sql = "SELECT COUNT(*) as total FROM videos";

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            Logger.error("Ошибка подсчёта количества ссылок: " + e.getMessage());
        }
        return 0;
    }

    public void deleteByUrl(String videoUrl) {
        // Валидация входного параметра
        if (videoUrl == null || videoUrl.isBlank()) {
            Logger.warn("deleteByUrl called with null or blank URL");
            return;
        }

        String sql = "DELETE FROM videos WHERE link = ?";

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                Logger.info("Удалено из БД: " + videoUrl);
            } else {
                Logger.warn("Ничего не удалено: видео не найдено - " + videoUrl);
            }

        } catch (SQLException e) {
            Logger.error("Ошибка удаления: " + e.getMessage());
        }
    }
}