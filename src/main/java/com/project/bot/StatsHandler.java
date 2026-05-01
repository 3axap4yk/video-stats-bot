package com.project.bot;

import com.project.repository.VideoRepository;
import com.project.utils.ViewFormatter;

import java.util.Map;

public class StatsHandler {

    private final VideoRepository videoRepository;

    public StatsHandler() {
        this.videoRepository = new VideoRepository();
    }

    public String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>СТАТИСТИКА</b>\n\n");

        int totalVideos = videoRepository.getTotalLinks();
        long totalViews = videoRepository.getTotalViews();

        sb.append("━━━━━━━━━━━━━━━━━━\n");
        sb.append("📈 <b>Общая статистика:</b>\n");
        sb.append("• Всего видео: ").append(totalVideos).append("\n");
        sb.append("• Всего просмотров: ").append(ViewFormatter.formatViews(totalViews)).append("\n\n");

        Map<String, Integer> platformCount = videoRepository.getPlatformCount();
        Map<String, Long> platformViews = videoRepository.getPlatformViews();

        if (!platformCount.isEmpty()) {
            sb.append("━━━━━━━━━━━━━━━━━━\n");
            sb.append("🎯 <b>По платформам:</b>\n");
            for (Map.Entry<String, Integer> entry : platformCount.entrySet()) {
                String platform = entry.getKey();
                int count = entry.getValue();
                long views = platformViews.getOrDefault(platform, 0L);
                double percent = totalViews > 0 ? (views * 100.0 / totalViews) : 0;
                sb.append("• ").append(platform).append(": ")
                        .append(count).append(" видео, ")
                        .append(ViewFormatter.formatViews(views))
                        .append(String.format(" (%.1f%%)\n", percent));
            }
            sb.append("\n");
        }

        var topVideos = videoRepository.findTopByViews(5);
        if (!topVideos.isEmpty()) {
            sb.append("━━━━━━━━━━━━━━━━━━\n");
            sb.append("🏆 <b>Топ-5 популярных видео:</b>\n");
            int rank = 1;
            for (var video : topVideos) {
                sb.append(rank++).append(". <b>").append(ViewFormatter.escapeHtml(video.getTitle()))
                        .append("</b>\n   👁️ ").append(ViewFormatter.formatViews(video.getViewCount()))
                        .append("\n\n");
            }
        }

        var growth = videoRepository.getWeeklyGrowth();
        sb.append("━━━━━━━━━━━━━━━━━━\n");
        sb.append("📈 <b>Динамика за неделю:</b>\n");
        if (growth.isEmpty()) {
            sb.append("⏳ Нет данных для расчёта динамики\n");
            sb.append("(Данные появятся через несколько дней после добавления видео)\n");
        } else {
            for (var g : growth) {
                String arrow = g.getGrowthPercent() >= 0 ? "📈 +" : "📉 ";
                sb.append("• <b>").append(ViewFormatter.escapeHtml(g.getTitle())).append("</b>\n")
                        .append("  ").append(arrow).append(String.format("%.1f", Math.abs(g.getGrowthPercent()))).append("%")
                        .append(" (").append(ViewFormatter.formatViews(g.getOldViews()))
                        .append(" → ").append(ViewFormatter.formatViews(g.getNewViews()))
                        .append(")\n\n");
            }
        }

        return sb.toString();
    }
}