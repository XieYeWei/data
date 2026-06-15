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

    public Map<String, Object> getClusterMetrics(String clusterId) throws IOException, YarnException {
        YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
        YarnClusterMetrics metrics = yarnClient.getClusterMetrics();
        Map<String, Object> m = new HashMap<>();
        m.put("numNodeManagers", metrics.getNumNodeManagers());
        m.put("numDecommissionedNodeManagers", metrics.getNumDecommissionedNodeManagers());
        m.put("totalMemoryMB", metrics.getTotalMemoryMB());
        m.put("totalVCores", metrics.getTotalVCores());
        m.put("runningApplications", metrics.getNumRunningApplications());
        return m;
    }

    public List<Map<String, Object>> getAllQueues(String clusterId) throws IOException, YarnException {
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
    }

    public List<Map<String, Object>> checkQueueAlerts(String clusterId) throws IOException, YarnException {
        List<QueueAlertRule> rules = (queueAlertRuleMapper != null) ? 
            queueAlertRuleMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<QueueAlertRule>().eq("enabled", true)) : 
            Collections.emptyList();

        List<Map<String, Object>> currentQueues = getAllQueues(clusterId);
        List<Map<String, Object>> triggeredAlerts = new ArrayList<>();

        for (Map<String, Object> q : currentQueues) {
            String queueName = (String) q.get("queueName");
            double usedCapacity = ((Number) q.getOrDefault("usedCapacity", 0)).doubleValue();
            int numApps = ((Number) q.getOrDefault("numApplications", 0)).intValue();

            for (QueueAlertRule rule : rules) {
                if (!rule.getQueueName().equals(queueName) && !"*".equals(rule.getQueueName())) continue;

                boolean triggered = false;
                String currentValue = "";

                if ("usedCapacity".equals(rule.getMetric())) {
                    currentValue = String.format("%.1f", usedCapacity) + "%";
                    if (">".equals(rule.getOperator()) && usedCapacity > rule.getThreshold()) triggered = true;
                    if (">=".equals(rule.getOperator()) && usedCapacity >= rule.getThreshold()) triggered = true;
                } else if ("numApplications".equals(rule.getMetric())) {
                    currentValue = String.valueOf(numApps);
                    if (">".equals(rule.getOperator()) && numApps > rule.getThreshold()) triggered = true;
                }

                if (triggered) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("queueName", queueName);
                    alert.put("metric", rule.getMetric());
                    alert.put("currentValue", currentValue);
                    alert.put("threshold", rule.getThreshold());
                    alert.put("operator", rule.getOperator());
                    triggeredAlerts.add(alert);
                }
            }
        }
        return triggeredAlerts;
    }

    public Map<String, Object> adjustQueueCapacityReal(String clusterId, String queueName, double newCapacity) {
        try {
            String rmAddress = "your-rm-host:8088";
            String url = String.format("http://%s/ws/v1/cluster/scheduler/queue/%s", rmAddress, queueName);

            Map<String, Object> payload = new HashMap<>();
            payload.put("capacity", newCapacity);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            Map<String, Object> result = new HashMap<>();
            result.put("queueName", queueName);
            result.put("newCapacity", newCapacity);
            result.put("status", response.getStatusCode().toString());
            return result;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("suggestion", "Run 'yarn rmadmin -refreshQueues' after modifying capacity-scheduler.xml");
            return error;
        }
    }

    public String submitApplication(String clusterId, String appName, String queue, Long userId) throws IOException, YarnException {
        YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
        ApplicationSubmissionContext appContext = yarnClient.createApplication().getApplicationSubmissionContext();
        appContext.setApplicationName(appName != null ? appName : "Hermes-Submitted-App");
        appContext.setQueue(queue != null ? queue : "default");
        ApplicationId appId = appContext.getApplicationId();
        yarnClient.submitApplication(appContext);
        logOperation(userId, clusterId, "yarn", "submitApp", appId.toString(), "success", null);
        return appId.toString();
    }

    public void killApplication(String clusterId, String appIdStr, Long userId) throws IOException, YarnException {
        YarnClient yarnClient = hadoopConfig.getYarnClient(clusterId);
        ApplicationId appId = ApplicationId.fromString(appIdStr);
        yarnClient.killApplication(appId);
        logOperation(userId, clusterId, "yarn", "killApp", appIdStr, "success", null);
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
        } catch (Exception e) {
            log.warn("Failed to write audit log", e);
        }
    }
}