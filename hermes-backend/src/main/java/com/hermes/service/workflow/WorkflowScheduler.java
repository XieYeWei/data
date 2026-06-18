package com.hermes.service.workflow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hermes.entity.WorkflowDefinition;
import com.hermes.mapper.WorkflowDefinitionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@Slf4j
public class WorkflowScheduler {

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Autowired
    private WorkflowService workflowService;

    /**
     * Check every minute for scheduled workflows that need to be triggered.
     */
    @Scheduled(fixedRate = 60000)
    public void checkScheduledWorkflows() {
        List<WorkflowDefinition> workflows = workflowDefinitionMapper.selectList(
                new QueryWrapper<WorkflowDefinition>()
                        .eq("enabled", true)
                        .isNotNull("schedule_cron")
                        .ne("schedule_cron", "")
        );

        for (WorkflowDefinition wf : workflows) {
            try {
                if (matchesCurrentMinute(wf.getScheduleCron())) {
                    log.info("Triggering scheduled workflow: id={}, name={}, cron={}",
                            wf.getId(), wf.getName(), wf.getScheduleCron());
                    workflowService.executeWorkflow(wf.getId(), wf.getCreatedBy());
                }
            } catch (Exception e) {
                log.error("Failed to check/trigger workflow: id={}, cron={}",
                        wf.getId(), wf.getScheduleCron(), e);
            }
        }
    }

    /**
     * Simple cron-like matcher that only checks the current minute.
     * Supports: "* * * * *" (standard 5-field cron) and special cases.
     * Only checks minute and hour fields for per-minute scheduling.
     */
    private boolean matchesCurrentMinute(String cron) {
        if (cron == null || cron.trim().isEmpty()) {
            return false;
        }

        String[] parts = cron.trim().split("\\s+");
        // Support both 5-field (min hour dom mon dow) and 6-field (sec min hour dom mon dow)
        int offset = (parts.length == 6) ? 1 : 0;

        if (parts.length < 5 + offset) {
            log.warn("Invalid cron expression: {}", cron);
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        int currentMinute = now.getMinute();
        int currentHour = now.getHour();

        String minuteField = parts[0 + offset];
        String hourField = parts[1 + offset];

        return matchesField(minuteField, currentMinute, 0, 59)
                && matchesField(hourField, currentHour, 0, 23);
    }

    private boolean matchesField(String field, int value, int min, int max) {
        if ("*".equals(field.trim())) {
            return true;
        }

        // Handle step values: */5, 1/5
        if (field.contains("/")) {
            String[] parts = field.split("/");
            int step = Integer.parseInt(parts[1].trim());
            int start = min;
            if (!"*".equals(parts[0].trim())) {
                start = Integer.parseInt(parts[0].trim());
            }
            return value >= start && (value - start) % step == 0;
        }

        // Handle comma-separated list: 5,10,15
        if (field.contains(",")) {
            String[] values = field.split(",");
            for (String v : values) {
                if (Integer.parseInt(v.trim()) == value) {
                    return true;
                }
            }
            return false;
        }

        // Handle range: 1-5
        if (field.contains("-")) {
            String[] range = field.split("-");
            int low = Integer.parseInt(range[0].trim());
            int high = Integer.parseInt(range[1].trim());
            return value >= low && value <= high;
        }

        // Single value
        try {
            return Integer.parseInt(field.trim()) == value;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
