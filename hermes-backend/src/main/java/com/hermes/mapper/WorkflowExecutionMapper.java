package com.hermes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.entity.WorkflowExecution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {
}
