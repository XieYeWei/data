package com.hermes.service.workflow;

import com.hermes.entity.WorkflowExecution;
import com.hermes.entity.WorkflowStep;
import com.hermes.entity.WorkflowStepExecution;
import com.hermes.mapper.WorkflowStepExecutionMapper;
import com.hermes.service.mr.MrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Component
@Slf4j
public class StepExecutor {

    @Autowired(required = false)
    private MrService mrService;

    @Autowired
    private WorkflowStepExecutionMapper workflowStepExecutionMapper;

    public WorkflowStepExecution executeStep(WorkflowStep step, WorkflowExecution execution) {
        WorkflowStepExecution stepExec = new WorkflowStepExecution();
        stepExec.setExecutionId(execution.getId());
        stepExec.setStepId(step.getId());
        stepExec.setStatus("RUNNING");
        stepExec.setAttempt(0);
        stepExec.setStartTime(LocalDateTime.now());
        workflowStepExecutionMapper.insert(stepExec);

        try {
            String stepType = step.getStepType() != null ? step.getStepType().toUpperCase() : "SHELL";

            switch (stepType) {
                case "MAPREDUCE":
                    executeMapReduce(step, execution, stepExec);
                    break;
                case "SHELL":
                    executeShell(step, stepExec);
                    break;
                case "WAIT":
                    executeWait(step, stepExec);
                    break;
                case "HTTP":
                    executeHttp(step, stepExec);
                    break;
                default:
                    executeShell(step, stepExec);
                    break;
            }

            stepExec.setStatus("SUCCESS");
        } catch (Exception e) {
            log.error("Step execution failed: stepId={}, error={}", step.getId(), e.getMessage(), e);
            stepExec.setStatus("FAILED");
            stepExec.setErrorMessage(e.getMessage());
        }

        stepExec.setEndTime(LocalDateTime.now());
        if (stepExec.getStartTime() != null) {
            stepExec.setDurationMs(java.time.Duration.between(stepExec.getStartTime(), stepExec.getEndTime()).toMillis());
        }
        workflowStepExecutionMapper.updateById(stepExec);
        return stepExec;
    }

    private void executeMapReduce(WorkflowStep step, WorkflowExecution execution, WorkflowStepExecution stepExec) throws Exception {
        if (mrService == null) {
            throw new RuntimeException("MrService not available for MAPREDUCE step");
        }
        String clusterId = step.getWorkflowId() != null ? "cluster" + step.getWorkflowId() : "cluster1";
        if (step.getTemplateId() != null) {
            String applicationId = mrService.submitJobFromTemplate(clusterId, step.getTemplateId(), execution.getCreatedBy());
            stepExec.setApplicationId(applicationId);
        } else {
            throw new RuntimeException("MAPREDUCE step requires a templateId");
        }
    }

    private void executeShell(WorkflowStep step, WorkflowStepExecution stepExec) throws Exception {
        String command = step.getCommand();
        if (command == null || command.isEmpty()) {
            command = step.getScriptPath();
        }
        if (command == null || command.isEmpty()) {
            throw new RuntimeException("SHELL step requires command or scriptPath");
        }

        ProcessBuilder pb = new ProcessBuilder();
        if (step.getScriptPath() != null && !step.getScriptPath().isEmpty()) {
            pb.command("bash", step.getScriptPath());
        } else {
            pb.command("bash", "-c", command);
        }

        Process process = pb.start();

        String stdout = new BufferedReader(new InputStreamReader(process.getInputStream()))
                .lines().collect(Collectors.joining("\n"));
        String stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()))
                .lines().collect(Collectors.joining("\n"));

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Shell command failed with exit code " + exitCode + ": " + stderr);
        }

        String logPath = "/tmp/workflow/" + stepExec.getExecutionId() + "/" + stepExec.getId() + ".log";
        stepExec.setLogPath(logPath);
        log.info("Shell step completed: stepId={}, stdout={}", step.getId(), stdout);
    }

    private void executeWait(WorkflowStep step, WorkflowStepExecution stepExec) throws Exception {
        long timeoutMs = 1000L;
        if (step.getArgs() != null && !step.getArgs().isEmpty()) {
            try {
                timeoutMs = Long.parseLong(step.getArgs().trim());
            } catch (NumberFormatException e) {
                // fallback to default
            }
        } else if (step.getTimeoutMinutes() > 0) {
            timeoutMs = step.getTimeoutMinutes() * 60L * 1000L;
        }
        Thread.sleep(timeoutMs);
        log.info("Wait step completed: stepId={}, durationMs={}", step.getId(), timeoutMs);
    }

    private void executeHttp(WorkflowStep step, WorkflowStepExecution stepExec) throws Exception {
        String urlStr = step.getCommand() != null ? step.getCommand() : step.getArgs();
        if (urlStr == null || urlStr.isEmpty()) {
            throw new RuntimeException("HTTP step requires a URL in command or args");
        }

        URI uri = new URI(urlStr);
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            log.info("HTTP step completed: stepId={}, url={}, responseCode={}", step.getId(), urlStr, responseCode);
        } else {
            throw new RuntimeException("HTTP request failed with response code: " + responseCode);
        }
        conn.disconnect();
    }
}
