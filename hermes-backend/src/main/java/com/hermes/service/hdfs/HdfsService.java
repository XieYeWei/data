package com.hermes.service.hdfs;

import com.hermes.config.HadoopConfig;
import com.hermes.config.HermesProperties;
import com.hermes.entity.OperationLog;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.mapper.UserMapper;
import com.hermes.util.AuditHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.fs.permission.*;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.protocol.DatanodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class HdfsService {

    private static final Logger log = LoggerFactory.getLogger(HdfsService.class);

    @Autowired
    private HadoopConfig hadoopConfig;

    @Autowired
    private HermesProperties props;

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper; // for audit
    @Autowired(required = false)
    private UserMapper userMapper;

    // === Server-side pagination cache for file listing ===
    private static class CachedDirListing {
        final List<Map<String, Object>> allFiles;
        final LocalDateTime cachedAt;
        final int ttlSeconds = 60;

        CachedDirListing(List<Map<String, Object>> allFiles) {
            this.allFiles = allFiles;
            this.cachedAt = LocalDateTime.now();
        }

        boolean isExpired() {
            return cachedAt.plusSeconds(ttlSeconds).isBefore(LocalDateTime.now());
        }
    }

    private final ConcurrentHashMap<String, CachedDirListing> fileListCache = new ConcurrentHashMap<>();

    public List<Map<String, Object>> listFiles(String clusterId, String pathStr, Long userId) throws IOException {
        // Use cache, return full list (backward-compatible)
        Map<String, Object> result = listFilesPaged(clusterId, pathStr, userId, 1, Integer.MAX_VALUE);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.get("files");
        return files;
    }

    /**
     * List files with server-side pagination and caching.
     * Returns a map with "files" (List<Map>) and "total" (int).
     */
    public Map<String, Object> listFilesPaged(String clusterId, String pathStr, Long userId, int page, int size) throws IOException {
        String cacheKey = clusterId + ":" + pathStr;
        CachedDirListing cached = fileListCache.get(cacheKey);

        if (cached == null || cached.isExpired()) {
            FileSystem fs = hadoopConfig.getFileSystem(clusterId);
            Path path = new Path(pathStr);
            if (!fs.exists(path)) {
                throw new IOException("Path does not exist: " + pathStr);
            }
            FileStatus[] statuses = fs.listStatus(path);
            List<Map<String, Object>> allFiles = Arrays.stream(statuses).map(this::fileStatusToMap).collect(Collectors.toList());
            cached = new CachedDirListing(allFiles);
            fileListCache.put(cacheKey, cached);
        }

        List<Map<String, Object>> allFiles = cached.allFiles;
        int total = allFiles.size();
        int start = (page - 1) * size;
        int end = Math.min(start + size, total);

        List<Map<String, Object>> pageFiles;
        if (start >= total) {
            pageFiles = Collections.emptyList();
        } else {
            pageFiles = allFiles.subList(start, end);
        }

        logOperation(userId, clusterId, "hdfs", "list", pathStr, "success", null);

        Map<String, Object> result = new HashMap<>();
        result.put("files", pageFiles);
        result.put("total", total);
        return result;
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
            logEntry.setUsername(AuditHelper.getUsernameById(userMapper, userId));
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

    public void uploadFile(String clusterId, String pathStr, java.io.InputStream inputStream, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        try (FSDataOutputStream out = fs.create(path, true)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
        logOperation(userId, clusterId, "hdfs", "upload", pathStr, "success", null);
    }

    public java.io.InputStream downloadFile(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (!fs.exists(path)) {
            throw new IOException("File not found: " + pathStr);
        }
        logOperation(userId, clusterId, "hdfs", "download", pathStr, "success", null);
        return fs.open(path);
    }

    public void createDirectory(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (fs.exists(path)) {
            throw new IOException("目录已存在: " + pathStr);
        }
        fs.mkdirs(path);
        logOperation(userId, clusterId, "hdfs", "mkdir", pathStr, "success", null);
    }

    public void setPermission(String clusterId, String pathStr, String permissionStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (!fs.exists(path)) {
            throw new IOException("路径不存在: " + pathStr);
        }
        // permissionStr 格式如 "755", "644", "777"
        short mode = (short) Integer.parseInt(permissionStr, 8);
        fs.setPermission(path, new FsPermission(mode));
        logOperation(userId, clusterId, "hdfs", "chmod", pathStr + " -> " + permissionStr, "success", null);
    }

    /**
     * Get ACL entries for a path.
     * Returns a list of ACL spec strings: "[type]:[name]:[perms]"
     */
    public List<String> getAcl(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (!fs.exists(path)) {
            throw new IOException("路径不存在: " + pathStr);
        }
        AclStatus aclStatus = fs.getAclStatus(path);
        List<String> entries = new ArrayList<>();
        for (AclEntry entry : aclStatus.getEntries()) {
            String type = entry.getType().name().toLowerCase();
            String name = entry.getName() != null ? entry.getName() : "";
            String perms = permissionToString(entry.getPermission());
            entries.add(type + ":" + name + ":" + perms);
        }
        logOperation(userId, clusterId, "hdfs", "getAcl", pathStr, "success", null);
        return entries;
    }

    /**
     * Set ACL entries on a path.
     * aclSpecs format: ["user:john:rwx", "group:dev:r-x", "other::r--", "mask::rwx"]
     */
    public void setAcl(String clusterId, String pathStr, List<String> aclSpecs, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (!fs.exists(path)) {
            throw new IOException("路径不存在: " + pathStr);
        }
        List<AclEntry> aclEntries = new ArrayList<>();
        for (String spec : aclSpecs) {
            aclEntries.add(parseAclSpec(spec));
        }
        fs.setAcl(path, aclEntries);
        logOperation(userId, clusterId, "hdfs", "setAcl", pathStr + " -> " + String.join(", ", aclSpecs), "success", null);
    }

    /**
     * Remove all ACL entries from a path (leaves only the base owner/group/other entries).
     */
    public void removeAcl(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (!fs.exists(path)) {
            throw new IOException("路径不存在: " + pathStr);
        }
        fs.removeAcl(path);
        logOperation(userId, clusterId, "hdfs", "removeAcl", pathStr, "success", null);
    }

    /**
     * Parse an ACL spec string like "user:john:rwx" into an AclEntry.
     */
    private AclEntry parseAclSpec(String spec) {
        String[] parts = spec.split(":", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("无效的 ACL 规范: " + spec + " (格式: type:name:perms)");
        }
        String typeStr = parts[0].toUpperCase();
        String name = parts[1];
        String permStr = parts[2];

        AclEntryType type;
        try {
            type = AclEntryType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的 ACL 类型: " + typeStr + " (可用: USER, GROUP, MASK, OTHER)");
        }

        FsAction permission;
        try {
            permission = FsAction.valueOf(permStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的权限: " + permStr + " (可用: rwx, r-x, r--, --- 等组合)");
        }

        AclEntry.Builder builder = new AclEntry.Builder();
        builder.setType(type);
        builder.setPermission(permission);
        if (!name.isEmpty()) {
            builder.setName(name);
        }
        return builder.build();
    }

    /**
     * Convert FsAction to string like "rwx", "r-x", "r--", "---".
     */
    private String permissionToString(FsAction action) {
        StringBuilder sb = new StringBuilder(3);
        sb.append(action.implies(FsAction.READ) ? 'r' : '-');
        sb.append(action.implies(FsAction.WRITE) ? 'w' : '-');
        sb.append(action.implies(FsAction.EXECUTE) ? 'x' : '-');
        return sb.toString();
    }

    public void renameFile(String clusterId, String oldPathStr, String newPathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path oldPath = new Path(oldPathStr);
        Path newPath = new Path(newPathStr);
        if (!fs.exists(oldPath)) {
            throw new IOException("源路径不存在: " + oldPathStr);
        }
        if (fs.exists(newPath)) {
            throw new IOException("目标路径已存在: " + newPathStr);
        }
        boolean renamed = fs.rename(oldPath, newPath);
        if (!renamed) {
            throw new IOException("重命名/移动失败（可能是权限不足或跨文件系统操作）");
        }
        logOperation(userId, clusterId, "hdfs", "rename", oldPathStr + " -> " + newPathStr, "success", null);
    }

    public byte[] readFilePreview(String clusterId, String pathStr, int maxBytes, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        Path path = new Path(pathStr);
        if (!fs.exists(path)) {
            throw new IOException("File not found: " + pathStr);
        }
        FileStatus status = fs.getFileStatus(path);
        if (status.isDirectory()) {
            throw new IOException("Cannot preview a directory: " + pathStr);
        }
        int bytesToRead = Math.min(maxBytes, (int) Math.min(status.getLen(), Integer.MAX_VALUE));
        byte[] buffer = new byte[bytesToRead];
        try (FSDataInputStream in = fs.open(path)) {
            int totalRead = 0;
            int remaining = bytesToRead;
            while (remaining > 0) {
                int read = in.read(buffer, totalRead, remaining);
                if (read == -1) break; // end of stream
                totalRead += read;
                remaining -= read;
            }
            if (totalRead < bytesToRead) {
                byte[] exact = new byte[totalRead];
                System.arraycopy(buffer, 0, exact, 0, totalRead);
                return exact;
            }
            return buffer;
        }
    }

    public void deleteFile(String clusterId, String pathStr, Long userId) throws IOException {
        deleteFile(clusterId, pathStr, userId, true);
    }

    public void deleteFile(String clusterId, String pathStr, Long userId, boolean useTrash) throws IOException {
        if (useTrash) {
            moveToTrash(clusterId, pathStr, userId);
        } else {
            FileSystem fs = hadoopConfig.getFileSystem(clusterId);
            Path path = new Path(pathStr);
            if (!fs.exists(path)) {
                throw new IOException("路径不存在: " + pathStr);
            }
            FileStatus status = fs.getFileStatus(path);
            boolean recursive = status.isDirectory();
            // HDFS 原生权限系统会自动校验：写父目录权限、文件自身权限
            // 无权限时抛出 AccessControlException
            boolean deleted = fs.delete(path, recursive);
            if (!deleted) {
                throw new IOException("删除失败（可能是权限不足或无此路径）: " + pathStr);
            }
            logOperation(userId, clusterId, "hdfs", "delete", pathStr + (recursive ? " (递归)" : ""), "success", null);
        }
    }

    /**
     * Search files across HDFS by name pattern (case-insensitive).
     * Recursively lists files from root / and filters by name containing the query.
     * Limited by maxResults to avoid excessive scanning.
     */
    public List<Map<String, Object>> searchFiles(String clusterId, String query, int maxResults, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        List<Map<String, Object>> results = new ArrayList<>();
        String q = query.toLowerCase();
        // Use globStatus with wildcard pattern if query doesn't contain wildcards
        // For simplicity, recursively list from root using listFiles (which handles deep listing)
        // but with a depth limit — use listFiles which supports recursive listing
        RemoteIterator<LocatedFileStatus> iterator = fs.listFiles(new Path("/"), true);
        while (iterator.hasNext() && results.size() < maxResults) {
            LocatedFileStatus status = iterator.next();
            String name = status.getPath().getName().toLowerCase();
            if (name.contains(q)) {
                Map<String, Object> item = fileStatusToMap(status);
                item.put("isDirectory", false); // listFiles only returns files
                results.add(item);
            }
        }
        // Also list directories recursively using listStatus
        if (results.size() < maxResults) {
            searchDirectoriesRecursive(fs, new Path("/"), q, results, maxResults, new java.util.HashSet<>());
        }
        logOperation(userId, clusterId, "hdfs", "search", "query=" + query, "success", "results=" + results.size());
        return results;
    }

    private void searchDirectoriesRecursive(FileSystem fs, Path dir, String query, List<Map<String, Object>> results, int maxResults, java.util.Set<String> visited) throws IOException {
        if (results.size() >= maxResults) return;
        String dirStr = dir.toString();
        if (visited.contains(dirStr)) return;
        visited.add(dirStr);
        FileStatus[] statuses;
        try {
            statuses = fs.listStatus(dir);
        } catch (Exception e) {
            return; // Skip directories we can't read
        }
        for (FileStatus status : statuses) {
            if (results.size() >= maxResults) return;
            String name = status.getPath().getName().toLowerCase();
            if (name.contains(query)) {
                Map<String, Object> item = fileStatusToMap(status);
                results.add(item);
            }
            if (status.isDirectory()) {
                searchDirectoriesRecursive(fs, status.getPath(), query, results, maxResults, visited);
            }
        }
    }

    // === Trash / Recycle Bin Operations ===

    /**
     * Move a file/directory to HDFS trash: /user/root/.Trash/Current/<path>
     * If .Trash dir doesn't exist, creates it.
     */
    public void moveToTrash(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);

        // 规范化路径：去掉 hdfs://nameservice 前缀
        String normalizedPath = pathStr;
        if (normalizedPath.startsWith("hdfs://")) {
            int idx = normalizedPath.indexOf("/", normalizedPath.indexOf("//") + 2);
            if (idx >= 0) {
                normalizedPath = normalizedPath.substring(idx);
            }
        }

        Path srcPath = new Path(normalizedPath);

        if (!fs.exists(srcPath)) {
            throw new IOException("路径不存在: " + normalizedPath);
        }

        // Determine the trash root directory
        String trashRoot = "/user/root/.Trash/Current";
        Path trashRootPath = new Path(trashRoot);

        // Ensure .Trash directory exists
        if (!fs.exists(trashRootPath)) {
            fs.mkdirs(trashRootPath);
        }

        // Build the trash destination path preserving the original path structure
        // If path is /data/test.txt, it goes to /user/root/.Trash/Current/data/test.txt
        String relativePath = normalizedPath.startsWith("/") ? normalizedPath.substring(1) : normalizedPath;
        Path trashPath = new Path(trashRoot, relativePath);

        // If the trash destination already exists, append a timestamp suffix
        if (fs.exists(trashPath)) {
            String ts = String.valueOf(System.currentTimeMillis());
            String parentDir = relativePath.contains("/") ? relativePath.substring(0, relativePath.lastIndexOf("/")) : "";
            String fileName = relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf("/") + 1) : relativePath;
            String newRelative = (parentDir.isEmpty() ? "" : parentDir + "/") + fileName + "." + ts;
            trashPath = new Path(trashRoot, newRelative);
        }

        // Ensure parent directory of trash destination exists
        Path trashParent = trashPath.getParent();
        if (!fs.exists(trashParent)) {
            fs.mkdirs(trashParent);
        }

        boolean renamed = fs.rename(srcPath, trashPath);
        if (!renamed) {
            throw new IOException("移动到回收站失败: " + pathStr);
        }

        logOperation(userId, clusterId, "hdfs", "trash", pathStr + " -> " + trashPath.toString(), "success", null);
    }

    /**
     * Restore a file from trash back to its original location.
     * pathStr should be the path relative to trash root, e.g. /data/test.txt
     */
    public void restoreFromTrash(String clusterId, String pathStr, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);

        String trashRoot = "/user/root/.Trash/Current";
        String relativePath = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;
        Path trashPath = new Path(trashRoot, relativePath);

        if (!fs.exists(trashPath)) {
            throw new IOException("回收站中不存在该路径: " + pathStr);
        }

        // Restore to original location
        Path originalPath = new Path("/" + relativePath);

        // Check if original path already exists
        if (fs.exists(originalPath)) {
            throw new IOException("原始路径已存在，无法恢复: " + originalPath.toString());
        }

        // Ensure parent directory exists
        Path parentPath = originalPath.getParent();
        if (!fs.exists(parentPath)) {
            fs.mkdirs(parentPath);
        }

        boolean renamed = fs.rename(trashPath, originalPath);
        if (!renamed) {
            throw new IOException("从回收站恢复失败: " + pathStr);
        }

        logOperation(userId, clusterId, "hdfs", "restore", trashPath.toString() + " -> " + originalPath.toString(), "success", null);
    }

    /**
     * List all files in the trash directory.
     * Returns file info including name, original path, size, and modification time.
     */
    public List<Map<String, Object>> listTrash(String clusterId, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        String trashRoot = "/user/root/.Trash/Current";
        Path trashRootPath = new Path(trashRoot);

        if (!fs.exists(trashRootPath)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        listTrashRecursive(fs, trashRootPath, result);
        return result;
    }

    private void listTrashRecursive(FileSystem fs, Path dir, List<Map<String, Object>> result) throws IOException {
        FileStatus[] statuses = fs.listStatus(dir);
        for (FileStatus status : statuses) {
            String fullPath = status.getPath().toString();
            String trashRoot = "/user/root/.Trash/Current";

            // Compute original path: remove trash prefix
            String relativePath = fullPath;
            if (relativePath.startsWith("hdfs://")) {
                // Strip the scheme+authority if present
                int pathStart = relativePath.indexOf("/", relativePath.indexOf("://") + 3);
                if (pathStart > 0) {
                    relativePath = relativePath.substring(pathStart);
                }
            }
            String originalPath = "";
            if (relativePath.startsWith(trashRoot)) {
                originalPath = "/" + relativePath.substring(trashRoot.length()).replaceAll("^/+", "");
            }

            Map<String, Object> item = fileStatusToMap(status);
            item.put("originalPath", originalPath);
            item.put("trashPath", relativePath);
            item.put("deletionTime", status.getModificationTime());

            if (status.isDirectory()) {
                // Recursively list contents of directories, but also add the directory itself
                result.add(item);
                listTrashRecursive(fs, status.getPath(), result);
            } else {
                result.add(item);
            }
        }
    }

    /**
     * Permanently delete all files in the trash directory.
     */
    public void emptyTrash(String clusterId, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        String trashRoot = "/user/root/.Trash/Current";
        Path trashRootPath = new Path(trashRoot);

        if (!fs.exists(trashRootPath)) {
            return; // trash is already empty
        }

        FileStatus[] statuses = fs.listStatus(trashRootPath);
        boolean deleted = fs.delete(trashRootPath, true);
        if (!deleted) {
            throw new IOException("清空回收站失败");
        }

        // Re-create the trash directory for future use
        fs.mkdirs(trashRootPath);

        int count = statuses != null ? statuses.length : 0;
        logOperation(userId, clusterId, "hdfs", "emptyTrash", "清空回收站: " + count + " 项", "success", null);
    }

    /**
     * Permanently delete only the expired files in the trash directory,
     * based on the configured retention days.
     * Items older than retentionDays are permanently deleted.
     * Returns the number of deleted items.
     */
    public int cleanExpiredTrash(String clusterId, Long userId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        String trashRoot = "/user/root/.Trash/Current";
        Path trashRootPath = new Path(trashRoot);

        if (!fs.exists(trashRootPath)) {
            return 0; // trash is empty
        }

        int retentionDays = props.getTrash().getRetentionDays();
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
        int deletedCount = 0;

        // Collect all trash items
        List<FileStatus> allItems = new ArrayList<>();
        collectTrashItemsForCleanup(fs, trashRootPath, allItems);

        // Find and delete expired items
        for (FileStatus item : allItems) {
            if (item.getModificationTime() < cutoffTime) {
                try {
                    fs.delete(item.getPath(), true);
                    deletedCount++;
                } catch (IOException e) {
                    log.warn("Failed to delete expired trash item: {}", item.getPath(), e);
                }
            }
        }

        logOperation(userId, clusterId, "hdfs", "cleanExpiredTrash",
                "清理过期回收站文件: " + deletedCount + " 项, 保留天数: " + retentionDays, "success", null);
        log.info("Manual trash cleanup completed: deleted {} expired items (retention: {} days)", deletedCount, retentionDays);
        return deletedCount;
    }

    private void collectTrashItemsForCleanup(FileSystem fs, Path dir, List<FileStatus> items) throws IOException {
        FileStatus[] statuses = fs.listStatus(dir);
        for (FileStatus status : statuses) {
            items.add(status);
            if (status.isDirectory()) {
                collectTrashItemsForCleanup(fs, status.getPath(), items);
            }
        }
    }

    /**
     * Check if restoring a file from trash would cause a conflict (target path already exists).
     * Returns a map with: hasConflict (boolean), existingPath (String).
     */
    public Map<String, Object> checkRestoreConflict(String clusterId, String pathStr) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        String relativePath = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;
        Path originalPath = new Path("/" + relativePath);
        Map<String, Object> result = new HashMap<>();
        result.put("hasConflict", fs.exists(originalPath));
        result.put("existingPath", originalPath.toString());
        return result;
    }

    /**
     * Preview the impact of permanently deleting items from trash.
     * Returns totalCount and totalSize of all trash items.
     */
    public Map<String, Object> previewDeleteImpact(String clusterId) throws IOException {
        FileSystem fs = hadoopConfig.getFileSystem(clusterId);
        String trashRoot = "/user/root/.Trash/Current";
        Path trashRootPath = new Path(trashRoot);

        if (!fs.exists(trashRootPath)) {
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", 0);
            result.put("totalSize", 0L);
            return result;
        }

        long totalSize = 0;
        int totalCount = 0;
        List<FileStatus> allItems = new ArrayList<>();
        collectTrashItems(fs, trashRootPath, allItems);

        for (FileStatus item : allItems) {
            totalCount++;
            totalSize += item.getLen();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", totalCount);
        result.put("totalSize", totalSize);
        return result;
    }

    private void collectTrashItems(FileSystem fs, Path dir, List<FileStatus> items) throws IOException {
        FileStatus[] statuses = fs.listStatus(dir);
        for (FileStatus status : statuses) {
            items.add(status);
            if (status.isDirectory()) {
                collectTrashItems(fs, status.getPath(), items);
            }
        }
    }

    // ================================================================
    // DataNode Decommission / Maintenance Mode
    // ================================================================

    public String decommissionDataNode(String clusterId, String hostname, boolean decom) throws Exception {
        String[] cmd;
        if (decom) {
            cmd = new String[]{"hdfs", "dfsadmin", "-decommission", hostname};
        } else {
            cmd = new String[]{"hdfs", "dfsadmin", "-recommission", hostname};
        }
        Process p = Runtime.getRuntime().exec(cmd);
        int exit = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        log.info("decommissionDataNode: hostname={}, decom={}, exit={}, out={}, err={}", hostname, decom, exit, out, err);
        if (exit != 0 && !err.isEmpty()) {
            throw new IOException("decommission failed: " + err);
        }
        return out.isEmpty() ? "ok" : out;
    }

    public String triggerRebalance(String clusterId) throws Exception {
        String[] cmd = new String[]{"hdfs", "balancer", "-threshold", "10"};
        Process p = Runtime.getRuntime().exec(cmd);
        int exit = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes());
        String err = new String(p.getErrorStream().readAllBytes());
        log.info("triggerRebalance: exit={}, out={}, err={}", exit, out, err);
        if (exit != 0 && !err.isEmpty()) {
            throw new IOException("rebalance failed: " + err);
        }
        return out.isEmpty() ? "ok" : out;
    }

    // ================================================================
    // HDFS Health Metrics (NN JMX)
    // ================================================================

    public Map<String, Object> getHealthMetrics(String clusterId) {
        Map<String, Object> result = new HashMap<>();
        try {
            String nnWeb = props.getHdfs().getNn1Web();
            String url = "http://" + nnWeb + "/jmx?qry=Hadoop:service=NameNode,name=FSNamesystem";

            String json = httpGet(url);
            ObjectMapper mapper = new ObjectMapper();
            Map<?, ?> root = mapper.readValue(json, Map.class);
            Object beans = root.get("beans");
            if (beans instanceof List && !((List<?>) beans).isEmpty()) {
                Map<?, ?> bean = (Map<?, ?>) ((List<?>) beans).get(0);

                Map<String, Object> metrics = new HashMap<>();
                metrics.put("UnderReplicatedBlocks", getLong(bean, "UnderReplicatedBlocks"));
                metrics.put("MissingBlocks", getLong(bean, "MissingBlocks"));
                metrics.put("CorruptBlocks", getLong(bean, "CorruptBlocks"));
                metrics.put("BlocksTotal", getLong(bean, "BlocksTotal"));
                metrics.put("FilesTotal", getLong(bean, "FilesTotal"));
                metrics.put("TotalLoad", getInt(bean, "TotalLoad"));
                metrics.put("NumLiveDataNodes", getInt(bean, "NumLiveDataNodes"));
                metrics.put("NumDeadDataNodes", getInt(bean, "NumDeadDataNodes"));
                metrics.put("CapacityTotal", getLong(bean, "CapacityTotal"));
                metrics.put("CapacityRemaining", getLong(bean, "CapacityRemaining"));
                metrics.put("CapacityUsed", getLong(bean, "CapacityUsed"));
                metrics.put("CapacityRemainingPercent", getDouble(bean, "CapacityRemainingPercent"));
                metrics.put("CapacityUsedPercent", getDouble(bean, "CapacityUsedPercent"));
                metrics.put("PendingReplicationBlocks", getLong(bean, "PendingReplicationBlocks"));
                metrics.put("ScheduledReplicationBlocks", getLong(bean, "ScheduledReplicationBlocks"));

                result.put("status", "healthy");
                result.put("metrics", metrics);
            } else {
                result.put("status", "error");
                result.put("error", "No JMX beans found for FSNamesystem");
            }
        } catch (Exception e) {
            // Fallback to NN2
            try {
                String nnWeb = props.getHdfs().getNn2Web();
                String url = "http://" + nnWeb + "/jmx?qry=Hadoop:service=NameNode,name=FSNamesystem";
                String json = httpGet(url);
                ObjectMapper mapper = new ObjectMapper();
                Map<?, ?> root = mapper.readValue(json, Map.class);
                Object beans = root.get("beans");
                if (beans instanceof List && !((List<?>) beans).isEmpty()) {
                    Map<?, ?> bean = (Map<?, ?>) ((List<?>) beans).get(0);
                    Map<String, Object> metrics = new HashMap<>();
                    metrics.put("UnderReplicatedBlocks", getLong(bean, "UnderReplicatedBlocks"));
                    metrics.put("MissingBlocks", getLong(bean, "MissingBlocks"));
                    metrics.put("CorruptBlocks", getLong(bean, "CorruptBlocks"));
                    metrics.put("BlocksTotal", getLong(bean, "BlocksTotal"));
                    metrics.put("FilesTotal", getLong(bean, "FilesTotal"));
                    metrics.put("TotalLoad", getInt(bean, "TotalLoad"));
                    metrics.put("NumLiveDataNodes", getInt(bean, "NumLiveDataNodes"));
                    metrics.put("NumDeadDataNodes", getInt(bean, "NumDeadDataNodes"));
                    metrics.put("CapacityTotal", getLong(bean, "CapacityTotal"));
                    metrics.put("CapacityRemaining", getLong(bean, "CapacityRemaining"));
                    metrics.put("CapacityUsed", getLong(bean, "CapacityUsed"));
                    metrics.put("CapacityRemainingPercent", getDouble(bean, "CapacityRemainingPercent"));
                    metrics.put("CapacityUsedPercent", getDouble(bean, "CapacityUsedPercent"));
                    metrics.put("PendingReplicationBlocks", getLong(bean, "PendingReplicationBlocks"));
                    metrics.put("ScheduledReplicationBlocks", getLong(bean, "ScheduledReplicationBlocks"));
                    result.put("status", "healthy");
                    result.put("metrics", metrics);
                } else {
                    result.put("status", "error");
                    result.put("error", "No JMX beans found on either NameNode");
                }
            } catch (Exception e2) {
                result.put("status", "error");
                result.put("error", "NN1: " + e.getMessage() + "; NN2: " + e2.getMessage());
            }
        }
        return result;
    }

    // ================================================================
    // DataNode JMX Metrics
    // ================================================================

    public List<Map<String, Object>> getDataNodeMetrics(String clusterId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            FileSystem fs = hadoopConfig.getFileSystem(clusterId);
            if (fs instanceof DistributedFileSystem) {
                DistributedFileSystem dfs = (DistributedFileSystem) fs;
                DatanodeInfo[] dns = dfs.getDataNodeStats();
                for (DatanodeInfo dn : dns) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("hostName", dn.getHostName());
                    m.put("name", dn.getName());
                    m.put("adminState", dn.getAdminState().name());
                    m.put("lastUpdate", dn.getLastUpdate());
                    m.put("lastUpdateDiff", System.currentTimeMillis() - dn.getLastUpdate());
                    m.put("capacity", dn.getCapacity());
                    m.put("dfsUsed", dn.getDfsUsed());
                    m.put("remaining", dn.getRemaining());
                    m.put("nonDfsUsed", dn.getNonDfsUsed());
                    m.put("numBlocks", dn.getNumBlocks());
                    m.put("xceiverCount", dn.getXceiverCount());
                    m.put("networkLocation", dn.getNetworkLocation());
                    m.put("ipAddr", dn.getIpAddr());
                    m.put("infoAddr", dn.getInfoAddr());
                    m.put("infoSecureAddr", dn.getInfoSecureAddr());
                    m.put("isDecommissioned", dn.isDecommissioned());
                    m.put("isEnteringMaintenance", dn.isEnteringMaintenance());
                    m.put("isInMaintenance", dn.isInMaintenance());
                    result.add(m);
                }
            }
        } catch (Exception e) {
            log.error("Failed to get DataNode metrics for cluster {}", clusterId, e);
        }
        return result;
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + " from " + urlStr);
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private long getLong(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }

    private int getInt(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }

    private double getDouble(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }
}