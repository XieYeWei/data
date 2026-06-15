package com.hermes.controller.yarn;

import com.hermes.entity.QueueAlertRule;
import com.hermes.mapper.QueueAlertRuleMapper;
import com.hermes.service.yarn.YarnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/yarn")
public class YarnController {

    @Autowired
    private YarnService yarnService;

    @Autowired(required = false)
    private QueueAlertRuleMapper queueAlertRuleMapper;

    @GetMapping("/apps")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public Map<String, Object> listApps(@RequestParam(defaultValue = "cluster1") String clusterId, @RequestParam(required = false) String state) {
        try { return success(yarnService.listApplications(clusterId, state, 1L)); } catch (Exception e) { return error(500, e.getMessage()); }
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
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adjustQueueWeight(@RequestParam String queueName, @RequestParam double newCapacity, @RequestParam(defaultValue = "cluster1") String clusterId) {
        try { return success(yarnService.adjustQueueCapacityReal(clusterId, queueName, newCapacity)); } catch (Exception e) { return error(500, e.getMessage()); }
    }

    /**
     * Alert Rule Full CRUD
     */
    @GetMapping("/alert-rules")
    public Map<String, Object> listAlertRules() {
        if (queueAlertRuleMapper == null) return success(Collections.emptyList());
        return success(queueAlertRuleMapper.selectList(null));
    }

    @PostMapping("/alert-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> createAlertRule(@RequestBody QueueAlertRule rule) {
        if (queueAlertRuleMapper == null) return error(500, "Database not available");
        queueAlertRuleMapper.insert(rule);
        return success(rule);
    }

    @DeleteMapping("/alert-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> deleteAlertRule(@PathVariable Long id) {
        if (queueAlertRuleMapper == null) return error(500, "Database not available");
        queueAlertRuleMapper.deleteById(id);
        return success("Rule deleted");
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> submit(@RequestParam(defaultValue = "cluster1") String clusterId, @RequestParam String appName, @RequestParam(defaultValue = "default") String queue) {
        try { return success(Map.of("appId", yarnService.submitApplication(clusterId, appName, queue, 1L))); } catch (Exception e) { return error(500, e.getMessage()); }
    }

    @PostMapping("/kill")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> kill(@RequestParam(defaultValue = "cluster1") String clusterId, @RequestParam String appId) {
        try { yarnService.killApplication(clusterId, appId, 1L); return success("Killed " + appId); } catch (Exception e) { return error(500, e.getMessage()); }
    }

    private Map<String, Object> success(Object data) { Map<String, Object> r = new HashMap<>(); r.put("code", 0); r.put("msg", "success"); r.put("data", data); return r; }
    private Map<String, Object> error(int code, String msg) { Map<String, Object> r = new HashMap<>(); r.put("code", code); r.put("msg", msg); r.put("data", null); return r; }
}