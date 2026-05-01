package com.project.repository;

import com.project.model.VideoStats;
import com.project.utils.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VideoRepository {

    // =============================================
    // ОСНОВНЫЕ МЕТОДЫ (таблица videos)
    // =============================================

    public void save(VideoStats stats) {
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

            // После сохранения видео, сохраняем платформо-специфичные данные
            if ("YouTube".equalsIgnoreCase(stats.getPlatform())) {
                String videoId = extractYouTubeId(stats.getVideoUrl());
                if (videoId != null) {
                    saveYouTubeId(stats.getVideoUrl(), videoId);
                }
            } else if ("VK".equalsIgnoreCase(stats.getPlatform()) || "VK Video".equalsIgnoreCase(stats.getPlatform())) {
                String vkId = extractVkId(stats.getVideoUrl());
                if (vkId != null) {
                    saveVkId(stats.getVideoUrl(), vkId, null);
                }
            }

        } catch (SQLException e) {
            Logger.error("Ошибка сохранения: " + e.getMessage());
        }
    }

    public VideoStats findByUrl(String videoUrl) {
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

    // =============================================
    // МЕТОДЫ ДЛЯ ТАБЛИЦЫ youtube
    // =============================================

    public void saveYouTubeId(String videoUrl, String youtubeId) {
        if (videoUrl == null || videoUrl.isBlank()) {
            Logger.warn("saveYouTubeId: videoUrl is null or blank");
            return;
        }
        if (youtubeId == null || youtubeId.isBlank()) {
            Logger.warn("saveYouTubeId: youtubeId is null or blank for URL: " + videoUrl);
            return;
        }

        String sql = """
            INSERT INTO youtube (video_link, id_youtube, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (video_link) DO UPDATE SET
                id_youtube = EXCLUDED.id_youtube,
                updated_at = CURRENT_TIMESTAMP
        """;

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            pstmt.setString(2, youtubeId);
            pstmt.executeUpdate();
            Logger.info("Сохранён YouTube ID для: " + videoUrl);

        } catch (SQLException e) {
            Logger.error("Ошибка сохранения YouTube ID: " + e.getMessage());
        }
    }

    public String findYouTubeIdByUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }

        String sql = "SELECT id_youtube FROM youtube WHERE video_link = ?";

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("id_youtube");
            }

        } catch (SQLException e) {
            Logger.error("Ошибка поиска YouTube ID: " + e.getMessage());
        }
        return null;
    }

    // =============================================
    // МЕТОДЫ ДЛЯ ТАБЛИЦЫ vk
    // =============================================

    public void saveVkId(String videoUrl, String vkId, String vkExternalId) {
        if (videoUrl == null || videoUrl.isBlank()) {
            Logger.warn("saveVkId: videoUrl is null or blank");
            return;
        }
        if (vkId == null || vkId.isBlank()) {
            Logger.warn("saveVkId: vkId is null or blank for URL: " + videoUrl);
            return;
        }

        String sql = """
            INSERT INTO vk (video_link, id_vk, id_vk_external, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (video_link) DO UPDATE SET
                id_vk = EXCLUDED.id_vk,
                id_vk_external = EXCLUDED.id_vk_external,
                updated_at = CURRENT_TIMESTAMP
        """;

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            pstmt.setString(2, vkId);
            pstmt.setString(3, vkExternalId);
            pstmt.executeUpdate();
            Logger.info("Сохранён VK ID для: " + videoUrl);

        } catch (SQLException e) {
            Logger.error("Ошибка сохранения VK ID: " + e.getMessage());
        }
    }

    public String findVkIdByUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }

        String sql = "SELECT id_vk FROM vk WHERE video_link = ?";

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("id_vk");
            }

        } catch (SQLException e) {
            Logger.error("Ошибка поиска VK ID: " + e.getMessage());
        }
        return null;
    }

    public String findVkExternalIdByUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }

        String sql = "SELECT id_vk_external FROM vk WHERE video_link = ?";

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("id_vk_external");
            }

        } catch (SQLException e) {
            Logger.error("Ошибка поиска VK external ID: " + e.getMessage());
        }
        return null;
    }

    // =============================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // =============================================

    private String extractYouTubeId(String videoUrl) {
        if (videoUrl == null) return null;

        if (videoUrl.contains("youtu.be/")) {
            String id = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
            if (id.contains("?")) {
                id = id.split("\\?")[0];
            }
            return id;
        } else if (videoUrl.contains("v=")) {
            String id = videoUrl.split("v=")[1];
            if (id.contains("&")) {
                id = id.split("&")[0];
            }
            return id;
        }
        return null;
    }

    private String extractVkId(String videoUrl) {
        if (videoUrl == null) return null;

        // VK video ID обычно в формате video-XXX_YYY или clipXXXXXX_XXX
        if (videoUrl.contains("video-") || videoUrl.contains("clip")) {
            String[] parts = videoUrl.split("/");
            for (String part : parts) {
                if (part.contains("video-") || part.contains("clip")) {
                    if (part.contains("?")) {
                        part = part.split("\\?")[0];
                    }
                    return part;
                }
            }
        }
        return null;
    }
}