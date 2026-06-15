package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cluster")
public class Cluster {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String namenode;      // hdfs://host:8020
    private String resourcemanager; // host:8032
    private String version;       // 3.3.6
    private String authType;      // simple / kerberos
    private String keytabPath;    // for kerberos
    private String principal;     // for kerberos
    private Boolean enabled = true;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}