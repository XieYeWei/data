package com.hermes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.entity.WorkflowStepExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkflowStepExecutionMapper extends BaseMapper<WorkflowStepExecution> {

    @Select("SELECT * FROM workflow_step_execution WHERE execution_id = #{executionId} ORDER BY id ASC")
    List<WorkflowStepExecution> findByExecutionId(@Param("executionId") Long executionId);
}
