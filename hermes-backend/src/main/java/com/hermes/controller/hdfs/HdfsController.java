package com.hermes.controller.hdfs;

import com.hermes.service.hdfs.HdfsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

/**
 * REST Controller for HDFS operations.
 * Base path: /api/v1/hdfs/**
 * Follows uniform response: {code, msg, data}
 * Milestone 1: read-only file browser
 */
@RestController
@RequestMapping("/api/v1/hdfs")
public class HdfsController {

    @Autowired
    private HdfsService hdfsService;

    /**
     * List files in directory
     * Example: GET /api/v1/hdfs/list?clusterId=cluster1&path=/user
     */
    @GetMapping("/list")
    public Map<String, Object> listFiles(
            @RequestParam(defaultValue = "cluster1") String clusterId,
            @RequestParam(defaultValue = "/") String path) {
        try {
            List<Map<String, Object>> files = hdfsService.listFiles(clusterId, path);
            return success(files);
        } catch (IOException e) {
            return error(500, "Failed to list files: " + e.getMessage());
        }
    }

    /**
     * Get file or directory status
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus(
            @RequestParam(defaultValue = "cluster1") String clusterId,
            @RequestParam String path) {
        try {
            Map<String, Object> status = hdfsService.getFileStatus(clusterId, path);
            return success(status);
        } catch (IOException e) {
            return error(404, "Path not found or error: " + e.getMessage());
        }
    }

    /**
     * Get content summary (space usage, quotas)
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary(
            @RequestParam(defaultValue = "cluster1") String clusterId,
            @RequestParam(defaultValue = "/") String path) {
        try {
            Map<String, Object> summary = hdfsService.getContentSummary(clusterId, path);
            return success(summary);
        } catch (IOException e) {
            return error(500, e.getMessage());
        }
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("msg", "success");
        resp.put("data", data);
        return resp;
    }

    private Map<String, Object> error(int code, String msg) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", code);
        resp.put("msg", msg);
        resp.put("data", null);
        return resp;
    }
}