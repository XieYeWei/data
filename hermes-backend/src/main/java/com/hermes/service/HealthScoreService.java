package com.hermes.service;

import com.hermes.entity.MetricSnapshot;
import com.hermes.mapper.HealthScoreHistoryMapper;
import com.hermes.entity.HealthScoreHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class HealthScoreService {

    private static final Logger log = LoggerFactory.getLogger(HealthScoreService.class);

    @Autowired
    private com.hermes.mapper.MetricSnapshotMapper metricSnapshotMapper;

    @Autowired
    private HealthScoreHistoryMapper healthScoreHistoryMapper;

    /**
     * 每小时计算一次当天最新的健康评分并写入 health_score_history 表
     */
    @Scheduled(fixedRate = 3600000) // 每小时
    public void computeDailyHealthScore() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(LocalTime.MAX);

        try {
            // 取今天最新的 HDFS 快照
            var hdfsQuery = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetricSnapshot>()
                    .eq("module", "hdfs")
                    .ge("create_time", dayStart)
                    .le("create_time", dayEnd)
                    .orderByDesc("create_time")
                    .last("LIMIT 1");
            List<MetricSnapshot> hdfsList = metricSnapshotMapper.selectList(hdfsQuery);

            // 取今天最新的 YARN 快照
            var yarnQuery = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetricSnapshot>()
                    .eq("module", "yarn")
                    .ge("create_time", dayStart)
                    .le("create_time", dayEnd)
                    .orderByDesc("create_time")
                    .last("LIMIT 1");
            List<MetricSnapshot> yarnList = metricSnapshotMapper.selectList(yarnQuery);

            // 计算健康评分
            int score = 92; // 基础分
            Long hdfsUsed = null;
            Long hdfsTotal = null;
            Integer nmCount = null;
            Integer appCount = null;

            if (!hdfsList.isEmpty()) {
                MetricSnapshot h = hdfsList.get(0);
                hdfsUsed = h.getUsedSpace();
                hdfsTotal = hdfsUsed != null ? hdfsUsed * 10 : 0;
                double usagePct = hdfsTotal > 0 ? (hdfsUsed * 100.0 / hdfsTotal) : 0;
                if (usagePct > 80) score -= 25;
                else if (usagePct > 60) score -= 10;
                else if (usagePct > 40) score -= 3;
                if (usagePct < 20) score += 3;
            }

            if (!yarnList.isEmpty()) {
                MetricSnapshot y = yarnList.get(0);
                nmCount = y.getNumNodeManagers();
                appCount = y.getRunningApplications();
                if (nmCount == null || nmCount == 0) score -= 20;
                if (appCount != null && appCount == 0) score -= 3;
            }

            if (hdfsList.isEmpty() && yarnList.isEmpty()) {
                score = 75;
            }

            score = Math.max(0, Math.min(100, score));

            // 写入 health_score_history 表（upsert 语义：删除今日旧记录后插入）
            HealthScoreHistory existing = healthScoreHistoryMapper.selectByDate(today);
            if (existing != null) {
                existing.setScore(score);
                existing.setHdfsUsed(hdfsUsed);
                existing.setHdfsTotal(hdfsTotal);
                existing.setNmCount(nmCount);
                existing.setAppCount(appCount);
                existing.setCreateTime(LocalDateTime.now());
                healthScoreHistoryMapper.updateById(existing);
            } else {
                HealthScoreHistory history = new HealthScoreHistory();
                history.setScore(score);
                history.setScoreDate(today);
                history.setHdfsUsed(hdfsUsed);
                history.setHdfsTotal(hdfsTotal);
                history.setNmCount(nmCount);
                history.setAppCount(appCount);
                healthScoreHistoryMapper.insert(history);
            }

            log.info("Health score computed and stored: date={}, score={}", today, score);
        } catch (Exception e) {
            log.error("Health score computation failed", e);
        }
    }
}
