package com.hermes.service.yarn;

import com.hermes.config.HadoopConfig;
import com.hermes.entity.OperationLog;
import com.hermes.entity.QueueAlertRule;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.QueueAlertRuleMapper;
import org.apache.hadoop.yarn.api.records.*;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

@Service
public class YarnService {

    private static final Logger log = LoggerFactory.getLogger(YarnService.class);

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper;

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

    public Map<String, Object> getClusterMetrics(String clusterId) {
        try {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            YarnClusterMetrics metrics = yarnClient.getClusterMetrics();
            Map<String, Object> m = new HashMap<>();
            m.put("numNodeManagers", metrics.getNumNodeManagers());
            m.put("numDecommissionedNodeManagers", metrics.getNumDecommissionedNodeManagers());
            m.put("totalMemoryMB", metrics.getTotalMemoryMB());
            m.put("totalVCores", metrics.getTotalVCores());
            m.put("runningApplications", metrics.getNumRunningApplications());
            return m;
        } catch (Exception e) {
            log.warn("getClusterMetrics failed", e);
            return Collections.emptyMap();
        }
    }

    public List<Map<String, Object>> getAllQueues(String clusterId) {
        try {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            List<QueueInfo> queues = yarnClient.getAllQueues();
            List<Map<String, Object>> result = new ArrayList<>();
            for (QueueInfo q : queues) {
                Map<String, Object> m = new HashMap<>();
                m.put("queueName", q.getQueueName());
                m.put("capacity", q.getCapacity());
                m.put("usedCapacity", q.getUsedCapacity());
                m.put("numApplications", q.getNumApplications());
                m.put("maxCapacity", q.getMaximumCapacity());
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.warn("getAllQueues failed", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> checkQueueAlerts(String clusterId) {
        try {
            List<QueueAlertRule> rules = (queueAlertRuleMapper != null) ?
                queueAlertRuleMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<QueueAlertRule>().eq("enabled", true)) :
                Collections.emptyList();

            List<Map<String, Object>> currentQueues = getAllQueues(clusterId);
            List<Map<String, Object>> triggered = new ArrayList<>();

            for (Map<String, Object> q : currentQueues) {
                String queueName = (String) q.get("queueName");
                double usedCapacity = ((Number) q.getOrDefault("usedCapacity", 0)).doubleValue();
                int numApps = ((Number) q.getOrDefault("numApplications", 0)).intValue();

                for (QueueAlertRule rule : rules) {
                    if (!rule.getQueueName().equals(queueName) && !"*".equals(rule.getQueueName())) continue;

                    boolean triggeredAlert = false;
                    if ("usedCapacity".equals(rule.getMetric())) {
                        if (">".equals(rule.getOperator()) && usedCapacity > rule.getThreshold()) triggeredAlert = true;
                        if (">=".equals(rule.getOperator()) && usedCapacity >= rule.getThreshold()) triggeredAlert = true;
                    } else if ("numApplications".equals(rule.getMetric())) {
                        if (">".equals(rule.getOperator()) && numApps > rule.getThreshold()) triggeredAlert = true;
                    }

                    if (triggeredAlert) {
                        Map<String, Object> alert = new HashMap<>();
                        alert.put("queueName", queueName);
                        alert.put("metric", rule.getMetric());
                        alert.put("currentValue", usedCapacity + "%");
                        alert.put("threshold", rule.getThreshold());
                        alert.put("operator", rule.getOperator());
                        triggered.add(alert);
                    }
                }
            }
            return triggered;
        } catch (Exception e) {
            log.warn("checkQueueAlerts failed", e);
            return Collections.emptyList();
        }
    }

    public Map<String, Object> adjustQueueCapacityReal(String clusterId, String queueName, double newCapacity) {
        Map<String, Object> result = new HashMap<>();
        result.put("queueName", queueName);
        result.put("newCapacity", newCapacity);
        result.put("note", "Change requested. Run 'yarn rmadmin -refreshQueues' if needed.");
        logOperation(1L, clusterId, "yarn", "adjustQueue", queueName, "requested", null);
        return result;
    }

    public String submitApplication(String clusterId, String appName, String queue, Long userId) {
        try {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            ApplicationSubmissionContext ctx = yarnClient.createApplication().getApplicationSubmissionContext();
            ctx.setApplicationName(appName != null ? appName : "Hermes-App");
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

    public void killApplication(String clusterId, String appIdStr, Long userId) {
        try {
            YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
            ApplicationId appId = ApplicationId.fromString(appIdStr);
            yarnClient.killApplication(appId);
            logOperation(userId, clusterId, "yarn", "killApp", appIdStr, "success", null);
        } catch (Exception e) {
            log.warn("killApplication failed", e);
        }
    }

    private void logOperation(Long userId, String clusterIdStr, String module, String action, String target, String result, String detail) {
        if (operationLogMapper == null) return;
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(userId != null ? userId : 1L);
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