package com.hermes.service;

import com.hermes.config.HadoopConfig;
import com.hermes.entity.MetricSnapshot;
import com.hermes.mapper.MetricSnapshotMapper;
import com.hermes.service.yarn.YarnService;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired
    private YarnService yarnService;

    @Autowired
    private MetricSnapshotMapper metricSnapshotMapper;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("MetricsCollector initialized, triggering first collection...");
        try {
            collectMetrics();
        } catch (Exception e) {
            log.warn("Initial metrics collection failed (will retry on schedule): {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60000)
    public void collectMetrics() {
        String clusterId = "cluster1";
        try {
            // HDFS
            FileSystem fs = hadoopConfig.getFileSystem(clusterId);
            ContentSummary summary = fs.getContentSummary(new Path("/"));
            MetricSnapshot hdfsSnap = new MetricSnapshot();
            hdfsSnap.setClusterId(1L);
            hdfsSnap.setModule("hdfs");
            hdfsSnap.setUsedSpace(summary.getLength());
            hdfsSnap.setFileCount(summary.getFileCount());
            hdfsSnap.setCreateTime(java.time.LocalDateTime.now());
            metricSnapshotMapper.insert(hdfsSnap);

            // YARN Cluster
            Map<String, Object> yarnMetrics = yarnService.getClusterMetrics(clusterId);
            MetricSnapshot yarnSnap = new MetricSnapshot();
            yarnSnap.setClusterId(1L);
            yarnSnap.setModule("yarn");
            yarnSnap.setNumNodeManagers(((Number) yarnMetrics.getOrDefault("numNodeManagers", 0)).intValue());
            yarnSnap.setTotalMemoryMB(((Number) yarnMetrics.getOrDefault("totalMemoryMB", 0)).longValue());
            yarnSnap.setRunningApplications(((Number) yarnMetrics.getOrDefault("runningApplications", 0)).intValue());
            yarnSnap.setCreateTime(java.time.LocalDateTime.now());
            metricSnapshotMapper.insert(yarnSnap);

            // Optimized: Per-queue history persistence
            List<Map<String, Object>> queues = yarnService.getAllQueues(clusterId);
            for (Map<String, Object> q : queues) {
                MetricSnapshot qSnap = new MetricSnapshot();
                qSnap.setClusterId(1L);
                qSnap.setModule("queue");
                qSnap.setUsedSpace(((Number) q.getOrDefault("usedMemoryMB", 0)).longValue());
                qSnap.setExtraJson(String.format("{\"queueName\":\"%s\",\"usedCapacity\":%.1f,\"numApplications\":%d}",
                        q.get("queueName"), q.get("usedCapacity"), q.get("numApplications")));
                qSnap.setCreateTime(java.time.LocalDateTime.now());
                metricSnapshotMapper.insert(qSnap);
            }

            log.info("Queue history metrics collected successfully");
        } catch (Exception e) {
            log.error("Metrics collection error", e);
        }
    }
}