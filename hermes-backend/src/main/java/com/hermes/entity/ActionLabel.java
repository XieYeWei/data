package com.hermes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("action_label")
public class ActionLabel {
    private String action;
    private String label;
    private String module;
}
