package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("metric_snapshot")
public class MetricSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long clusterId;
    private String module;           // hdfs / yarn
    private Long usedSpace;          // HDFS used bytes
    private Long fileCount;
    private Integer numNodeManagers;
    private Long totalMemoryMB;
    private Integer runningApplications;
    private String extraJson;        // future extension
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}