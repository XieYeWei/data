package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow_step")
public class WorkflowStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private String name;
    private String stepType;
    private Long templateId;
    private String command;
    private String scriptPath;
    private String inputPath;
    private String outputPath;
    private String args;
    private String queue;
    private int stepOrder;
    private String dependsOn;
    private int retryCount;
    private int timeoutMinutes;
    private LocalDateTime createTime;
}
