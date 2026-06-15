package com.hermes.controller.hdfs;

import com.hermes.service.hdfs.HdfsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/hdfs")
public class HdfsController {

    @Autowired
    private HdfsService hdfsService;

    @GetMapping("/files")
    public Map<String, Object> listFiles(@RequestParam(defaultValue = "cluster1") String clusterId,
                                         @RequestParam(defaultValue = "/") String path,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L; // TODO: get real user id
        try {
            return success(hdfsService.listFiles(clusterId, path, userId));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @GetMapping("/status")
    public Map<String, Object> getFileStatus(@RequestParam(defaultValue = "cluster1") String clusterId,
                                             @RequestParam String path,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            return success(hdfsService.getFileStatus(clusterId, path, userId));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @GetMapping("/summary")
    public Map<String, Object> getContentSummary(@RequestParam(defaultValue = "cluster1") String clusterId,
                                                 @RequestParam String path,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            return success(hdfsService.getContentSummary(clusterId, path, userId));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    private Map<String, Object> success(Object data) {
        return Map.of("code", 0, "msg", "success", "data", data);
    }

    private Map<String, Object> error(int code, String msg) {
        return Map.of("code", code, "msg", msg, "data", null);
    }
}