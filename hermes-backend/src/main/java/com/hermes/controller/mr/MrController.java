package com.hermes.controller.mr;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hermes.entity.JobTemplate;
import com.hermes.entity.OperationLog;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.UserMapper;
import com.hermes.service.mr.MrService;
import com.hermes.util.AuditHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/mr")
public class MrController {

    @Autowired
    private MrService mrService;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/templates")
    public Map<String, Object> listTemplates(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String useCountOp,
            @RequestParam(required = false) Integer useCountVal,
            @RequestParam(required = false) String name) {
        List<JobTemplate> all = mrService.getAllTemplates();

        // Client-side filter for flexibility with mixed conditions
        java.util.stream.Stream<JobTemplate> stream = all.stream();
        if (type != null && !type.isEmpty()) {
            stream = stream.filter(t -> type.equals(t.getType()));
        }
        if (id != null) {
            stream = stream.filter(t -> id.equals(t.getId()));
        }
        if (queue != null && !queue.isEmpty()) {
            stream = stream.filter(t -> queue.equals(t.getQueue()));
        }
        if (useCountOp != null && useCountVal != null) {
            int val = useCountVal;
            if (">".equals(useCountOp)) stream = stream.filter(t -> (t.getUseCount() != null ? t.getUseCount() : 0) > val);
            else if (">=".equals(useCountOp)) stream = stream.filter(t -> (t.getUseCount() != null ? t.getUseCount() : 0) >= val);
            else if ("=".equals(useCountOp)) stream = stream.filter(t -> (t.getUseCount() != null ? t.getUseCount() : 0) == val);
            else if ("<=".equals(useCountOp)) stream = stream.filter(t -> (t.getUseCount() != null ? t.getUseCount() : 0) <= val);
            else if ("<".equals(useCountOp)) stream = stream.filter(t -> (t.getUseCount() != null ? t.getUseCount() : 0) < val);
        }
        if (name != null && !name.isEmpty()) {
            String kw = name.toLowerCase();
            stream = stream.filter(t -> t.getName() != null && t.getName().toLowerCase().contains(kw));
        }
        return Map.of("code", 0, "data", stream.collect(java.util.stream.Collectors.toList()));
    }

    @PostMapping("/templates")
    public Map<String, Object> saveTemplate(@RequestBody JobTemplate template) {
        JobTemplate saved = mrService.saveTemplate(template);
        AuditHelper.log(operationLogMapper, userMapper, "mr", "create-template",
            "模板名=" + saved.getName(), "类别=" + (saved.getType() != null ? saved.getType() : ""));
        return Map.of("code", 0, "data", saved);
    }

    @PutMapping("/templates/{id}")
    public Map<String, Object> updateTemplate(@PathVariable Long id, @RequestBody JobTemplate template) {
        template.setId(id);
        JobTemplate updated = mrService.updateTemplate(template);
        AuditHelper.log(operationLogMapper, userMapper, "mr", "update-template",
            "模板名=" + updated.getName(), "类别=" + (updated.getType() != null ? updated.getType() : ""));
        return Map.of("code", 0, "data", updated);
    }

    @DeleteMapping("/templates/{id}")
    public Map<String, Object> deleteTemplate(@PathVariable Long id) {
        JobTemplate t = mrService.getTemplate(id);
        mrService.deleteTemplate(id);
        AuditHelper.log(operationLogMapper, userMapper, "mr", "delete-template",
            "模板名=" + (t != null ? t.getName() : "id=" + id), "已删除");
        return Map.of("code", 0, "msg", "模板已删除");
    }

    @GetMapping("/templates/{id}")
    public Map<String, Object> getTemplate(@PathVariable Long id) {
        return Map.of("code", 0, "data", mrService.getTemplate(id));
    }

    @PostMapping("/submit-from-template")
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