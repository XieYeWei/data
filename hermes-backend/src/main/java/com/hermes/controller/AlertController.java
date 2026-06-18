package com.hermes.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hermes.entity.OperationLog;
import com.hermes.entity.QueueAlertRule;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.QueueAlertRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    @Autowired
    private QueueAlertRuleMapper alertRuleMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    private Map<String, Object> ok(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 0);
        m.put("msg", "success");
        m.put("data", data);
        return m;
    }

    private Map<String, Object> fail(int code, String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("msg", msg);
        m.put("data", null);
        return m;
    }

    // ===== Alert Rules CRUD =====

    @GetMapping("/rules")
    public Map<String, Object> listRules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        QueryWrapper<QueueAlertRule> qw = new QueryWrapper<>();
        qw.orderByDesc("create_time");
        Page<QueueAlertRule> p = alertRuleMapper.selectPage(new Page<>(page, pageSize), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return ok(data);
    }

    @PostMapping("/rules")
    public Map<String, Object> createRule(@RequestBody QueueAlertRule rule) {
        if (rule.getQueueName() == null || rule.getQueueName().isEmpty()) {
            return fail(400, "队列名称不能为空");
        }
        if (rule.getMetric() == null || rule.getMetric().isEmpty()) {
            return fail(400, "指标不能为空");
        }
        if (rule.getOperator() == null || rule.getOperator().isEmpty()) {
            return fail(400, "操作符不能为空");
        }
        if (rule.getThreshold() == null) {
            return fail(400, "阈值不能为空");
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getNotifyEmail() == null) {
            rule.setNotifyEmail("");
        }
        alertRuleMapper.insert(rule);
        return ok(rule);
    }

    @PutMapping("/rules/{id}")
    public Map<String, Object> updateRule(@PathVariable Long id, @RequestBody QueueAlertRule rule) {
        QueueAlertRule existing = alertRuleMapper.selectById(id);
        if (existing == null) {
            return fail(404, "告警规则不存在");
        }
        QueueAlertRule update = new QueueAlertRule();
        update.setId(id);
        if (rule.getQueueName() != null) update.setQueueName(rule.getQueueName());
        if (rule.getMetric() != null) update.setMetric(rule.getMetric());
        if (rule.getOperator() != null) update.setOperator(rule.getOperator());
        if (rule.getThreshold() != null) update.setThreshold(rule.getThreshold());
        if (rule.getEnabled() != null) update.setEnabled(rule.getEnabled());
        if (rule.getNotifyEmail() != null) update.setNotifyEmail(rule.getNotifyEmail());
        alertRuleMapper.updateById(update);
        QueueAlertRule result = alertRuleMapper.selectById(id);
        return ok(result);
    }

    @DeleteMapping("/rules/{id}")
    public Map<String, Object> deleteRule(@PathVariable Long id) {
        QueueAlertRule existing = alertRuleMapper.selectById(id);
        if (existing == null) {
            return fail(404, "告警规则不存在");
        }
        alertRuleMapper.deleteById(id);
        return ok(null);
    }

    // ===== Unread Alerts Count =====
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount() {
        QueryWrapper<OperationLog> qw = new QueryWrapper<>();
        qw.eq("result", "failed");
        long count = operationLogMapper.selectCount(qw);
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return ok(data);
    }

    // ===== Alert History from operation_log =====

    @GetMapping("/history")
    public Map<String, Object> listHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        QueryWrapper<OperationLog> qw = new QueryWrapper<>();
        qw.eq("result", "failed");
        qw.orderByDesc("create_time");
        if (module != null && !module.isEmpty()) {
            qw.eq("module", module);
        }
        if (startDate != null && !startDate.isEmpty()) {
            qw.ge("create_time", startDate + " 00:00:00");
        }
        if (endDate != null && !endDate.isEmpty()) {
            qw.le("create_time", endDate + " 23:59:59");
        }
        Page<OperationLog> p = operationLogMapper.selectPage(new Page<>(page, pageSize), qw);
        Map<String, Object> data = new HashMap<>();
        data.put("records", p.getRecords());
        data.put("total", p.getTotal());
        return ok(data);
    }
}
