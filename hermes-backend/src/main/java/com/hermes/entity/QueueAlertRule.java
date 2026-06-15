package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("queue_alert_rule")
public class QueueAlertRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String queueName;
    private String metric;           // usedCapacity, numApplications
    private Double threshold;        // e.g. 80.0 for 80%
    private String operator;         // >, <, >=, <=
    private Boolean enabled = true;
    private String notifyEmail;      // future
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}