package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("catalog_column")
public class CatalogColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String name;
    private String type;
    private String comment;
    private Boolean nullable;
    private Boolean isPartition;
    private Integer ordinalPosition;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
