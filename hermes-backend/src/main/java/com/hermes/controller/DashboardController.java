package com.hermes.controller;

import com.hermes.config.HadoopConfig;
import com.hermes.service.hdfs.HdfsService;
import com.hermes.service.yarn.YarnService;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired
    private HdfsService hdfsService;

    @Autowired
    private YarnService yarnService;

    /**
     * Unified cluster overview for visualization (ECharts ready)
     */
    @GetMapping("/overview")
    public Map<String, Object> getOverview(@RequestParam(defaultValue = "cluster1") String clusterId) {
        Map<String, Object> overview = new HashMap<>();
        try {
            // HDFS capacity
            FileSystem fs = hadoopConfig.getFileSystem(clusterId);
            ContentSummary summary = fs.getContentSummary(new Path("/"));
            Map<String, Object> hdfs = new HashMap<>();
            hdfs.put("totalSpace", summary.getSpaceQuota() > 0 ? summary.getSpaceQuota() : summary.getLength() * 10); // approx
            hdfs.put("usedSpace", summary.getLength());
            hdfs.put("fileCount", summary.getFileCount());
            hdfs.put("dirCount", summary.getDirectoryCount());
            overview.put("hdfs", hdfs);

            // YARN metrics
            Map<String, Object> yarn = yarnService.getClusterMetrics(clusterId);
            overview.put("yarn", yarn);

            overview.put("clusterId", clusterId);
            overview.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            overview.put("error", e.getMessage());
        }
        return Map.of("code", 0, "data", overview);
    }
}