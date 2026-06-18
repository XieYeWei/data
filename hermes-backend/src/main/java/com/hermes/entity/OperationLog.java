package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;      // 冗余字段，用户删除后仍可追溯
    private Long clusterId;
    private String module;        // hdfs / yarn / mr
    private String action;        // list, upload, submit, kill etc.
    private String target;        // path or appId
    private String result;        // success / failed
    private String detail;        // extra info
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}