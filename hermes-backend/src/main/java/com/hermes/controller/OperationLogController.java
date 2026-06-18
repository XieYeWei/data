package com.hermes.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hermes.entity.ActionLabel;
import com.hermes.entity.OperationLog;
import com.hermes.entity.User;
import com.hermes.mapper.ActionLabelMapper;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.UserMapper;
import com.hermes.util.AuditHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/logs")
public class OperationLogController {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ActionLabelMapper actionLabelMapper;

    @GetMapping("/labels")
    public Map<String, Object> listLabels() {
        List<ActionLabel> labels = actionLabelMapper.selectList(null);
        Map<String, String> map = new HashMap<>();
        for (ActionLabel l : labels) {
            map.put(l.getAction(), l.getLabel());
        }
        return Map.of("code", 0, "data", map);
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String timeRangeStart,
            @RequestParam(required = false) String timeRangeEnd,
            @RequestParam(required = false) String result) {

        QueryWrapper<OperationLog> qw = new QueryWrapper<>();
        qw.orderByDesc("create_time");

        if (module != null && !module.isEmpty()) {
            qw.eq("module", module);
        }
        if (action != null && !action.isEmpty()) {
            qw.eq("action", action);
        }
        if (userId != null) {
            qw.eq("user_id", userId);
        }
        if (timeRangeStart != null && !timeRangeStart.isEmpty()) {
            qw.ge("create_time", timeRangeStart);
        }
        if (timeRangeEnd != null && !timeRangeEnd.isEmpty()) {
            qw.le("create_time", timeRangeEnd);
        }
        if (result != null && !result.isEmpty()) {
            qw.eq("result", result);
        }

        Page<OperationLog> p = operationLogMapper.selectPage(new Page<>(page, pageSize), qw);

        // 填充用户名
        List<OperationLog> records = p.getRecords();
        List<Map<String, Object>> enrichedRecords = new ArrayList<>();
        if (records != null) {
            List<Long> userIds = records.stream()
                .map(OperationLog::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
            Map<Long, String> userMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(userIds);
                for (User u : users) {
                    userMap.put(u.getId(), u.getUsername());
                }
            }
            for (OperationLog log : records) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", log.getId());
                map.put("userId", log.getUserId());
                // 优先使用写入时冗余存储的用户名，回退到从user表查询
                String storedUsername = log.getUsername();
                String displayName;
                if (storedUsername != null && !storedUsername.isEmpty()) {
                    displayName = storedUsername;
                    // 如果用户名有值但当前user表查不到（用户已删除），追加标记
                    if (log.getUserId() != null && log.getUserId() > 0 && !userMap.containsKey(log.getUserId())) {
                        displayName += "(已删除)";
                    }
                } else {
                    displayName = log.getUserId() != null && userMap.containsKey(log.getUserId())
                        ? userMap.get(log.getUserId()) : "";
                }
                map.put("username", displayName);
                map.put("clusterId", log.getClusterId());
                map.put("module", log.getModule());
                map.put("action", log.getAction());
                map.put("target", log.getTarget());
                map.put("result", log.getResult());
                map.put("detail", log.getDetail());
                map.put("createTime", log.getCreateTime());
                enrichedRecords.add(map);
            }
        }

        return Map.of("code", 0, "data", Map.of("records", enrichedRecords, "total", p.getTotal()));
    }

    // Delete single operation log record
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteById(@PathVariable Long id) {
        int affected = operationLogMapper.deleteById(id);
        if (affected > 0) {
            return Map.of("code", 0, "msg", "success");
        }
        return Map.of("code", 1, "msg", "记录不存在或删除失败");
    }

    // Batch delete operation log records
    @DeleteMapping("/batch")
    public Map<String, Object> deleteBatch(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("code", 1, "msg", "请选择要删除的记录");
        }
        int affected = operationLogMapper.deleteBatchIds(ids);
        return Map.of("code", 0, "msg", "已删除 " + affected + " 条记录");
    }

    // Generic audit log endpoint for frontend-only operations (CSV export, log download, etc.)
    @PostMapping("/audit")
    public Map<String, Object> audit(@RequestBody Map<String, String> body) {
        try {
            String module = body.getOrDefault("module", "system");
            String action = body.getOrDefault("action", "unknown");
            String target = body.getOrDefault("target", "");
            String detail = body.getOrDefault("detail", "");
            AuditHelper.log(operationLogMapper, userMapper, module, action, target, detail);
            return Map.of("code", 0, "msg", "success");
        } catch (Exception e) {
            return Map.of("code", 0, "msg", "logged");
        }
    }
}
