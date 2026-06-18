package com.hermes.service.workflow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.entity.WorkflowDefinition;
import com.hermes.entity.WorkflowExecution;
import com.hermes.entity.WorkflowStep;
import com.hermes.entity.WorkflowStepExecution;
import com.hermes.mapper.WorkflowDefinitionMapper;
import com.hermes.mapper.WorkflowExecutionMapper;
import com.hermes.mapper.WorkflowStepExecutionMapper;
import com.hermes.mapper.WorkflowStepMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;

@Service
@Slf4j
public class WorkflowService {

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Autowired
    private WorkflowStepMapper workflowStepMapper;

    @Autowired
    private WorkflowExecutionMapper workflowExecutionMapper;

    @Autowired
    private WorkflowStepExecutionMapper workflowStepExecutionMapper;

    @Autowired
    private StepExecutor stepExecutor;

    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Workflow Definition CRUD ====================

    public IPage<WorkflowDefinition> listWorkflows(int page, int size) {
        return workflowDefinitionMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<WorkflowDefinition>().orderByDesc("create_time"));
    }

    public WorkflowDefinition getWorkflow(Long id) {
        return workflowDefinitionMapper.selectById(id);
    }

    @Transactional
    public WorkflowDefinition createWorkflow(WorkflowDefinition workflow) {
        workflowDefinitionMapper.insert(workflow);
        return workflow;
    }

    @Transactional
    public WorkflowDefinition updateWorkflow(WorkflowDefinition workflow) {
        workflowDefinitionMapper.updateById(workflow);
        return workflowDefinitionMapper.selectById(workflow.getId());
    }

    @Transactional
    public void deleteWorkflow(Long id) {
        // cascade delete steps
        workflowStepMapper.delete(new QueryWrapper<WorkflowStep>().eq("workflow_id", id));
        workflowDefinitionMapper.deleteById(id);
    }

    @Transactional
    public void toggleWorkflow(Long id) {
        WorkflowDefinition wf = workflowDefinitionMapper.selectById(id);
        if (wf != null) {
            wf.setEnabled(wf.getEnabled() != null ? !wf.getEnabled() : true);
            workflowDefinitionMapper.updateById(wf);
        }
    }

    // ==================== Workflow Step CRUD ====================

    public List<WorkflowStep> listSteps(Long workflowId) {
        return workflowStepMapper.findByWorkflowId(workflowId);
    }

    @Transactional
    public WorkflowStep addStep(WorkflowStep step) {
        // auto-assign step_order
        List<WorkflowStep> existing = workflowStepMapper.findByWorkflowId(step.getWorkflowId());
        int maxOrder = existing.stream().mapToInt(WorkflowStep::getStepOrder).max().orElse(-1);
        step.setStepOrder(maxOrder + 1);
        workflowStepMapper.insert(step);
        return step;
    }

    @Transactional
    public WorkflowStep updateStep(WorkflowStep step) {
        workflowStepMapper.updateById(step);
        return workflowStepMapper.selectById(step.getId());
    }

    @Transactional
    public void deleteStep(Long stepId) {
        workflowStepMapper.deleteById(stepId);
    }

    @Transactional
    public void reorderSteps(Long workflowId, List<Map<String, Object>> orderList) {
        // orderList: [{id: 1, stepOrder: 0}, {id: 2, stepOrder: 1}, ...]
        for (Map<String, Object> item : orderList) {
            Long id = Long.valueOf(item.get("id").toString());
            int order = Integer.parseInt(item.get("stepOrder").toString());
            WorkflowStep step = workflowStepMapper.selectById(id);
            if (step != null && step.getWorkflowId().equals(workflowId)) {
                step.setStepOrder(order);
                workflowStepMapper.updateById(step);
            }
        }
    }

    // ==================== Execution CRUD ====================

    public IPage<WorkflowExecution> listExecutions(Long workflowId, int page, int size) {
        return workflowExecutionMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<WorkflowExecution>().eq("workflow_id", workflowId).orderByDesc("create_time"));
    }

    public WorkflowExecution getExecution(Long execId) {
        return workflowExecutionMapper.selectById(execId);
    }

    public List<WorkflowStepExecution> getStepExecutions(Long execId) {
        return workflowStepExecutionMapper.findByExecutionId(execId);
    }

    // ==================== Topological Sort ====================

    /**
     * Parse depends_on JSON array and return list of dependency step IDs.
     * depends_on format: "[1, 3]" or "[\"step1\", \"step3\"]" or null
     */
    private List<Long> parseDependsOn(String dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty() || dependsOn.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(dependsOn, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse depends_on: {}", dependsOn, e);
            return Collections.emptyList();
        }
    }

    /**
     * Build adjacency list and compute in-degrees for Kahn's algorithm.
     * Returns a map of stepId -> list of downstream stepIds.
     */
    private Map<Long, List<Long>> buildDAG(List<WorkflowStep> steps, Map<Long, Integer> inDegrees) {
        Map<Long, List<Long>> adjacency = new HashMap<>();
        Map<Long, WorkflowStep> stepMap = steps.stream().collect(Collectors.toMap(WorkflowStep::getId, s -> s));

        for (WorkflowStep step : steps) {
            adjacency.putIfAbsent(step.getId(), new ArrayList<>());
            inDegrees.putIfAbsent(step.getId(), 0);
        }

        for (WorkflowStep step : steps) {
            List<Long> deps = parseDependsOn(step.getDependsOn());
            for (Long depId : deps) {
                adjacency.computeIfAbsent(depId, k -> new ArrayList<>()).add(step.getId());
                inDegrees.merge(step.getId(), 1, Integer::sum);
            }
        }

        return adjacency;
    }

    /**
     * Topological sort using Kahn's algorithm.
     * Returns ordered list of step IDs, or throws if cycle detected.
     */
    public List<Long> topologicalSort(List<WorkflowStep> steps) {
        Map<Long, Integer> inDegrees = new HashMap<>();
        Map<Long, List<Long>> adjacency = buildDAG(steps, inDegrees);

        Queue<Long> queue = new LinkedList<>();
        for (Map.Entry<Long, Integer> entry : inDegrees.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<Long> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            sorted.add(current);
            for (Long neighbor : adjacency.getOrDefault(current, Collections.emptyList())) {
                int newDegree = inDegrees.merge(neighbor, -1, (old, val) -> old - 1);
                if (newDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        if (sorted.size() != steps.size()) {
            throw new RuntimeException("Cycle detected in workflow DAG: sorted " + sorted.size() + " of " + steps.size() + " steps");
        }

        return sorted;
    }

    // ==================== Execution Engine ====================

    /**
     * Main execution method. Runs workflow asynchronously.
     */
    @Transactional
    public WorkflowExecution executeWorkflow(Long workflowId, Long userId) {
        WorkflowDefinition wf = workflowDefinitionMapper.selectById(workflowId);
        if (wf == null) {
            throw new RuntimeException("Workflow not found: " + workflowId);
        }

        WorkflowExecution execution = new WorkflowExecution();
        execution.setWorkflowId(workflowId);
        execution.setStatus("RUNNING");
        execution.setTriggerType("MANUAL");
        execution.setStartTime(LocalDateTime.now());
        execution.setCreatedBy(userId);
        workflowExecutionMapper.insert(execution);

        Long executionId = execution.getId();

        // Run asynchronously
        executor.submit(() -> {
            try {
                runExecution(executionId, wf);
            } catch (Exception e) {
                log.error("Workflow execution failed: executionId={}, error={}", executionId, e.getMessage(), e);
                WorkflowExecution failed = workflowExecutionMapper.selectById(executionId);
                if (failed != null) {
                    failed.setStatus("FAILED");
                    failed.setErrorMessage(e.getMessage());
                    failed.setEndTime(LocalDateTime.now());
                    if (failed.getStartTime() != null) {
                        failed.setDurationMs(java.time.Duration.between(failed.getStartTime(), failed.getEndTime()).toMillis());
                    }
                    workflowExecutionMapper.updateById(failed);
                }
            }
        });

        return execution;
    }

    private void runExecution(Long executionId, WorkflowDefinition wf) {
        List<WorkflowStep> steps = workflowStepMapper.findByWorkflowId(wf.getId());
        if (steps.isEmpty()) {
            completeExecution(executionId, "SUCCESS", null);
            return;
        }

        // Build DAG for in-degree tracking during execution
        Map<Long, Integer> inDegrees = new HashMap<>();
        Map<Long, List<Long>> adjacency = buildDAG(steps, inDegrees);
        Map<Long, WorkflowStep> stepMap = steps.stream().collect(Collectors.toMap(WorkflowStep::getId, s -> s));

        // Track which steps are completed and their results
        Map<Long, WorkflowStepExecution> completedSteps = new ConcurrentHashMap<>();
        Set<Long> failedSteps = ConcurrentHashMap.newKeySet();

        // Track currently running futures
        Map<Long, CompletableFuture<Void>> runningFutures = new ConcurrentHashMap<>();
        Set<Long> submittedSteps = ConcurrentHashMap.newKeySet();

        // Find initial ready steps (in-degree 0)
        List<Long> readySteps = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : inDegrees.entrySet()) {
            if (entry.getValue() == 0) {
                readySteps.add(entry.getKey());
            }
        }

        WorkflowExecution execution = workflowExecutionMapper.selectById(executionId);

        // Process steps in waves
        while (!readySteps.isEmpty() || !runningFutures.isEmpty()) {
            // Submit all currently ready steps
            for (Long stepId : readySteps) {
                if (submittedSteps.contains(stepId)) continue;
                submittedSteps.add(stepId);

                WorkflowStep step = stepMap.get(stepId);
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        WorkflowStepExecution stepExec = stepExecutor.executeStep(step, execution);
                        completedSteps.put(stepId, stepExec);
                        if ("FAILED".equals(stepExec.getStatus())) {
                            failedSteps.add(stepId);
                        }
                    } catch (Exception e) {
                        log.error("Async step execution failed: stepId={}", stepId, e);
                        failedSteps.add(stepId);
                    }
                }, executor);

                runningFutures.put(stepId, future);
            }
            readySteps.clear();

            if (runningFutures.isEmpty()) break;

            // Wait for at least one step to complete
            CompletableFuture<?> anyOf = CompletableFuture.anyOf(
                    runningFutures.values().toArray(new CompletableFuture[0])
            );
            try {
                anyOf.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Timeout waiting for step completion, checking status...");
            } catch (Exception e) {
                log.error("Error while waiting for step completion", e);
            }

            // Check completed steps and update downstream in-degrees
            Set<Long> completedNow = new HashSet<>();
            for (Map.Entry<Long, CompletableFuture<Void>> entry : runningFutures.entrySet()) {
                if (entry.getValue().isDone()) {
                    completedNow.add(entry.getKey());
                }
            }

            for (Long completedStepId : completedNow) {
                runningFutures.remove(completedStepId);
                // Update downstream in-degrees
                for (Long downstream : adjacency.getOrDefault(completedStepId, Collections.emptyList())) {
                    int newDegree = inDegrees.merge(downstream, -1, (old, val) -> old - 1);
                    if (newDegree == 0 && !submittedSteps.contains(downstream)) {
                        readySteps.add(downstream);
                    }
                }
            }
        }

        // Determine final status
        if (failedSteps.isEmpty()) {
            completeExecution(executionId, "SUCCESS", null);
        } else {
            String errorMsg = "Steps failed: " + failedSteps.stream()
                    .map(id -> stepMap.get(id) != null ? stepMap.get(id).getName() : String.valueOf(id))
                    .collect(Collectors.joining(", "));
            completeExecution(executionId, "FAILED", errorMsg);
        }

        // Send webhook notification if configured
        sendWebhook(wf, executionId);
    }

    private void completeExecution(Long executionId, String status, String errorMessage) {
        WorkflowExecution execution = workflowExecutionMapper.selectById(executionId);
        if (execution != null) {
            execution.setStatus(status);
            execution.setEndTime(LocalDateTime.now());
            if (execution.getStartTime() != null) {
                execution.setDurationMs(java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis());
            }
            if (errorMessage != null) {
                execution.setErrorMessage(errorMessage);
            }
            workflowExecutionMapper.updateById(execution);
        }
    }

    /**
     * Send webhook notification after workflow execution completes.
     */
    private void sendWebhook(WorkflowDefinition def, Long executionId) {
        if (def.getWebhookUrl() == null || def.getWebhookUrl().isEmpty()) {
            return;
        }

        WorkflowExecution exec = workflowExecutionMapper.selectById(executionId);
        if (exec == null) {
            return;
        }

        List<WorkflowStepExecution> stepExecs = workflowStepExecutionMapper.findByExecutionId(executionId);

        // Build JSON payload
        StringBuilder stepsJson = new StringBuilder("[");
        for (int i = 0; i < stepExecs.size(); i++) {
            WorkflowStepExecution se = stepExecs.get(i);
            if (i > 0) stepsJson.append(",");
            stepsJson.append(String.format(
                "{\"stepId\":%d,\"status\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"durationMs\":%d}",
                se.getStepId() != null ? se.getStepId() : 0,
                se.getStatus() != null ? se.getStatus() : "",
                se.getStartTime() != null ? se.getStartTime().toString() : "",
                se.getEndTime() != null ? se.getEndTime().toString() : "",
                se.getDurationMs() != null ? se.getDurationMs() : 0
            ));
        }
        stepsJson.append("]");

        String json = String.format(
            "{\"workflowId\":%d,\"workflowName\":\"%s\",\"executionId\":%d,\"status\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"durationMs\":%d,\"steps\":%s}",
            def.getId(),
            escapeJson(def.getName() != null ? def.getName() : ""),
            exec.getId(),
            exec.getStatus() != null ? exec.getStatus() : "",
            exec.getStartTime() != null ? exec.getStartTime().toString() : "",
            exec.getEndTime() != null ? exec.getEndTime().toString() : "",
            exec.getDurationMs() != null ? exec.getDurationMs() : 0,
            stepsJson.toString()
        );

        try {
            URL url = new URL(def.getWebhookUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes("UTF-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                log.info("Webhook sent successfully to {} for execution {}, response code: {}",
                    def.getWebhookUrl(), executionId, responseCode);
            } else {
                log.warn("Webhook returned non-2xx status: {} for execution {}", responseCode, executionId);
            }
            conn.disconnect();
        } catch (Exception e) {
            log.error("Failed to send webhook to {} for execution {}: {}",
                def.getWebhookUrl(), executionId, e.getMessage());
        }
    }

    /**
     * Escape a string for JSON (simple escaping of quotes, backslashes, and control chars).
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    @Transactional
    public void cancelExecution(Long execId) {
        WorkflowExecution execution = workflowExecutionMapper.selectById(execId);
        if (execution != null && "RUNNING".equals(execution.getStatus())) {
            execution.setStatus("CANCELLED");
            execution.setEndTime(LocalDateTime.now());
            if (execution.getStartTime() != null) {
                execution.setDurationMs(java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis());
            }
            workflowExecutionMapper.updateById(execution);
        }
    }
}
