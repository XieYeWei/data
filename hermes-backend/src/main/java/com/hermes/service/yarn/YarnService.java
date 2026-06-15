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
            log.warn("Failed to get cluster metrics", e);
            return Map.of("error", e.getMessage());
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
            log.warn("Failed to get queues", e);
            return Collections.emptyList();
        }
    }

    // ... other methods remain similar with try-catch for robustness ...

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