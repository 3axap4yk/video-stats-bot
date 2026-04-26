package com.project.repository;

import com.project.model.VideoStats;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VideoRepository {

    // INSERT/UPDATE с hosting_unavailable
    public void save(VideoStats stats) {
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
            System.out.println("Сохранено в БД: " + stats.getVideoUrl());

        } catch (SQLException e) {
            System.err.println("Ошибка сохранения: " + e.getMessage());
        }
    }

    // SELECT с hosting_unavailable
    public VideoStats findByUrl(String videoUrl) {
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
                stats.setLastUpdated(rs.getTimestamp("last_updated").toLocalDateTime());
                stats.setHostingUnavailable(rs.getBoolean("hosting_unavailable"));
                return stats;
            }

        } catch (SQLException e) {
            System.err.println("Ошибка поиска: " + e.getMessage());
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
                stats.setLastUpdated(rs.getTimestamp("last_updated").toLocalDateTime());
                stats.setHostingUnavailable(rs.getBoolean("hosting_unavailable"));
                list.add(stats);
            }

        } catch (SQLException e) {
            System.err.println("Ошибка получения списка: " + e.getMessage());
        }
        return list;
    }

    // Остальные методы (getTotalViews, getTotalLinks, deleteByUrl) остаются без изменений
    public long getTotalViews() {
        String sql = "SELECT SUM(views_count) as total FROM videos";

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong("total");
            }

        } catch (SQLException e) {
            System.err.println("Ошибка подсчёта: " + e.getMessage());
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
            System.err.println("Ошибка подсчёта количества ссылок: " + e.getMessage());
        }
        return 0;
    }

    public void deleteByUrl(String videoUrl) {
        String sql = "DELETE FROM videos WHERE link = ?";

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            pstmt.executeUpdate();
            System.out.println("Удалено из БД: " + videoUrl);

        } catch (SQLException e) {
            System.err.println("Ошибка удаления: " + e.getMessage());
        }
    }
}