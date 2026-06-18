package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("catalog_tag")
public class CatalogTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String color;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
