package com.project.bot;

import com.project.repository.VideoRepository;
import com.project.utils.FormatUtils;

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
        sb.append("• Всего просмотров: ").append(FormatUtils.formatViews(totalViews)).append("\n\n");

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
                        .append(FormatUtils.formatViews(views))
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
                sb.append(rank++).append(". <b>").append(FormatUtils.escapeHtml(video.getTitle()))
                        .append("</b>\n   👁️ ").append(FormatUtils.formatViews(video.getViewCount()))
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
                long diff = g.getNewViews() - g.getOldViews();
                String arrow = diff >= 0 ? "📈 +" : "📉 ";
                double percent = g.getGrowthPercent();
                String diffFormatted = FormatUtils.formatViews(Math.abs(diff));
                String sign = diff >= 0 ? "+" : "-";

                sb.append("• <b>").append(FormatUtils.escapeHtml(g.getTitle())).append("</b>\n")
                        .append("  ").append(arrow).append(String.format("%.1f%%", Math.abs(percent)))
                        .append(" (").append(sign).append(diffFormatted).append(") | ")
                        .append(FormatUtils.formatViews(g.getOldViews()))
                        .append(" → ").append(FormatUtils.formatViews(g.getNewViews())).append("\n\n");
            }
        }

        return sb.toString();
    }
}