package com.hermes.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hermes.entity.WorkflowDefinition;
import com.hermes.entity.WorkflowExecution;
import com.hermes.entity.WorkflowStep;
import com.hermes.entity.WorkflowStepExecution;
import com.hermes.service.workflow.WorkflowService;
import com.hermes.util.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    @Autowired
    private WorkflowService workflowService;

    // ==================== Workflow Definition ====================

    @GetMapping
    public R listWorkflows(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        IPage<WorkflowDefinition> result = workflowService.listWorkflows(page, size);
        return R.success(result);
    }

    @PostMapping
    public R createWorkflow(@RequestBody WorkflowDefinition workflow) {
        WorkflowDefinition created = workflowService.createWorkflow(workflow);
        log.info("Created workflow: id={}, name={}", created.getId(), created.getName());
        return R.success(created);
    }

    @GetMapping("/{id}")
    public R getWorkflow(@PathVariable Long id) {
        WorkflowDefinition wf = workflowService.getWorkflow(id);
        if (wf == null) {
            return R.error(404, "Workflow not found: " + id);
        }
        List<WorkflowStep> steps = workflowService.listSteps(id);
        return R.success(Map.of("workflow", wf, "steps", steps));
    }

    @PutMapping("/{id}")
    public R updateWorkflow(@PathVariable Long id, @RequestBody WorkflowDefinition workflow) {
        WorkflowDefinition existing = workflowService.getWorkflow(id);
        if (existing == null) {
            return R.error(404, "Workflow not found: " + id);
        }
        workflow.setId(id);
        WorkflowDefinition updated = workflowService.updateWorkflow(workflow);
        log.info("Updated workflow: id={}", id);
        return R.success(updated);
    }

    @DeleteMapping("/{id}")
    public R deleteWorkflow(@PathVariable Long id) {
        WorkflowDefinition existing = workflowService.getWorkflow(id);
        if (existing == null) {
            return R.error(404, "Workflow not found: " + id);
        }
        workflowService.deleteWorkflow(id);
        log.info("Deleted workflow: id={}", id);
        return R.success();
    }

    @PatchMapping("/{id}/toggle")
    public R toggleWorkflow(@PathVariable Long id) {
        WorkflowDefinition existing = workflowService.getWorkflow(id);
        if (existing == null) {
            return R.error(404, "Workflow not found: " + id);
        }
        workflowService.toggleWorkflow(id);
        WorkflowDefinition updated = workflowService.getWorkflow(id);
        log.info("Toggled workflow: id={}, enabled={}", id, updated.getEnabled());
        return R.success(updated);
    }

    // ==================== Workflow Steps ====================

    @GetMapping("/{id}/steps")
    public R listSteps(@PathVariable Long id) {
        List<WorkflowStep> steps = workflowService.listSteps(id);
        return R.success(steps);
    }

    @PostMapping("/{id}/steps")
    public R addStep(@PathVariable Long id, @RequestBody WorkflowStep step) {
        step.setWorkflowId(id);
        WorkflowStep created = workflowService.addStep(step);
        log.info("Added step: id={}, name={}, workflowId={}", created.getId(), created.getName(), id);
        return R.success(created);
    }

    @PutMapping("/{id}/steps/{stepId}")
    public R updateStep(@PathVariable Long id, @PathVariable Long stepId,
                        @RequestBody WorkflowStep step) {
        step.setId(stepId);
        step.setWorkflowId(id);
        WorkflowStep updated = workflowService.updateStep(step);
        log.info("Updated step: id={}, workflowId={}", stepId, id);
        return R.success(updated);
    }

    @DeleteMapping("/{id}/steps/{stepId}")
    public R deleteStep(@PathVariable Long id, @PathVariable Long stepId) {
        workflowService.deleteStep(stepId);
        log.info("Deleted step: id={}, workflowId={}", stepId, id);
        return R.success();
    }

    @PutMapping("/{id}/steps/reorder")
    public R reorderSteps(@PathVariable Long id, @RequestBody List<Map<String, Object>> orderList) {
        workflowService.reorderSteps(id, orderList);
        log.info("Reordered steps for workflow: id={}", id);
        return R.success();
    }

    // ==================== Execution ====================

    @PostMapping("/{id}/execute")
    public R executeWorkflow(@PathVariable Long id) {
        WorkflowDefinition wf = workflowService.getWorkflow(id);
        if (wf == null) {
            return R.error(404, "Workflow not found: " + id);
        }
        WorkflowExecution execution = workflowService.executeWorkflow(id, wf.getCreatedBy());
        log.info("Triggered execution: id={}, workflowId={}", execution.getId(), id);
        return R.success(execution);
    }

    @GetMapping("/{id}/executions")
    public R listExecutions(@PathVariable Long id,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size) {
        IPage<WorkflowExecution> result = workflowService.listExecutions(id, page, size);
        return R.success(result);
    }

    @GetMapping("/executions/{execId}")
    public R getExecution(@PathVariable Long execId) {
        WorkflowExecution execution = workflowService.getExecution(execId);
        if (execution == null) {
            return R.error(404, "Execution not found: " + execId);
        }
        return R.success(execution);
    }

    @GetMapping("/executions/{execId}/steps")
    public R getStepExecutions(@PathVariable Long execId) {
        List<WorkflowStepExecution> stepExecs = workflowService.getStepExecutions(execId);
        return R.success(stepExecs);
    }

    @PostMapping("/executions/{execId}/cancel")
    public R cancelExecution(@PathVariable Long execId) {
        WorkflowExecution execution = workflowService.getExecution(execId);
        if (execution == null) {
            return R.error(404, "Execution not found: " + execId);
        }
        workflowService.cancelExecution(execId);
        log.info("Cancelled execution: id={}", execId);
        return R.success();
    }
}
