package com.hermes.service;

import com.hermes.config.HadoopConfig;
import com.hermes.entity.MetricSnapshot;
import com.hermes.mapper.MetricSnapshotMapper;
import com.hermes.service.hdfs.HdfsService;
import com.hermes.service.yarn.YarnService;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Scheduled task to collect cluster metrics and persist to DB (metric_snapshot table).
 * Runs every 5 minutes by default.
 */
@Service
public class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired
    private HdfsService hdfsService;

    @Autowired
    private YarnService yarnService;

    @Autowired
    private MetricSnapshotMapper metricSnapshotMapper;

    /**
     * Collect metrics for default cluster every 5 minutes.
     * In production: loop over all enabled clusters from Cluster table.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void collectMetrics() {
        String clusterId = "cluster1"; // TODO: load from DB
        try {
            // HDFS metrics
            FileSystem fs = hadoopConfig.getFileSystem(clusterId);
            ContentSummary summary = fs.getContentSummary(new Path("/"));

            MetricSnapshot hdfsSnap = new MetricSnapshot();
            hdfsSnap.setClusterId(1L);
            hdfsSnap.setModule("hdfs");
            hdfsSnap.setUsedSpace(summary.getLength());
            hdfsSnap.setFileCount(summary.getFileCount());
            metricSnapshotMapper.insert(hdfsSnap);

            // YARN metrics
            var yarnMetrics = yarnService.getClusterMetrics(clusterId);
            MetricSnapshot yarnSnap = new MetricSnapshot();
            yarnSnap.setClusterId(1L);
            yarnSnap.setModule("yarn");
            yarnSnap.setNumNodeManagers(((Number) yarnMetrics.getOrDefault("numNodeManagers", 0)).intValue());
            yarnSnap.setTotalMemoryMB(((Number) yarnMetrics.getOrDefault("totalMemoryMB", 0)).longValue());
            yarnSnap.setRunningApplications(((Number) yarnMetrics.getOrDefault("runningApplications", 0)).intValue());
            metricSnapshotMapper.insert(yarnSnap);

            log.info("Metrics snapshot collected for cluster {}", clusterId);
        } catch (IOException | YarnException e) {
            log.error("Failed to collect metrics for cluster {}", clusterId, e);
        }
    }
}