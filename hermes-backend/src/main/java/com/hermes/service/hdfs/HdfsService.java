package com.hermes.service.hdfs;

import com.hermes.config.HadoopConfig;
import org.apache.hadoop.fs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HDFS Service layer using official Hadoop FileSystem / DistributedFileSystem APIs.
 * Implements core file browser operations (read-only for milestone 1).
 * All operations go through FileSystem interface for compatibility.
 */
@Service
public class HdfsService {

    private static final Logger log = LoggerFactory.getLogger(HdfsService.class);

    @Autowired
    private HadoopConfig hadoopConfig;

    /**
     * List directory contents using FileSystem.listStatus(Path)
     */
    public List<Map<String, Object>> listFiles(String clusterId, String pathStr) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (!fs.exists(path)) {
            throw new IOException("Path does not exist: " + pathStr);
        }
        FileStatus[] statuses = fs.listStatus(path);
        return Arrays.stream(statuses)
                .map(this::fileStatusToMap)
                .collect(Collectors.toList());
    }

    /**
     * Get single file/directory status using FileSystem.getFileStatus(Path)
     */
    public Map<String, Object> getFileStatus(String clusterId, String pathStr) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        FileStatus status = fs.getFileStatus(path);
        return fileStatusToMap(status);
    }

    /**
     * Get content summary (used for quota, space usage) - FileSystem.getContentSummary
     */
    public Map<String, Object> getContentSummary(String clusterId, String pathStr) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        ContentSummary summary = fs.getContentSummary(path);
        Map<String, Object> result = new HashMap<>();
        result.put("length", summary.getLength());
        result.put("fileCount", summary.getFileCount());
        result.put("directoryCount", summary.getDirectoryCount());
        result.put("quota", summary.getQuota());
        result.put("spaceQuota", summary.getSpaceQuota());
        result.put("spaceConsumed", summary.getSpaceConsumed());
        return result;
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

    // TODO for future milestones:
    // - create/mkdirs using fs.mkdirs(Path, FsPermission)
    // - delete using fs.delete(Path, recursive)
    // - rename, setPermission, upload/download streams (FSDataOutputStream / FSDataInputStream)
    // - Support UserGroupInformation.doAs(proxyUser, action) for audited proxy operations
}