package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("catalog_table")
public class CatalogTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clusterId;
    private String name;
    private String hdfsPath;
    private String schemaName;
    private String format;
    private String owner;
    private String description;
    private Long rowCount;
    private Integer fileCount;
    private Long totalSizeBytes;
    private String partitionColumns;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
