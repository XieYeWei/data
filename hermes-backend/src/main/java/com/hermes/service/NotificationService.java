package com.hermes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${notification.dingtalk.webhook:}")
    private String dingTalkWebhook;

    public void sendAlertNotification(List<Map<String, Object>> alerts) {
        if (alerts == null || alerts.isEmpty()) return;

        StringBuilder message = new StringBuilder("**YARN 队列告警**

");
        for (Map<String, Object> alert : alerts) {
            message.append(String.format("- 队列: %s | 指标: %s | 当前值: %s | 阈值: %s
",
                    alert.get("queueName"),
                    alert.get("metric"),
                    alert.get("currentValue"),
                    alert.get("threshold")));
        }

        // DingTalk notification
        if (dingTalkWebhook != null && !dingTalkWebhook.isEmpty()) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("msgtype", "markdown");
                Map<String, String> markdown = new HashMap<>();
                markdown.put("title", "YARN Queue Alert");
                markdown.put("text", message.toString());
                payload.put("markdown", markdown);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

                restTemplate.postForEntity(dingTalkWebhook, entity, String.class);
                log.info("DingTalk alert sent successfully");
            } catch (Exception e) {
                log.error("Failed to send DingTalk notification", e);
            }
        }

        // TODO: Add Spring Mail implementation here
        log.info("Alert notification triggered: {}", alerts.size());
    }
}