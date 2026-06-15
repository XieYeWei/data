package com.hermes.controller.yarn;

import com.hermes.service.yarn.YarnService;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/yarn")
public class YarnController {

    @Autowired
    private YarnService yarnService;

    @GetMapping("/apps")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public Map<String, Object> listApps(
            @RequestParam(defaultValue = "cluster1") String clusterId,
            @RequestParam(required = false) String state) {
        try {
            List<Map<String, Object>> apps = yarnService.listApplications(clusterId, state);
            return success(apps);
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics(@RequestParam(defaultValue = "cluster1") String clusterId) {
        try {
            Map<String, Object> metrics = yarnService.getClusterMetrics(clusterId);
            return success(metrics);
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> submit(@RequestParam(defaultValue = "cluster1") String clusterId,
                                      @RequestParam String appName,
                                      @RequestParam(defaultValue = "default") String queue) {
        try {
            String appId = yarnService.submitApplication(clusterId, appName, queue);
            return success(Map.of("appId", appId));
        } catch (Exception e) {
            return error(500, "Submit failed: " + e.getMessage());
        }
    }

    @PostMapping("/kill")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> kill(@RequestParam(defaultValue = "cluster1") String clusterId,
                                    @RequestParam String appId) {
        try {
            yarnService.killApplication(clusterId, appId);
            return success("Killed " + appId);
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> r = new HashMap<>();
        r.put("code", 0);
        r.put("msg", "success");
        r.put("data", data);
        return r;
    }

    private Map<String, Object> error(int code, String msg) {
        Map<String, Object> r = new HashMap<>();
        r.put("code", code);
        r.put("msg", msg);
        r.put("data", null);
        return r;
    }
}