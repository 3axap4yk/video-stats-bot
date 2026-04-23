package com.project.repository;

import com.project.model.VideoStats;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// JDBC-методы для работы с базой данных:
// INSERT (сохранение статистики видео), SELECT (получение списка)

public class VideoRepository {

    // INSERT (сохранить статистику видео)
    public void save(VideoStats stats) {
        String sql = """
            INSERT INTO videos (link, platform, title, views_count, last_updated)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (link) DO UPDATE SET
                views_count = EXCLUDED.views_count,
                last_updated = CURRENT_TIMESTAMP
        """;

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, stats.getVideoUrl());
            pstmt.setString(2, stats.getPlatform());
            pstmt.setString(3, stats.getTitle());
            pstmt.setLong(4, stats.getViewCount());

            pstmt.executeUpdate();
            System.out.println("Сохранено в БД: " + stats.getVideoUrl());

        } catch (SQLException e) {
            System.err.println("Ошибка сохранения: " + e.getMessage());
        }
    }

    // SELECT (найти статистику по ссылке)
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
                stats.setLastUpdated(rs.getString("last_updated"));
                return stats;
            }

        } catch (SQLException e) {
            System.err.println("Ошибка поиска: " + e.getMessage());
        }
        return null;
    }

    // SELECT (получить все видео)
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
                stats.setLastUpdated(rs.getString("last_updated"));
                list.add(stats);
            }

        } catch (SQLException e) {
            System.err.println("Ошибка получения списка: " + e.getMessage());
        }
        return list;
    }

    // SELECT (сумма всех просмотров)
    public long getTotalViews() {
        String sql = "SELECT SUM(views_count) as total FROM videos";

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong("total");
            }

        } catch (SQLException e) {
            System.err.println("Ошибка подсчета: " + e.getMessage());
        }
        return 0;
    }

    // SELECT (количество всех ссылок) - НОВЫЙ МЕТОД
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

    // DELETE (удалить видео по ссылке)
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