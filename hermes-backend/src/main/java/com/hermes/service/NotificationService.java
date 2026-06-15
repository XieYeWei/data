package com.hermes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${notification.dingtalk.webhook:}")
    private String dingTalkWebhook;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendAlertNotification(List<Map<String, Object>> alerts) {
        if (alerts == null || alerts.isEmpty()) return;

        StringBuilder content = new StringBuilder();
        content.append("YARN Queue Alert Triggered:\n\n");
        for (Map<String, Object> alert : alerts) {
            content.append(String.format("Queue: %s | Metric: %s | Current: %s | Threshold: %s\n",
                    alert.get("queueName"), alert.get("metric"), alert.get("currentValue"), alert.get("threshold")));
        }

        // Real Email Notification
        if (fromEmail != null && !fromEmail.isEmpty()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo("admin@yourcompany.com"); // TODO: Make per-rule configurable
                message.setSubject("[Hermes Alert] YARN Queue Threshold Exceeded");
                message.setText(content.toString());
                mailSender.send(message);
                log.info("Email alert sent to admin@yourcompany.com");
            } catch (Exception e) {
                log.error("Failed to send email notification", e);
            }
        }

        // DingTalk Webhook
        if (dingTalkWebhook != null && !dingTalkWebhook.isEmpty()) {
            log.info("[DingTalk] Alert would be sent: {}", content);
        }

        log.info("Processed {} alerts", alerts.size());
    }
}