package com.hermes.controller.mr;

import com.hermes.entity.JobTemplate;
import com.hermes.service.mr.MrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/mr")
public class MrController {

    @Autowired
    private MrService mrService;

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> saveTemplate(@RequestBody JobTemplate template) {
        JobTemplate saved = mrService.saveTemplate(template);
        return Map.of("code", 0, "data", saved);
    }

    @GetMapping("/templates/{id}")
    public Map<String, Object> getTemplate(@PathVariable Long id) {
        return Map.of("code", 0, "data", mrService.getTemplate(id));
    }

    @PostMapping("/submit-from-template")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public Map<String, Object> submitFromTemplate(
            @RequestParam(defaultValue = "cluster1") String clusterId,
            @RequestParam Long templateId) {
        try {
            String appId = mrService.submitJobFromTemplate(clusterId, templateId, 1L);
            return Map.of("code", 0, "data", Map.of("appId", appId));
        } catch (Exception e) {
            return Map.of("code", 500, "msg", e.getMessage());
        }
    }
}