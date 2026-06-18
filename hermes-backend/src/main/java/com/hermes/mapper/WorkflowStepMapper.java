package com.hermes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.entity.WorkflowStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WorkflowStepMapper extends BaseMapper<WorkflowStep> {

    @Select("SELECT * FROM workflow_step WHERE workflow_id = #{workflowId} ORDER BY step_order ASC")
    List<WorkflowStep> findByWorkflowId(@Param("workflowId") Long workflowId);
}
