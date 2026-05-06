package com.project.repository;

import com.project.model.VideoStats;
import com.project.utils.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Репозиторий для работы с таблицами видео в БД.
 *
 * Основные таблицы:
 * - videos: главная таблица со всеми видео
 * - youtube: дополнительная информация для YouTube видео
 * - vk: дополнительная информация для VK видео
 * - views_history: история просмотров для аналитики
 *
 * Все методы используют HikariCP пул соединений через DbConnection.
 */
public class VideoRepository {

    // =============================================
    // ОСНОВНЫЕ МЕТОДЫ (таблица videos с id PRIMARY KEY)
    // =============================================

    /**
     * Сохраняет или обновляет видео в базе данных.
     *
     * Логика работы:
     * 1. Если видео с таким link не существует → INSERT (создаётся новая запись)
     * 2. Если видео существует → UPDATE (обновляются просмотры, название, дата)
     * 3. После сохранения в videos, сохраняются платформозависимые данные (YouTube/VK ID)
     * 4. Добавляется запись в историю просмотров (для аналитики динамики)
     *
     * @param stats объект VideoStats с данными для сохранения
     */
    public void save(VideoStats stats) {
        if (stats == null) {
            Logger.error("Cannot save: VideoStats is null");
            return;
        }

        if (stats.getVideoUrl() == null || stats.getVideoUrl().isBlank()) {
            Logger.error("Cannot save: video URL is null or blank");
            return;
        }

        // SQL с ON CONFLICT - upsert (update or insert)
        // RETURNING id - возвращает ID созданной/обновлённой записи
        String sql = """
            INSERT INTO videos (link, platform, title, views_count, last_updated, hosting_unavailable)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
            ON CONFLICT (link) DO UPDATE SET
                views_count = EXCLUDED.views_count,
                title = EXCLUDED.title,
                last_updated = CURRENT_TIMESTAMP,
                hosting_unavailable = EXCLUDED.hosting_unavailable
            RETURNING id
        """;

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, stats.getVideoUrl());
            pstmt.setString(2, stats.getPlatform());
            pstmt.setString(3, stats.getTitle());
            pstmt.setLong(4, stats.getViewCount());
            pstmt.setBoolean(5, stats.isHostingUnavailable());

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                stats.setId((long) id);  // Сохраняем ID в объекте для дальнейшего использования
                Logger.info("Сохранено в БД: " + stats.getVideoUrl() + " (id=" + id + ")");
                saveToHistory(id, stats.getViewCount());  // Сохраняем историю просмотров
            }

            savePlatformSpecificData(stats);  // Сохраняем YouTube/VK ID в соответствующие таблицы

        } catch (SQLException e) {
            Logger.error("Ошибка сохранения: " + e.getMessage());
        }
    }

    /**
     * Сохраняет запись в таблицу истории просмотров.
     * Используется для отслеживания динамики изменения просмотров.
     *
     * @param videoId ID видео из таблицы videos
     * @param viewsCount текущее количество просмотров
     */
    public void saveToHistory(int videoId, long viewsCount) {
        String sql = "INSERT INTO views_history (video_id, views_count) VALUES (?, ?)";
        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, videoId);
            pstmt.setLong(2, viewsCount);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            Logger.error("Ошибка сохранения истории: " + e.getMessage());
        }
    }

    /**
     * Сохраняет платформозависимые данные (YouTube ID или VK ID).
     * Вызывается автоматически при сохранении видео.
     *
     * @param stats объект VideoStats с данными
     */
    private void savePlatformSpecificData(VideoStats stats) {
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
    }

    /**
     * Находит видео по его ID в таблице videos.
     *
     * @param id первичный ключ из таблицы videos
     * @return VideoStats или null, если не найдено
     */
    public VideoStats findById(int id) {
        String sql = """
            SELECT v.*, 
                   COALESCE(y.id_youtube, vk.id_vk) as platform_video_id
            FROM videos v
            LEFT JOIN youtube y ON v.link = y.video_link
            LEFT JOIN vk ON v.link = vk.video_link
            WHERE v.id = ?
        """;

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                VideoStats stats = mapResultSetToVideoStats(rs);
                stats.setPlatformVideoId(rs.getString("platform_video_id"));
                return stats;
            }

        } catch (SQLException e) {
            Logger.error("Ошибка поиска по id: " + e.getMessage());
        }
        return null;
    }

    /**
     * Находит видео по URL ссылке.
     *
     * @param videoUrl полная ссылка на видео
     * @return VideoStats или null, если не найдено
     */
    public VideoStats findByUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return null;
        }

        String sql = """
            SELECT v.*, 
                   COALESCE(y.id_youtube, vk.id_vk) as platform_video_id
            FROM videos v
            LEFT JOIN youtube y ON v.link = y.video_link
            LEFT JOIN vk ON v.link = vk.video_link
            WHERE v.link = ?
        """;

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                VideoStats stats = mapResultSetToVideoStats(rs);
                stats.setPlatformVideoId(rs.getString("platform_video_id"));
                return stats;
            }

        } catch (SQLException e) {
            Logger.error("Ошибка поиска: " + e.getMessage());
        }
        return null;
    }

    /**
     * Возвращает список всех видео, отсортированный по ID (новые сверху).
     *
     * @return список VideoStats (может быть пустым, но не null)
     */
    public List<VideoStats> findAll() {
        List<VideoStats> list = new ArrayList<>();
        String sql = """
            SELECT v.*, 
                   COALESCE(y.id_youtube, vk.id_vk) as platform_video_id
            FROM videos v
            LEFT JOIN youtube y ON v.link = y.video_link
            LEFT JOIN vk ON v.link = vk.video_link
            ORDER BY v.id DESC
        """;

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                VideoStats stats = mapResultSetToVideoStats(rs);
                stats.setPlatformVideoId(rs.getString("platform_video_id"));
                list.add(stats);
            }

        } catch (SQLException e) {
            Logger.error("Ошибка получения списка: " + e.getMessage());
        }
        return list;
    }

    /**
     * Возвращает топ N видео по количеству просмотров.
     * Используется для отображения "Топ-5 популярных видео".
     *
     * @param limit количество видео в топе (обычно 5)
     * @return список VideoStats, отсортированный по убыванию просмотров
     */
    public List<VideoStats> findTopByViews(int limit) {
        List<VideoStats> list = new ArrayList<>();
        String sql = """
            SELECT v.*, 
                   COALESCE(y.id_youtube, vk.id_vk) as platform_video_id
            FROM videos v
            LEFT JOIN youtube y ON v.link = y.video_link
            LEFT JOIN vk ON v.link = vk.video_link
            ORDER BY v.views_count DESC LIMIT ?
        """;

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                VideoStats stats = mapResultSetToVideoStats(rs);
                stats.setPlatformVideoId(rs.getString("platform_video_id"));
                list.add(stats);
            }

        } catch (SQLException e) {
            Logger.error("Ошибка получения топа: " + e.getMessage());
        }
        return list;
    }

    /**
     * Возвращает статистику: сколько видео на каждой платформе.
     *
     * @return Map<Платформа, Количество>
     */
    public Map<String, Integer> getPlatformCount() {
        Map<String, Integer> result = new HashMap<>();
        String sql = "SELECT platform, COUNT(*) FROM videos GROUP BY platform";

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                result.put(rs.getString(1), rs.getInt(2));
            }

        } catch (SQLException e) {
            Logger.error("Ошибка подсчёта по платформам: " + e.getMessage());
        }
        return result;
    }

    /**
     * Возвращает статистику: суммарное количество просмотров по платформам.
     *
     * @return Map<Платформа, Суммарные просмотры>
     */
    public Map<String, Long> getPlatformViews() {
        Map<String, Long> result = new HashMap<>();
        String sql = "SELECT platform, SUM(views_count) FROM videos GROUP BY platform";

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                result.put(rs.getString(1), rs.getLong(2));
            }

        } catch (SQLException e) {
            Logger.error("Ошибка подсчёта просмотров: " + e.getMessage());
        }
        return result;
    }

    /**
     * Возвращает динамику роста просмотров за последнюю неделю.
     *
     * Алгоритм:
     * 1. Для каждого видео находим самую старую запись в истории за последние 7 дней
     * 2. Сравниваем с текущими просмотрами
     * 3. Вычисляем процент роста
     * 4. Возвращаем топ-10 видео с наибольшим ростом
     *
     * @return список GrowthData с информацией о росте
     */
    public List<GrowthData> getWeeklyGrowth() {
        List<GrowthData> result = new ArrayList<>();
        String sql = """
            SELECT v.title, 
                   h1.views_count as old_views,
                   v.views_count as new_views,
                   ((v.views_count - h1.views_count) * 100.0 / h1.views_count) as growth
            FROM videos v
            JOIN views_history h1 ON v.id = h1.video_id
            WHERE h1.recorded_at >= NOW() - INTERVAL '7 days'
            AND h1.recorded_at = (
                SELECT MIN(h2.recorded_at) 
                FROM views_history h2 
                WHERE h2.video_id = v.id 
                AND h2.recorded_at >= NOW() - INTERVAL '7 days'
            )
            AND v.views_count > h1.views_count
            ORDER BY growth DESC
            LIMIT 10
        """;

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                GrowthData g = new GrowthData();
                g.setTitle(rs.getString("title"));
                g.setOldViews(rs.getLong("old_views"));
                g.setNewViews(rs.getLong("new_views"));
                g.setGrowthPercent(rs.getDouble("growth"));
                result.add(g);
            }

        } catch (SQLException e) {
            Logger.error("Ошибка получения динамики: " + e.getMessage());
        }
        return result;
    }

    /**
     * Возвращает суммарное количество просмотров всех видео.
     *
     * @return общее количество просмотров (0 если видео нет)
     */
    public long getTotalViews() {
        String sql = "SELECT COALESCE(SUM(views_count), 0) as total FROM videos";

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

    /**
     * Возвращает общее количество добавленных ссылок (видео).
     *
     * @return количество видео в базе
     */
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

    /**
     * Удаляет видео из базы данных по URL.
     *
     * @param videoUrl полная ссылка на видео
     */
    public void deleteByUrl(String videoUrl) {
        String sql = "DELETE FROM videos WHERE link = ?";

        try (Connection conn = DbConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, videoUrl);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            Logger.error("Ошибка удаления: " + e.getMessage());
        }
    }

    /**
     * Преобразует ResultSet в объект VideoStats.
     * Используется во всех SELECT методах для унификации.
     *
     * @param rs ResultSet из запроса
     * @return заполненный объект VideoStats
     * @throws SQLException при ошибках чтения из ResultSet
     */
    private VideoStats mapResultSetToVideoStats(ResultSet rs) throws SQLException {
        VideoStats stats = new VideoStats();
        stats.setId(rs.getLong("id"));
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

    // =============================================
    // МЕТОДЫ ДЛЯ ТАБЛИЦЫ youtube
    // =============================================

    /**
     * Сохраняет или обновляет YouTube ID для видео.
     * ID нужен для batch-запросов к YouTube API (экономия запросов).
     *
     * @param videoUrl ссылка на видео
     * @param youtubeId 11-символьный ID видео на YouTube
     */
    public void saveYouTubeId(String videoUrl, String youtubeId) {
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

        } catch (SQLException e) {
            Logger.error("Ошибка сохранения YouTube ID: " + e.getMessage());
        }
    }

    /**
     * Находит YouTube ID по ссылке на видео.
     *
     * @param videoUrl ссылка на видео
     * @return YouTube ID или null, если не найден
     */
    public String findYouTubeIdByUrl(String videoUrl) {
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

    /**
     * Сохраняет или обновляет VK ID для видео.
     * ID нужен для batch-запросов к VK API.
     *
     * @param videoUrl ссылка на видео
     * @param vkId VK ID видео (формат: ownerId_videoId)
     * @param vkExternalId внешний ID (опционально)
     */
    public void saveVkId(String videoUrl, String vkId, String vkExternalId) {
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

        } catch (SQLException e) {
            Logger.error("Ошибка сохранения VK ID: " + e.getMessage());
        }
    }

    /**
     * Находит VK ID по ссылке на видео.
     *
     * @param videoUrl ссылка на видео
     * @return VK ID или null, если не найден
     */
    public String findVkIdByUrl(String videoUrl) {
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

    // =============================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (парсинг ID из URL)
    // =============================================

    /**
     * Извлекает YouTube Video ID из ссылки.
     * Поддерживает форматы:
     * - https://youtu.be/VIDEO_ID
     * - https://youtube.com/watch?v=VIDEO_ID
     * - https://www.youtube.com/watch?v=VIDEO_ID
     *
     * @param videoUrl полная ссылка на видео
     * @return 11-символьный ID или null
     */
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

    /**
     * Извлекает VK Video ID из ссылки.
     * Поддерживает форматы:
     * - https://vk.com/video-111905078_456248997
     * - https://vkvideo.ru/video-111905078_456248997
     * - https://vk.com/clip123456_789
     *
     * @param videoUrl полная ссылка на видео
     * @return VK ID в формате "ownerId_videoId" или null
     */
    private String extractVkId(String videoUrl) {
        if (videoUrl == null) return null;

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

    // =============================================
    // ВНУТРЕННИЙ КЛАСС ДЛЯ ДАННЫХ РОСТА
    // =============================================

    /**
     * Вспомогательный класс для хранения данных о росте просмотров.
     * Используется в методе getWeeklyGrowth().
     */
    public static class GrowthData {
        private String title;          // Название видео
        private long oldViews;         // Просмотры неделю назад
        private long newViews;         // Текущие просмотры
        private double growthPercent;  // Процент роста

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public long getOldViews() { return oldViews; }
        public void setOldViews(long oldViews) { this.oldViews = oldViews; }
        public long getNewViews() { return newViews; }
        public void setNewViews(long newViews) { this.newViews = newViews; }
        public double getGrowthPercent() { return growthPercent; }
        public void setGrowthPercent(double growthPercent) { this.growthPercent = growthPercent; }
    }
}