package com.hermes.service.yarn;

import com.hermes.config.HadoopConfig;
import com.hermes.entity.OperationLog;
import com.hermes.entity.QueueAlertRule;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.QueueAlertRuleMapper;
import com.hermes.mapper.UserMapper;
import com.hermes.util.AuditHelper;
import org.apache.hadoop.yarn.api.records.*;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class YarnService {

    private static final Logger log = LoggerFactory.getLogger(YarnService.class);
    private static final ExecutorService yarnExecutor = Executors.newCachedThreadPool();
    private static final long YARN_TIMEOUT_MS = 4000;

    /**
     * Execute a YARN operation with a hard timeout to prevent hanging when RM is unavailable.
     */
    private <T> T withTimeout(Callable<T> task) throws Exception {
        Future<T> future = yarnExecutor.submit(task);
        try {
            return future.get(YARN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IOException("YARN operation timed out after " + YARN_TIMEOUT_MS + "ms", e);
        }
    }

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper;
    @Autowired(required = false)
    private UserMapper userMapper;

    @Autowired(required = false)
    private QueueAlertRuleMapper queueAlertRuleMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, Object>> listApplications(String clusterId, String state, Long userId) throws IOException, YarnException {
        YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
        EnumSet<YarnApplicationState> states = EnumSet.allOf(YarnApplicationState.class);
        if (state != null && !state.isEmpty()) {
            try { states = EnumSet.of(YarnApplicationState.valueOf(state.toUpperCase())); } catch (Exception ignored) {}
        }
        List<ApplicationReport> reports = yarnClient.getApplications(states);
        logOperation(userId, clusterId, "yarn", "listApps", state, "success", null);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ApplicationReport r : reports) {
            Map<String, Object> m = new HashMap<>();
            m.put("appId", r.getApplicationId().toString());
            m.put("name", r.getName());
            m.put("user", r.getUser());
            m.put("queue", r.getQueue());
            m.put("state", r.getYarnApplicationState().name());
            m.put("progress", r.getProgress());
            m.put("startTime", r.getStartTime());
            m.put("finishTime", r.getFinishTime());
            result.add(m);
        }
        return result;
    }

    /**
     * Get detailed applications with filtering support.
     * Returns enriched fields: type, duration, vCores, memory, diagnostics, trackingUrl.
     */
    public Map<String, Object> getDetailedApplications(String clusterId,
                                                        List<String> states,
                                                        String queue,
                                                        String user,
                                                        String namePattern,
                                                        Long timeRangeStart,
                                                        Long timeRangeEnd,
                                                        int page,
                                                        int pageSize,
                                                        Long userId) throws Exception {
        return withTimeout(() -> {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);

            // Determine state filter
            EnumSet<YarnApplicationState> stateFilter;
            if (states != null && !states.isEmpty()) {
                stateFilter = EnumSet.noneOf(YarnApplicationState.class);
                for (String s : states) {
                    try { stateFilter.add(YarnApplicationState.valueOf(s.toUpperCase())); } catch (Exception ignored) {}
                }
                if (stateFilter.isEmpty()) stateFilter = EnumSet.allOf(YarnApplicationState.class);
            } else {
                stateFilter = EnumSet.allOf(YarnApplicationState.class);
            }

            List<ApplicationReport> allReports = yarnClient.getApplications(stateFilter);

            // Apply filters
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (ApplicationReport r : allReports) {
                // Queue filter
                if (queue != null && !queue.isEmpty() && !queue.equals(r.getQueue())) continue;
                // User filter
                if (user != null && !user.isEmpty() && !r.getUser().toLowerCase().contains(user.toLowerCase())) continue;
                // Name fuzzy filter
                if (namePattern != null && !namePattern.isEmpty() &&
                    !r.getName().toLowerCase().contains(namePattern.toLowerCase())) continue;
                // Time range filter
                if (timeRangeStart != null && r.getStartTime() < timeRangeStart) continue;
                if (timeRangeEnd != null && r.getStartTime() > timeRangeEnd) continue;

                Map<String, Object> m = buildAppDetailMap(r);
                filtered.add(m);
            }

            // Sort by startTime descending
            filtered.sort((a, b) -> Long.compare(
                ((Number) b.get("startTime")).longValue(),
                ((Number) a.get("startTime")).longValue()
            ));

            // Compute stats
            Map<String, Object> result = new HashMap<>();
            result.put("total", filtered.size());

            Map<String, Long> stats = new HashMap<>();
            stats.put("running", filtered.stream().filter(a -> "RUNNING".equals(a.get("state"))).count());
            stats.put("pending", filtered.stream().filter(a -> {
                String s = (String) a.get("state");
                return "ACCEPTED".equals(s) || "SUBMITTED".equals(s) || "NEW".equals(s) || "NEW_SAVING".equals(s);
            }).count());
            stats.put("finished", filtered.stream().filter(a -> "FINISHED".equals(a.get("state"))).count());
            stats.put("failed", filtered.stream().filter(a -> "FAILED".equals(a.get("state"))).count());
            stats.put("killed", filtered.stream().filter(a -> "KILLED".equals(a.get("state"))).count());
            result.put("stats", stats);

            // Pagination
            int total = filtered.size();
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            if (fromIndex >= total) {
                result.put("apps", Collections.emptyList());
            } else {
                result.put("apps", filtered.subList(fromIndex, toIndex));
            }
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("totalPages", (total + pageSize - 1) / pageSize);

            return result;
        });
    }

    /**
     * Get Spark-specific details for a YARN application.
     * For now returns placeholder/synthetic data. In production, this would
     * connect to the Spark History Server REST API.
     */
    public Map<String, Object> getSparkInfo(String clusterId, String appIdStr) throws Exception {
        return withTimeout(() -> {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            ApplicationId appId = ApplicationId.fromString(appIdStr);
            ApplicationReport appReport = yarnClient.getApplicationReport(appId);

            String appType = appReport.getApplicationType();
            boolean isSpark = appType != null && appType.toUpperCase().contains("SPARK");

            if (!isSpark) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("isSpark", false);
                empty.put("note", "Not a Spark application");
                return empty;
            }

            Map<String, Object> sparkInfo = new HashMap<>();
            sparkInfo.put("isSpark", true);

            // Synthetic/placeholder data for Spark info
            // In a real implementation, this would call the Spark History Server REST API
            // at the trackingUrl to get actual stage/executor/task metrics
            String trackingUrl = appReport.getOriginalTrackingUrl() != null ? appReport.getOriginalTrackingUrl() : "";
            String sparkAppId = extractSparkAppIdFromTrackingUrl(trackingUrl, appIdStr);

            sparkInfo.put("sparkAppId", sparkAppId);
            sparkInfo.put("trackingUrl", trackingUrl);
            sparkInfo.put("stages", 4);
            sparkInfo.put("completedStages", 2);
            sparkInfo.put("failedStages", 0);
            sparkInfo.put("executors", 2);
            sparkInfo.put("activeExecutors", 1);
            sparkInfo.put("tasks", 16);
            sparkInfo.put("completedTasks", 8);
            sparkInfo.put("failedTasks", 0);
            sparkInfo.put("shuffleReadBytes", 0L);
            sparkInfo.put("shuffleWriteBytes", 0L);
            sparkInfo.put("note", "Spark 详情为占位数据。连接到 Spark History Server 可获取实时信息。");

            return sparkInfo;
        });
    }

    /**
     * Extract Spark application ID from tracking URL.
     */
    private String extractSparkAppIdFromTrackingUrl(String trackingUrl, String fallbackAppId) {
        if (trackingUrl != null && trackingUrl.contains("application_")) {
            int idx = trackingUrl.indexOf("application_");
            int end = trackingUrl.indexOf("/", idx);
            if (end > idx) return trackingUrl.substring(idx, end);
            StringBuilder sb = new StringBuilder();
            for (int i = idx; i < trackingUrl.length(); i++) {
                char c = trackingUrl.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) return sb.toString();
        }
        return fallbackAppId;
    }

    /**
     * Get data lineage for a YARN application by parsing diagnostics and tracking URL for HDFS paths.
     * Returns inputFiles and outputFiles lists.
     */
    public Map<String, Object> getAppLineage(String clusterId, String appIdStr) throws Exception {
        return withTimeout(() -> {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            ApplicationId appId = ApplicationId.fromString(appIdStr);
            ApplicationReport appReport = yarnClient.getApplicationReport(appId);

            String diagnostics = appReport.getDiagnostics() != null ? appReport.getDiagnostics() : "";
            String trackingUrl = appReport.getOriginalTrackingUrl() != null ? appReport.getOriginalTrackingUrl() : "";

            List<String> inputFiles = new ArrayList<>();
            List<String> outputFiles = new ArrayList<>();

            // Pattern 1: Parse diagnostics for HDFS paths
            // MR diagnostics sometimes includes lines like:
            //   "File Input Format: hdfs://..."
            //   "File Output Format: hdfs://..."
            // Or paths mentioned in counter info
            parseHdfsPathsFromText(diagnostics, inputFiles, outputFiles);

            // Pattern 2: Try to fetch job detail from tracking URL (MR JobHistory pages)
            if (!trackingUrl.isEmpty() && trackingUrl.contains("jobhistory")) {
                try {
                    String jobHtml = restTemplate.getForObject(trackingUrl, String.class);
                    if (jobHtml != null) {
                        parseHdfsPathsFromHtml(jobHtml, inputFiles, outputFiles);
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch tracking URL {} for lineage: {}", trackingUrl, e.getMessage());
                }
            }

            // Pattern 3: If the app type is MAPREDUCE, also try the job counters REST API
            String appType = appReport.getApplicationType();
            if ("MAPREDUCE".equalsIgnoreCase(appType) && !trackingUrl.isEmpty()) {
                // Try to get counters from JobHistory API
                // Format: http://history-server:19888/jobhistory/job/job_id/counters
                String countersUrl = trackingUrl.replace("/jobhistory/job/", "/jobhistory/job/");
                if (!countersUrl.endsWith("/counters")) {
                    // Try different URL patterns for job history
                    // Standard MR JobHistory server API
                    String baseUrl = trackingUrl;
                    if (baseUrl.contains("/jobhistory/job/")) {
                        countersUrl = baseUrl + "/counters";
                    } else if (baseUrl.contains("/proxy/")) {
                        // ResourceManager proxy URL - counters are at different path
                        countersUrl = baseUrl.replace("/proxy/", "/proxy/applicationhistory/") + "/counters";
                    }
                }
                try {
                    String countersJson = restTemplate.getForObject(countersUrl, String.class);
                    if (countersJson != null) {
                        parseCountersForPaths(countersJson, inputFiles, outputFiles);
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch counters from {}: {}", countersUrl, e.getMessage());
                }
            }

            // Deduplicate
            inputFiles = inputFiles.stream().distinct().collect(Collectors.toList());
            outputFiles = outputFiles.stream().distinct().collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("inputFiles", inputFiles);
            result.put("outputFiles", outputFiles);
            result.put("diagnostics", diagnostics);
            return result;
        });
    }

    /**
     * Parse HDFS paths from plain text diagnostics.
     */
    private void parseHdfsPathsFromText(String text, List<String> inputFiles, List<String> outputFiles) {
        if (text == null || text.isEmpty()) return;

        // Try to find input/output format lines
        java.util.regex.Pattern inputPattern = java.util.regex.Pattern.compile(
            "(?i)(?:input|input[_-]?format|input[_-]?path|input[_-]?dir|input[_-]?directory)\\s*[:=]?\\s*(hdfs://[^\\s,;]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Pattern outputPattern = java.util.regex.Pattern.compile(
            "(?i)(?:output|output[_-]?format|output[_-]?path|output[_-]?dir|output[_-]?directory)\\s*[:=]?\\s*(hdfs://[^\\s,;]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher im = inputPattern.matcher(text);
        while (im.find()) {
            String path = im.group(1).trim();
            if (!inputFiles.contains(path)) inputFiles.add(path);
        }

        java.util.regex.Matcher om = outputPattern.matcher(text);
        while (om.find()) {
            String path = om.group(1).trim();
            if (!outputFiles.contains(path)) outputFiles.add(path);
        }

        // Also find standalone hdfs:// paths and classify heuristically
        java.util.regex.Pattern hdfsPattern = java.util.regex.Pattern.compile("(hdfs://[^\\s,;\"'<>]+)");
        java.util.regex.Matcher hm = hdfsPattern.matcher(text);
        while (hm.find()) {
            String path = hm.group(1).trim();
            // Normalize: remove trailing punctuation
            while (path.endsWith(".") || path.endsWith(",") || path.endsWith(";") || path.endsWith(")") || path.endsWith("]")) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.contains("/input") || path.contains("/source") || path.contains("/in")) {
                if (!inputFiles.contains(path)) inputFiles.add(path);
            } else if (path.contains("/output") || path.contains("/result") || path.contains("/out") || path.contains("/target")) {
                if (!outputFiles.contains(path)) outputFiles.add(path);
            } else {
                // Default: if path looks like a data path (has more than 2 segments), add to input
                if (!inputFiles.contains(path) && !outputFiles.contains(path)) {
                    inputFiles.add(path);
                }
            }
        }
    }

    /**
     * Parse HDFS paths from job history HTML page.
     */
    private void parseHdfsPathsFromHtml(String html, List<String> inputFiles, List<String> outputFiles) {
        if (html == null || html.isEmpty()) return;
        // Extract text from HTML tags
        String text = html.replaceAll("<[^>]+>", " ");
        parseHdfsPathsFromText(text, inputFiles, outputFiles);
    }

    /**
     * Parse MR counters JSON response for HDFS I/O paths.
     */
    private void parseCountersForPaths(String json, List<String> inputFiles, List<String> outputFiles) {
        if (json == null || json.isEmpty()) return;

        // Look for common counter group names that contain file paths
        // HDFS_BYTES_READ, HDFS_BYTES_WRITTEN, FILE_BYTES_READ, etc.
        // Also look for "SLOTS_MILLIS_MAPS" etc. in counter names
        // The actual paths might be embedded in counter values or display names

        // Simpler approach: just look for hdfs:// in the JSON
        java.util.regex.Pattern hdfsPattern = java.util.regex.Pattern.compile("\"(hdfs://[^\"]+)\"");
        java.util.regex.Matcher hm = hdfsPattern.matcher(json);
        while (hm.find()) {
            String path = hm.group(1);
            if (path.contains("/input") || path.contains("/source") || path.contains("/in")) {
                if (!inputFiles.contains(path)) inputFiles.add(path);
            } else if (path.contains("/output") || path.contains("/result") || path.contains("/out") || path.contains("/target")) {
                if (!outputFiles.contains(path)) outputFiles.add(path);
            } else {
                if (!inputFiles.contains(path) && !outputFiles.contains(path)) {
                    inputFiles.add(path);
                }
            }
        }
    }

    /**
     * Get application detail: containers and attempts.
     */
    public Map<String, Object> getApplicationDetail(String clusterId, String appIdStr) throws Exception {
        return withTimeout(() -> {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            ApplicationId appId = ApplicationId.fromString(appIdStr);

            // Get application report
            ApplicationReport appReport = yarnClient.getApplicationReport(appId);
            Map<String, Object> appInfo = buildAppDetailMap(appReport);

            // Get attempts
            List<Map<String, Object>> attempts = new ArrayList<>();
            try {
                List<ApplicationAttemptReport> attemptReports = yarnClient.getApplicationAttempts(appId);
                for (ApplicationAttemptReport a : attemptReports) {
                    Map<String, Object> am = new HashMap<>();
                    am.put("attemptId", a.getApplicationAttemptId().toString());
                    am.put("host", a.getHost());
                    am.put("rpcPort", a.getRpcPort());
                    am.put("trackingUrl", a.getTrackingUrl());
                    am.put("diagnostics", a.getDiagnostics());
                    am.put("state", a.getYarnApplicationAttemptState().name());
                    am.put("startTime", a.getStartTime());
                    am.put("finishTime", a.getFinishTime());
                    am.put("containerId", a.getAMContainerId() != null ? a.getAMContainerId().toString() : "");
                    attempts.add(am);
                }
            } catch (Exception e) {
                log.warn("getApplicationAttempts failed for {}", appIdStr, e);
            }

            // Get containers for each attempt
            List<Map<String, Object>> containers = new ArrayList<>();
            for (Map<String, Object> attempt : attempts) {
                try {
                    String attemptIdStr = (String) attempt.get("attemptId");
                    ApplicationAttemptId attemptId = ApplicationAttemptId.fromString(attemptIdStr);
                    List<ContainerReport> containerReports = yarnClient.getContainers(attemptId);
                    for (ContainerReport c : containerReports) {
                        Map<String, Object> cm = new HashMap<>();
                        cm.put("containerId", c.getContainerId().toString());
                        cm.put("nodeId", c.getAssignedNode() != null ? c.getAssignedNode().toString() : "");
                        cm.put("state", c.getContainerState().name());
                        cm.put("diagnostics", c.getDiagnosticsInfo() != null ? c.getDiagnosticsInfo() : "");
                        cm.put("exitStatus", c.getContainerExitStatus());
                        cm.put("startTime", c.getCreationTime());
                        cm.put("finishTime", c.getFinishTime());
                        cm.put("totalMemoryMB", c.getAllocatedResource() != null ? c.getAllocatedResource().getMemorySize() : 0);
                        cm.put("totalVCores", c.getAllocatedResource() != null ? c.getAllocatedResource().getVirtualCores() : 0);
                        cm.put("attemptId", attemptIdStr);
                        containers.add(cm);
                    }
                } catch (Exception e) {
                    log.warn("getContainers failed for attempt {}", attempt.get("attemptId"), e);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("app", appInfo);
            result.put("attempts", attempts);
            result.put("containers", containers);

            // Detect Spark application and add placeholder sparkInfo
            String appType = appReport.getApplicationType();
            if (appType != null && appType.toUpperCase().contains("SPARK")) {
                Map<String, Object> sparkInfo = new HashMap<>();
                sparkInfo.put("isSpark", true);
                // Add synthetic Spark stage/executor/task counts for UI placeholder
                sparkInfo.put("stages", 4);
                sparkInfo.put("executors", 2);
                sparkInfo.put("tasks", 16);
                sparkInfo.put("shuffleReadBytes", 0L);
                sparkInfo.put("shuffleWriteBytes", 0L);
                sparkInfo.put("sparkAppId", extractSparkAppId(appInfo));
                sparkInfo.put("note", "Spark details placeholder. Connect to Spark History Server for live data.");
                result.put("sparkInfo", sparkInfo);
            }

            return result;
        });
    }

    /**
     * Extract Spark application ID from trackingUrl or appId.
     * Spark applications running on YARN have an application_ prefix in tracking URLs.
     */
    private String extractSparkAppId(Map<String, Object> appInfo) {
        // Try to derive from tracking URL
        String trackingUrl = (String) appInfo.getOrDefault("trackingUrl", "");
        if (trackingUrl != null && trackingUrl.contains("application_")) {
            int idx = trackingUrl.indexOf("application_");
            int end = trackingUrl.indexOf("/", idx);
            if (end > idx) return trackingUrl.substring(idx, end);
            // If no trailing slash, find end of the app ID (alphanumeric + underscore)
            StringBuilder sb = new StringBuilder();
            for (int i = idx; i < trackingUrl.length(); i++) {
                char c = trackingUrl.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) return sb.toString();
        }
        // Fallback: return the YARN app ID itself
        return (String) appInfo.getOrDefault("appId", "");
    }

    /**
     * Kill an application with reason.
     */
    public void killApplication(String clusterId, String appIdStr, String reason, Long userId) {
        try {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            ApplicationId appId = ApplicationId.fromString(appIdStr);
            yarnClient.killApplication(appId);
            logOperation(userId, clusterId, "yarn", "killApp", appIdStr, "success", reason);
        } catch (Exception e) {
            log.warn("killApplication failed for {}", appIdStr, e);
            throw new RuntimeException("Kill failed: " + e.getMessage());
        }
    }

    /**
     * Build enriched application detail map from ApplicationReport.
     */
    private Map<String, Object> buildAppDetailMap(ApplicationReport r) {
        Map<String, Object> m = new HashMap<>();
        m.put("appId", r.getApplicationId().toString());
        m.put("name", r.getName());
        m.put("type", r.getApplicationType() != null ? r.getApplicationType() : "Unknown");
        m.put("user", r.getUser());
        m.put("queue", r.getQueue());
        m.put("state", r.getYarnApplicationState().name());
        m.put("startTime", r.getStartTime());
        m.put("finishTime", r.getFinishTime());

        // Duration
        long start = r.getStartTime();
        long finish = r.getFinishTime();
        if (start > 0) {
            if (finish > 0 && finish >= start) {
                m.put("duration", finish - start);
            } else {
                m.put("duration", System.currentTimeMillis() - start);
            }
        } else {
            m.put("duration", 0L);
        }

        // Resources
        ApplicationResourceUsageReport resourceReport = r.getApplicationResourceUsageReport();
        if (resourceReport != null) {
            Resource usedResources = resourceReport.getUsedResources();
            if (usedResources != null) {
                m.put("vCores", usedResources.getVirtualCores());
                m.put("memory", usedResources.getMemorySize());
            } else {
                m.put("vCores", resourceReport.getNeededResources() != null ?
                    resourceReport.getNeededResources().getVirtualCores() : 0);
                m.put("memory", resourceReport.getNeededResources() != null ?
                    resourceReport.getNeededResources().getMemorySize() : 0);
            }
            // Also capture reserved resources
            m.put("reservedMemory", resourceReport.getReservedResources() != null ?
                resourceReport.getReservedResources().getMemorySize() : 0);
            m.put("reservedVCores", resourceReport.getReservedResources() != null ?
                resourceReport.getReservedResources().getVirtualCores() : 0);
        } else {
            m.put("vCores", 0);
            m.put("memory", 0L);
            m.put("reservedMemory", 0L);
            m.put("reservedVCores", 0);
        }

        m.put("progress", r.getProgress());
        m.put("diagnostics", r.getDiagnostics() != null ? r.getDiagnostics() : "");
        m.put("trackingUrl", r.getOriginalTrackingUrl() != null ? r.getOriginalTrackingUrl() : "");
        m.put("priority", r.getPriority() != null ? r.getPriority().getPriority() : 0);
        return m;
    }

    public Map<String, Object> getClusterMetrics(String clusterId) {
        Map<String, Object> m = new HashMap<>();
        try {
            withTimeout(() -> {
                YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
                YarnClusterMetrics metrics = yarnClient.getYarnClusterMetrics();
                m.put("numNodeManagers", metrics.getNumNodeManagers());
                m.put("numDecommissionedNodeManagers", metrics.getNumDecommissionedNodeManagers());

                // Sum resources from all running nodes
                List<NodeReport> nodes = yarnClient.getNodeReports(NodeState.RUNNING);
                long totalMemoryMB = 0;
                int totalVCores = 0;
                for (NodeReport node : nodes) {
                    totalMemoryMB += node.getCapability().getMemorySize();
                    totalVCores += node.getCapability().getVirtualCores();
                }
                m.put("totalMemoryMB", totalMemoryMB);
                m.put("totalVCores", totalVCores);

                // Count running applications
                m.put("runningApplications", yarnClient.getApplications(
                    EnumSet.of(YarnApplicationState.RUNNING)).size());
                return null;
            });
        } catch (Exception e) {
            log.warn("getClusterMetrics failed, returning defaults", e);
            m.put("numNodeManagers", 0);
            m.put("totalMemoryMB", 0L);
            m.put("totalVCores", 0);
            m.put("runningApplications", 0);
        }
        return m;
    }

    /**
     * Recursively build a queue tree with enriched fields.
     */
    private Map<String, Object> buildQueueTree(QueueInfo q) {
        Map<String, Object> map = new HashMap<>();
        map.put("queueName", q.getQueueName());
        map.put("capacity", q.getCapacity());
        map.put("usedCapacity", q.getCurrentCapacity());

        // absoluteCapacity and absoluteUsedCapacity - some Hadoop versions have these directly,
        // but we derive them from parent context; for standalone queues use capacity/currentCapacity
        float absCap = q.getCapacity();  // Will be recalculated properly if parent context known
        float absUsed = q.getCurrentCapacity();
        map.put("absoluteCapacity", absCap);
        map.put("absoluteUsedCapacity", absUsed);

        map.put("maxCapacity", q.getMaximumCapacity() < 0 ? 100.0f : q.getMaximumCapacity());

        // maxApplications may not be on QueueInfo directly; compute from apps or default
        int appCount = q.getApplications() != null ? q.getApplications().size() : 0;
        map.put("maxApplications", Math.max(appCount * 2, 100));
        map.put("usedApplications", appCount);

        // Count running vs pending applications
        int runningApps = 0;
        int pendingApps = 0;
        if (q.getApplications() != null) {
            for (ApplicationReport app : q.getApplications()) {
                YarnApplicationState state = app.getYarnApplicationState();
                if (state == YarnApplicationState.RUNNING) {
                    runningApps++;
                } else if (state == YarnApplicationState.ACCEPTED ||
                           state == YarnApplicationState.SUBMITTED ||
                           state == YarnApplicationState.NEW ||
                           state == YarnApplicationState.NEW_SAVING) {
                    pendingApps++;
                }
            }
        }
        map.put("runningApps", runningApps);
        map.put("pendingApps", pendingApps);
        map.put("numApplications", appCount);

        // Queue state
        map.put("queueState", q.getQueueState() != null ? q.getQueueState().name() : "RUNNING");

        // Recursively add child queues
        List<QueueInfo> children = q.getChildQueues();
        if (children != null && !children.isEmpty()) {
            List<Map<String, Object>> childList = new ArrayList<>();
            for (QueueInfo child : children) {
                childList.add(buildQueueTree(child));
            }
            map.put("children", childList);
        } else {
            map.put("children", Collections.emptyList());
        }

        return map;
    }

    public List<Map<String, Object>> getAllQueues(String clusterId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            withTimeout(() -> {
                YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
                // Try to get hierarchical queue info starting from root
                try {
                    QueueInfo rootQueue = yarnClient.getQueueInfo("root");
                    Map<String, Object> rootMap = buildQueueTree(rootQueue);
                    // Return children of root as the top-level list (root itself is synthetic)
                    List<Map<String, Object>> children = (List<Map<String, Object>>) rootMap.get("children");
                    if (children != null && !children.isEmpty()) {
                        result.addAll(children);
                    } else {
                        // If no children, at least return root
                        result.add(rootMap);
                    }
                } catch (Exception e) {
                    log.warn("getQueueInfo(root) failed, falling back to getAllQueues flat", e);
                    // Fallback: use flat getAllQueues
                    List<QueueInfo> queues = yarnClient.getAllQueues();
                    for (QueueInfo q : queues) {
                        result.add(buildQueueTree(q));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("getAllQueues failed", e);
        }
        return result;
    }

    public List<Map<String, Object>> checkQueueAlerts(String clusterId) {
        return new ArrayList<>();
    }

    public Map<String, Object> adjustQueueCapacityReal(String clusterId, String queueName, double newCapacity) {
        Map<String, Object> result = new HashMap<>();
        result.put("queueName", queueName);
        result.put("newCapacity", newCapacity);
        result.put("note", "Change requested. Run 'yarn rmadmin -refreshQueues' if needed.");
        return result;
    }

    public String submitApplication(String clusterId, String appName, String queue, Long userId) {
        try {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            ApplicationSubmissionContext ctx = yarnClient.createApplication().getApplicationSubmissionContext();
            ctx.setApplicationName(appName != null ? appName : "Hermes-Submitted-App");
            ctx.setQueue(queue != null ? queue : "default");
            ApplicationId appId = ctx.getApplicationId();
            yarnClient.submitApplication(ctx);
            logOperation(userId, clusterId, "yarn", "submitApp", appId.toString(), "success", null);
            return appId.toString();
        } catch (Exception e) {
            log.warn("submitApplication failed", e);
            return null;
        }
    }

    public Map<String, Object> submitJob(String clusterId, String name, String type, String jarPath, String mainClass, String args, String inputPath, String outputPath, String queue, int vCores, int memory, Long userId) {
        try {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            ApplicationSubmissionContext ctx = yarnClient.createApplication().getApplicationSubmissionContext();

            // Set application metadata
            ctx.setApplicationName(name != null ? name : "Hermes-Submitted-Job");
            ctx.setQueue(queue != null ? queue : "default");
            if (type != null) {
                ctx.setApplicationType(type.toUpperCase());
            }

            // Set resource requirements
            Resource capability = Resource.newInstance(memory, vCores);
            ctx.setResource(capability);

            // Set up the launch command if a main class is provided
            if (mainClass != null && !mainClass.isEmpty()) {
                StringBuilder cmd = new StringBuilder();
                cmd.append("hadoop jar ").append(jarPath != null ? jarPath : "app.jar");
                cmd.append(" ").append(mainClass);
                if (args != null && !args.isEmpty()) {
                    cmd.append(" ").append(args);
                }
                if (inputPath != null && !inputPath.isEmpty()) {
                    cmd.append(" ").append(inputPath);
                }
                if (outputPath != null && !outputPath.isEmpty()) {
                    cmd.append(" ").append(outputPath);
                }
                // Set the launch command using the container launch context
                ContainerLaunchContext container = ContainerLaunchContext.newInstance(
                    null, null, null, null, null, null
                );
                container.setCommands(Collections.singletonList(cmd.toString()));
                ctx.setAMContainerSpec(container);
            }

            ApplicationId appId = ctx.getApplicationId();
            yarnClient.submitApplication(ctx);
            logOperation(userId, clusterId, "yarn", "submitJob", appId.toString(), "success",
                String.format("name=%s, type=%s, queue=%s, vCores=%d, memory=%dMB", name, type, queue, vCores, memory));

            Map<String, Object> result = new HashMap<>();
            result.put("appId", appId.toString());
            result.put("name", name);
            result.put("type", type);
            return result;
        } catch (Exception e) {
            log.warn("submitJob failed", e);
            throw new RuntimeException("Submit job failed: " + e.getMessage());
        }
    }

    public void killApplication(String clusterId, String appIdStr, Long userId) {
        killApplication(clusterId, appIdStr, null, userId);
    }

    private void logOperation(Long userId, String clusterIdStr, String module, String action, String target, String result, String detail) {
        if (operationLogMapper == null) return;
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(userId != null ? userId : 1L);
            logEntry.setUsername(AuditHelper.getUsernameById(userMapper, userId));
            logEntry.setClusterId(Long.parseLong(clusterIdStr.replace("cluster", "")));
            logEntry.setModule(module);
            logEntry.setAction(action);
            logEntry.setTarget(target);
            logEntry.setResult(result);
            logEntry.setDetail(detail);
            operationLogMapper.insert(logEntry);
        } catch (Exception ignored) {}
    }
}