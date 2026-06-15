package com.hermes.service.yarn;

import com.hermes.config.HadoopConfig;
import com.hermes.entity.OperationLog;
import com.hermes.mapper.OperationLogMapper;
import org.apache.hadoop.yarn.api.records.*;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class YarnService {

    private static final Logger log = LoggerFactory.getLogger(YarnService.class);

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper;

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
            m.put("memoryMB", r.getApplicationResourceUsageReport().getUsedResources().getMemorySize());
            m.put("vcores", r.getApplicationResourceUsageReport().getUsedResources().getVirtualCores());
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