package com.hermes.controller;

import com.hermes.entity.MetricSnapshot;
import com.hermes.entity.HealthScoreHistory;
import com.hermes.mapper.MetricSnapshotMapper;
import com.hermes.mapper.HealthScoreHistoryMapper;
import com.hermes.service.MetricsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {

    @Autowired
    private MetricSnapshotMapper metricSnapshotMapper;

    @Autowired
    private MetricsCollector metricsCollector;

    @Autowired
    private HealthScoreHistoryMapper healthScoreHistoryMapper;

    @PostMapping("/collect")
    public Map<String, Object> triggerCollect() {
        Map<String, Object> result = new HashMap<>();
        try {
            metricsCollector.collectMetrics();
            result.put("code", 0);
            result.put("msg", "Metrics collection triggered");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "Collection failed: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/history")
    public Map<String, Object> getHistory(@RequestParam(defaultValue = "cluster1") String clusterId,
                                           @RequestParam(defaultValue = "queue") String module,
                                           @RequestParam(required = false) String startTime,
                                           @RequestParam(required = false) String endTime,
                                           @RequestParam(defaultValue = "168") int hours,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "200") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            var query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetricSnapshot>()
                    .eq("module", module)
                    .orderByDesc("create_time");

            if ((startTime == null || startTime.isEmpty()) && hours > 0) {
                LocalDateTime start = LocalDateTime.now().minusHours(hours);
                query.ge("create_time", start);
            }

            if (startTime != null && !startTime.isEmpty()) {
                query.ge("create_time", startTime);
            }
            if (endTime != null && !endTime.isEmpty()) {
                query.le("create_time", endTime);
            }

            var countQuery = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetricSnapshot>()
                    .eq("module", module);
            if ((startTime == null || startTime.isEmpty()) && hours > 0) {
                LocalDateTime start = LocalDateTime.now().minusHours(hours);
                countQuery.ge("create_time", start);
            }
            if (startTime != null && !startTime.isEmpty()) {
                countQuery.ge("create_time", startTime);
            }
            if (endTime != null && !endTime.isEmpty()) {
                countQuery.le("create_time", endTime);
            }
            long total = metricSnapshotMapper.selectCount(countQuery);
            int offset = (page - 1) * size;
            query.last("LIMIT " + size + " OFFSET " + offset);
            List<MetricSnapshot> list = metricSnapshotMapper.selectList(query);

            result.put("code", 0);
            result.put("msg", "success");
            result.put("data", list != null ? list : Collections.emptyList());
            result.put("total", total);
            result.put("page", page);
            result.put("size", size);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "查询监控数据失败: " + e.getMessage());
            result.put("data", Collections.emptyList());
            result.put("total", 0L);
        }
        return result;
    }

    /**
     * Health score history over the last N days for the dashboard trend chart.
     * Returns one data point per day aggregated from metric_snapshot records.
     */
    @GetMapping("/health-history")
    public Map<String, Object> getHealthHistory(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> history = new ArrayList<>();

            // 优先从 health_score_history 表读取
            List<HealthScoreHistory> stored = healthScoreHistoryMapper.selectAllOrderByDateDesc();
            LocalDate today = LocalDate.now();

            for (int i = days - 1; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                // 查找当天是否有存储的评分
                HealthScoreHistory match = null;
                for (HealthScoreHistory h : stored) {
                    if (h.getScoreDate() != null && h.getScoreDate().equals(day)) {
                        match = h;
                        break;
                    }
                }

                int score = 0; // 无数据的天返回0
                if (match != null) {
                    score = match.getScore();
                } else {
                    Integer computed = computeScoreForDate(day);
                    if (computed != null) {
                        score = computed;
                    }
                }

                Map<String, Object> point = new HashMap<>();
                point.put("date", day.toString());
                point.put("score", score);
                history.add(point);
            }

            result.put("code", 0);
            result.put("msg", "success");
            result.put("data", history);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "查询健康历史失败: " + e.getMessage());
            result.put("data", Collections.emptyList());
        }
        return result;
    }

    /**
     * 根据 metric_snapshot 计算某一天的评分
     */
    private Integer computeScoreForDate(LocalDate day) {
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.atTime(LocalTime.MAX);

        var hdfsQ = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetricSnapshot>()
                .eq("module", "hdfs")
                .ge("create_time", dayStart)
                .le("create_time", dayEnd)
                .orderByDesc("create_time")
                .last("LIMIT 1");
        List<MetricSnapshot> hdfsList = metricSnapshotMapper.selectList(hdfsQ);

        var yarnQ = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetricSnapshot>()
                .eq("module", "yarn")
                .ge("create_time", dayStart)
                .le("create_time", dayEnd)
                .orderByDesc("create_time")
                .last("LIMIT 1");
        List<MetricSnapshot> yarnList = metricSnapshotMapper.selectList(yarnQ);

        int score = 92;
        if (!hdfsList.isEmpty()) {
            MetricSnapshot h = hdfsList.get(0);
            long totalSpace = h.getUsedSpace() != null ? h.getUsedSpace() * 10 : 0;
            long usedSpace = h.getUsedSpace() != null ? h.getUsedSpace() : 0;
            double usagePct = totalSpace > 0 ? (usedSpace * 100.0 / totalSpace) : 0;
            if (usagePct > 80) score -= 25;
            else if (usagePct > 60) score -= 10;
            else if (usagePct > 40) score -= 3;
            if (usagePct < 20) score += 3;
        }
        if (!yarnList.isEmpty()) {
            MetricSnapshot y = yarnList.get(0);
            if (y.getNumNodeManagers() == null || y.getNumNodeManagers() == 0) score -= 20;
            if (y.getRunningApplications() != null && y.getRunningApplications() == 0) score -= 3;
        }
        if (hdfsList.isEmpty() && yarnList.isEmpty()) {
            return null; // 无真实数据，不返回假评分
        }
        return Math.max(0, Math.min(100, score));
    }
}
