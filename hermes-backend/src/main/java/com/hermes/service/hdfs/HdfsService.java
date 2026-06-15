package com.hermes.service.hdfs;

import com.hermes.config.HadoopConfig;
import com.hermes.entity.OperationLog;
import com.hermes.mapper.OperationLogMapper;
import org.apache.hadoop.fs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HdfsService {

    private static final Logger log = LoggerFactory.getLogger(HdfsService.class);

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper; // for audit

    public List<Map<String, Object>> listFiles(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (!fs.exists(path)) {
            throw new IOException("Path does not exist: " + pathStr);
        }
        FileStatus[] statuses = fs.listStatus(path);
        logOperation(userId, clusterId, "hdfs", "list", pathStr, "success", null);
        return Arrays.stream(statuses).map(this::fileStatusToMap).collect(Collectors.toList());
    }

    public Map<String, Object> getFileStatus(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        FileStatus status = fs.getFileStatus(path);
        logOperation(userId, clusterId, "hdfs", "getStatus", pathStr, "success", null);
        return fileStatusToMap(status);
    }

    public Map<String, Object> getContentSummary(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        ContentSummary summary = fs.getContentSummary(path);
        logOperation(userId, clusterId, "hdfs", "getSummary", pathStr, "success", null);
        Map<String, Object> result = new HashMap<>();
        result.put("length", summary.getLength());
        result.put("fileCount", summary.getFileCount());
        result.put("directoryCount", summary.getDirectoryCount());
        result.put("quota", summary.getQuota());
        result.put("spaceQuota", summary.getSpaceQuota());
        result.put("spaceConsumed", summary.getSpaceConsumed());
        return result;
    }

    private void logOperation(Long userId, String clusterIdStr, String module, String action, String target, String result, String detail) {
        if (operationLogMapper == null) return;
        try {
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(userId != null ? userId : 1L); // demo user
            logEntry.setClusterId(Long.parseLong(clusterIdStr.replace("cluster", "")));
            logEntry.setModule(module);
            logEntry.setAction(action);
            logEntry.setTarget(target);
            logEntry.setResult(result);
            logEntry.setDetail(detail);
            operationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("Failed to write audit log", e);
        }
    }

    private Map<String, Object> fileStatusToMap(FileStatus status) {
        Map<String, Object> map = new HashMap<>();
        map.put("path", status.getPath().toString());
        map.put("name", status.getPath().getName());
        map.put("isDirectory", status.isDirectory());
        map.put("length", status.getLen());
        map.put("replication", status.getReplication());
        map.put("blockSize", status.getBlockSize());
        map.put("modificationTime", status.getModificationTime());
        map.put("accessTime", status.getAccessTime());
        map.put("owner", status.getOwner());
        map.put("group", status.getGroup());
        map.put("permission", status.getPermission().toString());
        map.put("isFile", status.isFile());
        return map;
    }

    // TODO: Add create, delete, upload with proper audit logging
}