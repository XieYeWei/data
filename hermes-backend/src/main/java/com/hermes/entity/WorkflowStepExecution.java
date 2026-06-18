package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow_step_execution")
public class WorkflowStepExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long executionId;
    private Long stepId;
    private String status;
    private String applicationId;
    private String logPath;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
    private int attempt;
    private LocalDateTime createTime;
}
