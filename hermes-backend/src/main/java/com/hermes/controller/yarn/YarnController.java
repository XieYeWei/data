package com.hermes.controller.yarn;

import com.hermes.entity.OperationLog;
import com.hermes.entity.QueueAlertRule;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.QueueAlertRuleMapper;
import com.hermes.mapper.UserMapper;
import com.hermes.service.yarn.YarnService;
import com.hermes.util.AuditHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/yarn")
public class YarnController {

    @Autowired
    private YarnService yarnService;

    @Autowired(required = false)
    private QueueAlertRuleMapper queueAlertRuleMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private UserMapper userMapper;

    private void logOp(String action, String target, String detail) {
        AuditHelper.log(operationLogMapper, userMapper, "yarn", action, target, detail);
    }

    @GetMapping("/apps")
    public Map<String, Object> listApps(@RequestParam(defaultValue = "cluster1") String clusterId,
                                        @RequestParam(required = false) List<String> state,
                                        @RequestParam(required = false) String queue,
                                        @RequestParam(required = false) String user,
                                        @RequestParam(required = false) String name,
                                        @RequestParam(required = false) Long timeRangeStart,
                                        @RequestParam(required = false) Long timeRangeEnd,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "50") int pageSize) {
        try {
            return success(yarnService.getDetailedApplications(
                clusterId, state, queue, user, name,
                timeRangeStart, timeRangeEnd, page, pageSize, 1L));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @GetMapping("/app/{id}/lineage")
    public Map<String, Object> getAppLineage(@PathVariable String id,
                                              @RequestParam(defaultValue = "cluster1") String clusterId) {
        try {
            return success(yarnService.getAppLineage(clusterId, id));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @GetMapping("/app/{id}/details")
    public Map<String, Object> getAppDetails(@PathVariable String id,
                                              @RequestParam(defaultValue = "cluster1") String clusterId) {
        try {
            return success(yarnService.getApplicationDetail(clusterId, id));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @GetMapping("/app/{id}/spark")
    public Map<String, Object> getAppSparkInfo(@PathVariable String id,
                                                @RequestParam(defaultValue = "cluster1") String clusterId) {
        try {
            return success(yarnService.getSparkInfo(clusterId, id));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics(@RequestParam(defaultValue = "cluster1") String clusterId) {
        try { return success(yarnService.getClusterMetrics(clusterId)); } catch (Exception e) { return error(500, e.getMessage()); }
    }

    @GetMapping("/queues")
    public Map<String, Object> getQueues(@RequestParam(defaultValue = "cluster1") String clusterId) {
        try { return success(yarnService.getAllQueues(clusterId)); } catch (Exception e) { return error(500, e.getMessage()); }
    }

    @GetMapping("/check-alerts")
    public Map<String, Object> checkAlerts(@RequestParam(defaultValue = "cluster1") String clusterId) {
        try { return success(yarnService.checkQueueAlerts(clusterId)); } catch (Exception e) { return error(500, e.getMessage()); }
    }

    @PostMapping("/queues/adjust-weight")
    public Map<String, Object> adjustQueueWeight(@RequestParam String queueName, @RequestParam double newCapacity, @RequestParam(defaultValue = "cluster1") String clusterId) {
        try {
            Map<String, Object> r = yarnService.adjustQueueCapacityReal(clusterId, queueName, newCapacity);
            logOp("adjust-weight", "队列=" + queueName, "新容量=" + newCapacity);
            return success(r);
        } catch (Exception e) { return error(500, e.getMessage()); }
    }

    /**
     * Alert Rule CRUD
     */
    @GetMapping("/alert-rules")
    public Map<String, Object> listAlertRules() {
        if (queueAlertRuleMapper == null) return success(Collections.emptyList());
        return success(queueAlertRuleMapper.selectList(null));
    }

    @PostMapping("/alert-rules")
    public Map<String, Object> createAlertRule(@RequestBody QueueAlertRule rule) {
        if (queueAlertRuleMapper == null) return error(500, "Database not available");
        queueAlertRuleMapper.insert(rule);
        logOp("create-alert", "队列=" + rule.getQueueName(), "指标=" + rule.getMetric() + " 阈值=" + rule.getThreshold());
        return success(rule);
    }

    @PutMapping("/alert-rules/{id}")
    public Map<String, Object> updateAlertRule(@PathVariable Long id, @RequestBody QueueAlertRule rule) {
        if (queueAlertRuleMapper == null) return error(500, "Database not available");
        rule.setId(id);
        queueAlertRuleMapper.updateById(rule);
        logOp("update-alert", "规则ID=" + id, "指标=" + rule.getMetric() + " 阈值=" + rule.getThreshold());
        return success(rule);
    }

    @DeleteMapping("/alert-rules/{id}")
    public Map<String, Object> deleteAlertRule(@PathVariable Long id) {
        if (queueAlertRuleMapper == null) return error(500, "Database not available");
        QueueAlertRule old = queueAlertRuleMapper.selectById(id);
        queueAlertRuleMapper.deleteById(id);
        logOp("delete-alert", "规则ID=" + id, (old != null ? "队列=" + old.getQueueName() : ""));
        return success("Rule deleted");
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestParam(defaultValue = "cluster1") String clusterId, @RequestParam String appName, @RequestParam(defaultValue = "default") String queue) {
        try { return success(Map.of("appId", yarnService.submitApplication(clusterId, appName, queue, 1L))); } catch (Exception e) { return error(500, e.getMessage()); }
    }

    @PostMapping("/submit-job")
    public Map<String, Object> submitJob(@RequestBody Map<String, Object> body) {
        try {
            String clusterId = (String) body.getOrDefault("clusterId", "cluster1");
            String name = (String) body.get("name");
            String type = (String) body.get("type");
            String jarPath = (String) body.get("jarPath");
            String mainClass = (String) body.get("mainClass");
            String args = (String) body.get("args");
            String inputPath = (String) body.get("inputPath");
            String outputPath = (String) body.get("outputPath");
            String queue = (String) body.getOrDefault("queue", "default");
            int vCores = body.containsKey("vCores") ? ((Number) body.get("vCores")).intValue() : 1;
            int memory = body.containsKey("memory") ? ((Number) body.get("memory")).intValue() : 1024;
            Map<String, Object> result = yarnService.submitJob(clusterId, name, type, jarPath, mainClass, args, inputPath, outputPath, queue, vCores, memory, 1L);
            return success(result);
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @PostMapping("/kill")
    public Map<String, Object> kill(@RequestParam(defaultValue = "cluster1") String clusterId,
                                    @RequestParam String appId,
                                    @RequestParam(required = false) String reason) {
        try {
            yarnService.killApplication(clusterId, appId, reason, 1L);
            return success("Killed " + appId);
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    private Map<String, Object> success(Object data) { Map<String, Object> r = new HashMap<>(); r.put("code", 0); r.put("msg", "success"); r.put("data", data); return r; }
    private Map<String, Object> error(int code, String msg) { Map<String, Object> r = new HashMap<>(); r.put("code", code); r.put("msg", msg); r.put("data", null); return r; }
}