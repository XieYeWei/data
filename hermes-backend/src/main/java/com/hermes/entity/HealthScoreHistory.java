package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("health_score_history")
public class HealthScoreHistory {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer score;

    private LocalDate scoreDate;

    private Long hdfsUsed;

    private Long hdfsTotal;

    private Integer nmCount;

    private Integer appCount;

    private String detailJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
