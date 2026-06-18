package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("job_template")
public class JobTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String jarHdfsPath;      // e.g. hdfs:///apps/mr/wordcount.jar
    private String mainClass;        // e.g. org.apache.hadoop.examples.WordCount
    private String defaultArgs;      // JSON or space-separated
    private String inputPath;
    private String outputPath;
    private String queue;            // YARN queue
    private String type;             // template category: WiFi, Spark, 自定义, etc.
    private Integer useCount;        // how many times this template has been used
    private LocalDateTime lastUsedTime; // last submission time
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}