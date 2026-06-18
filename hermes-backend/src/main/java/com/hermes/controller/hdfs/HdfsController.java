package com.hermes.controller.hdfs;

import com.hermes.config.HermesProperties;
import com.hermes.service.hdfs.HdfsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.hermes.entity.FileNote;
import com.hermes.mapper.FileNoteMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/hdfs")
public class HdfsController {

    @Autowired
    private HdfsService hdfsService;

    @Autowired
    private HermesProperties props;

    @Autowired(required = false)
    private FileNoteMapper fileNoteMapper;

    @GetMapping("/files")
    public Map<String, Object> listFiles(@RequestParam(defaultValue = "cluster1") String clusterId,
                                         @RequestParam(defaultValue = "/") String path,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "100") int size,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            return success(hdfsService.listFilesPaged(clusterId, path, userId, page, size));
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

    @PostMapping("/upload")
    public Map<String, Object> uploadFile(@RequestParam(defaultValue = "cluster1") String clusterId,
                                           @RequestParam String path,
                                           @RequestParam("file") MultipartFile file,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        String fullPath = path.endsWith("/") ? path + file.getOriginalFilename() : path + "/" + file.getOriginalFilename();
        try {
            hdfsService.uploadFile(clusterId, fullPath, file.getInputStream(), userId);
            return success(Map.of("path", fullPath, "size", file.getSize(), "name", file.getOriginalFilename()));
        } catch (Exception e) {
            return error(500, "上传失败: " + e.getMessage());
        }
    }

    // 备选上传方式：文件内容直接放在 request body，文件名由参数指定
    @PostMapping("/upload/raw")
    public Map<String, Object> uploadRaw(@RequestParam(defaultValue = "cluster1") String clusterId,
                                          @RequestParam String path,
                                          @RequestBody byte[] fileContent,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
        try {
            hdfsService.uploadFile(clusterId, path, new java.io.ByteArrayInputStream(fileContent), userId);
            return success(Map.of("path", path, "size", fileContent.length, "name", fileName));
        } catch (Exception e) {
            return error(500, "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/preview")
    public Map<String, Object> filePreview(@RequestParam(defaultValue = "cluster1") String clusterId,
                                            @RequestParam String path,
                                            @RequestParam(defaultValue = "65536") int maxBytes,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            // Validate it's a file, not directory
            Map<String, Object> status = hdfsService.getFileStatus(clusterId, path, userId);
            if (status != null && Boolean.TRUE.equals(status.get("isDirectory"))) {
                return error(400, "Cannot preview a directory: " + path);
            }
            long fileSize = status != null && status.get("length") instanceof Number
                    ? ((Number) status.get("length")).longValue() : 0;
            byte[] data = hdfsService.readFilePreview(clusterId, path, maxBytes, userId);
            String contentBase64 = Base64.getEncoder().encodeToString(data);
            boolean isTruncated = data.length < fileSize && data.length >= maxBytes;
            if (data.length < maxBytes) {
                isTruncated = false; // file is smaller than maxBytes
            }
            // Recalculate: truncated if file is larger than what we read
            isTruncated = fileSize > data.length;
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
            return success(Map.of(
                "content", contentBase64,
                "fileName", fileName,
                "fileSize", fileSize,
                "previewSize", data.length,
                "isTruncated", isTruncated
            ));
        } catch (Exception e) {
            return error(500, "Preview failed: " + e.getMessage());
        }
    }

    @GetMapping("/download")
    public void downloadFile(@RequestParam(defaultValue = "cluster1") String clusterId,
                              @RequestParam String path,
                              @AuthenticationPrincipal UserDetails userDetails,
                              HttpServletResponse response) throws Exception {
        Long userId = userDetails != null ? 1L : 1L;
        try (java.io.InputStream in = hdfsService.downloadFile(clusterId, path, userId)) {
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                response.getOutputStream().write(buffer, 0, len);
            }
            response.getOutputStream().flush();
        } catch (Exception e) {
            response.setStatus(500);
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write("下载失败: " + e.getMessage());
        }
    }

    @PostMapping("/mkdir")
    public Map<String, Object> createDir(@RequestParam(defaultValue = "cluster1") String clusterId,
                                          @RequestParam String path,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            hdfsService.createDirectory(clusterId, path, userId);
            return success(Map.of("path", path));
        } catch (Exception e) {
            return error(500, "创建目录失败: " + e.getMessage());
        }
    }

    @PostMapping("/chmod")
    public Map<String, Object> changePermission(@RequestParam(defaultValue = "cluster1") String clusterId,
                                                  @RequestParam String path,
                                                  @RequestParam String mode,  // 如 755, 644, 777
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            hdfsService.setPermission(clusterId, path, mode, userId);
            return success(Map.of("path", path, "mode", mode));
        } catch (Exception e) {
            return error(500, "修改权限失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/hdfs/acl?path=/xxx — returns ACL entries
     */
    @GetMapping("/acl")
    public Map<String, Object> getAcl(@RequestParam(defaultValue = "cluster1") String clusterId,
                                       @RequestParam String path,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            List<String> entries = hdfsService.getAcl(clusterId, path, userId);
            return success(Map.of("path", path, "entries", entries));
        } catch (Exception e) {
            return error(500, "获取 ACL 失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/hdfs/acl?path=/xxx
     * Body: {"aclSpecs": ["user:john:rwx", "group:dev:r-x"]}
     */
    @PostMapping("/acl")
    public Map<String, Object> setAcl(@RequestParam(defaultValue = "cluster1") String clusterId,
                                       @RequestParam String path,
                                       @RequestBody Map<String, Object> body,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            @SuppressWarnings("unchecked")
            List<String> aclSpecs = (List<String>) body.get("aclSpecs");
            if (aclSpecs == null || aclSpecs.isEmpty()) {
                return error(400, "aclSpecs 不能为空");
            }
            hdfsService.setAcl(clusterId, path, aclSpecs, userId);
            return success(Map.of("path", path, "aclSpecs", aclSpecs));
        } catch (Exception e) {
            return error(500, "设置 ACL 失败: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/hdfs/acl?path=/xxx — removes all ACL entries
     */
    @DeleteMapping("/acl")
    public Map<String, Object> removeAcl(@RequestParam(defaultValue = "cluster1") String clusterId,
                                          @RequestParam String path,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            hdfsService.removeAcl(clusterId, path, userId);
            return success(Map.of("path", path, "message", "ACL 已清除"));
        } catch (Exception e) {
            return error(500, "清除 ACL 失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public Map<String, Object> deletePath(@RequestParam(defaultValue = "cluster1") String clusterId,
                                           @RequestParam String path,
                                           @RequestParam(defaultValue = "true") boolean useTrash,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            hdfsService.deleteFile(clusterId, path, userId, useTrash);
            Map<String, Object> data = new HashMap<>();
            data.put("path", path);
            data.put("useTrash", useTrash);
            if (useTrash) {
                data.put("message", "已移动到回收站");
            } else {
                data.put("message", "已永久删除");
            }
            return success(data);
        } catch (org.apache.hadoop.security.AccessControlException e) {
            return error(403, "权限不足，无法删除: " + path + "（需要写权限）");
        } catch (Exception e) {
            return error(500, "删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/rename")
    public Map<String, Object> renamePath(@RequestParam(defaultValue = "cluster1") String clusterId,
                                           @RequestParam String path,
                                           @RequestParam("newPath") String newPath,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            hdfsService.renameFile(clusterId, path, newPath, userId);
            return success(Map.of("oldPath", path, "newPath", newPath));
        } catch (org.apache.hadoop.security.AccessControlException e) {
            return error(403, "权限不足，无法重命名: " + path + "（需要写权限）");
        } catch (Exception e) {
            return error(500, "重命名失败: " + e.getMessage());
        }
    }

    // === Global HDFS Search ===

    @GetMapping("/search")
    public Map<String, Object> searchFiles(@RequestParam(defaultValue = "cluster1") String clusterId,
                                            @RequestParam String query,
                                            @RequestParam(defaultValue = "100") int maxResults,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            List<Map<String, Object>> results = hdfsService.searchFiles(clusterId, query, maxResults, userId);
            return success(results);
        } catch (Exception e) {
            return error(500, "搜索失败: " + e.getMessage());
        }
    }

    // === File Notes (Local DB) ===

    @GetMapping("/notes")
    public Map<String, Object> getNote(@RequestParam(defaultValue = "cluster1") String clusterId,
                                        @RequestParam String path) {
        try {
            if (fileNoteMapper == null) return success(null);
            FileNote note = fileNoteMapper.findByClusterIdAndPath(clusterId, path);
            return success(note != null ? Map.of(
                "id", note.getId(),
                "clusterId", note.getClusterId(),
                "path", note.getPath(),
                "note", note.getNote(),
                "createdBy", note.getCreatedBy(),
                "createTime", note.getCreateTime(),
                "updateTime", note.getUpdateTime()
            ) : null);
        } catch (Exception e) {
            return error(500, "获取笔记失败: " + e.getMessage());
        }
    }

    @PostMapping("/notes")
    public Map<String, Object> saveNote(@RequestParam(defaultValue = "cluster1") String clusterId,
                                         @RequestBody Map<String, Object> body,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (fileNoteMapper == null) return error(500, "笔记服务不可用");
            String path = (String) body.get("path");
            String note = (String) body.get("note");
            String createdBy = userDetails != null ? userDetails.getUsername() : "anonymous";
            if (path == null) return error(400, "path 不能为空");

            FileNote existing = fileNoteMapper.findByClusterIdAndPath(clusterId, path);
            if (existing != null) {
                existing.setNote(note);
                existing.setUpdateTime(LocalDateTime.now());
                fileNoteMapper.updateById(existing);
            } else {
                FileNote newNote = new FileNote();
                newNote.setClusterId(clusterId);
                newNote.setPath(path);
                newNote.setNote(note);
                newNote.setCreatedBy(createdBy);
                newNote.setCreateTime(LocalDateTime.now());
                newNote.setUpdateTime(LocalDateTime.now());
                fileNoteMapper.insert(newNote);
            }
            return success(Map.of("path", path, "note", note));
        } catch (Exception e) {
            return error(500, "保存笔记失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/notes")
    public Map<String, Object> deleteNote(@RequestParam(defaultValue = "cluster1") String clusterId,
                                           @RequestParam String path) {
        try {
            if (fileNoteMapper == null) return error(500, "笔记服务不可用");
            FileNote existing = fileNoteMapper.findByClusterIdAndPath(clusterId, path);
            if (existing != null) {
                fileNoteMapper.deleteById(existing.getId());
            }
            return success(Map.of("path", path, "deleted", true));
        } catch (Exception e) {
            return error(500, "删除笔记失败: " + e.getMessage());
        }
    }

    // === Trash / Recycle Bin Endpoints ===

    @PostMapping("/trash")
    public Map<String, Object> moveToTrash(@RequestParam(defaultValue = "cluster1") String clusterId,
                                            @RequestParam String path,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            hdfsService.moveToTrash(clusterId, path, userId);
            return success(Map.of("path", path, "message", "已移动到回收站"));
        } catch (org.apache.hadoop.security.AccessControlException e) {
            return error(403, "权限不足，无法移动到回收站: " + path + "（需要写权限）");
        } catch (Exception e) {
            return error(500, "移动到回收站失败: " + e.getMessage());
        }
    }

    @PostMapping("/trash/restore")
    public Map<String, Object> restoreFromTrash(@RequestParam(defaultValue = "cluster1") String clusterId,
                                                 @RequestParam String path,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            hdfsService.restoreFromTrash(clusterId, path, userId);
            return success(Map.of("path", path, "message", "已从回收站恢复"));
        } catch (org.apache.hadoop.security.AccessControlException e) {
            return error(403, "权限不足，无法从回收站恢复: " + path + "（需要写权限）");
        } catch (Exception e) {
            return error(500, "从回收站恢复失败: " + e.getMessage());
        }
    }

    @GetMapping("/trash/list")
    public Map<String, Object> listTrash(@RequestParam(defaultValue = "cluster1") String clusterId,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            List<Map<String, Object>> trashFiles = hdfsService.listTrash(clusterId, userId);
            return success(trashFiles);
        } catch (Exception e) {
            return error(500, "获取回收站列表失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/hdfs/trash/retention — get retention policy config (days)
     */
    @GetMapping("/trash/retention")
    public Map<String, Object> getTrashRetention() {
        try {
            HermesProperties.Trash trash = props.getTrash();
            return success(Map.of("retentionDays", trash.getRetentionDays()));
        } catch (Exception e) {
            return error(500, "获取回收站保留策略失败: " + e.getMessage());
        }
    }

    /**
     * PUT /api/v1/hdfs/trash/retention — update retention policy config (days)
     * Body: {"retentionDays": 30}
     */
    @PutMapping("/trash/retention")
    public Map<String, Object> updateTrashRetention(@RequestBody Map<String, Object> body) {
        try {
            Object daysObj = body.get("retentionDays");
            if (daysObj == null) {
                return error(400, "retentionDays 不能为空");
            }
            int days = ((Number) daysObj).intValue();
            if (days < 1 || days > 365) {
                return error(400, "retentionDays 必须在 1~365 之间");
            }
            props.getTrash().setRetentionDays(days);
            return success(Map.of("retentionDays", days, "message", "回收站保留策略已更新"));
        } catch (Exception e) {
            return error(500, "更新回收站保留策略失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/hdfs/trash/restore-check — check for conflicts before restoring
     * Body: {"path": "/data/test.txt"}  (the original path)
     */
    @PostMapping("/trash/restore-check")
    public Map<String, Object> checkRestoreConflict(@RequestParam(defaultValue = "cluster1") String clusterId,
                                                     @RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            String path = (String) body.get("path");
            if (path == null) {
                return error(400, "path 不能为空");
            }
            Map<String, Object> result = hdfsService.checkRestoreConflict(clusterId, path);
            return success(result);
        } catch (Exception e) {
            return error(500, "检查恢复冲突失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/hdfs/trash/delete-preview — preview delete impact
     */
    @PostMapping("/trash/delete-preview")
    public Map<String, Object> previewDeleteImpact(@RequestParam(defaultValue = "cluster1") String clusterId,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            Map<String, Object> result = hdfsService.previewDeleteImpact(clusterId);
            return success(result);
        } catch (Exception e) {
            return error(500, "预览删除影响失败: " + e.getMessage());
        }
    }

    @PostMapping("/trash/empty")
    public Map<String, Object> emptyTrash(@RequestParam(defaultValue = "cluster1") String clusterId,
                                           @RequestParam(defaultValue = "false") boolean confirmed,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        if (!confirmed) {
            return error(400, "请确认要清空回收站（confirmed=true）");
        }
        try {
            hdfsService.emptyTrash(clusterId, userId);
            return success(Map.of("message", "回收站已清空"));
        } catch (Exception e) {
            return error(500, "清空回收站失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/hdfs/trash/clean-expired — trigger immediate cleanup of expired trash items
     * based on the configured retention days.
     */
    @PostMapping("/trash/clean-expired")
    public Map<String, Object> cleanExpiredTrash(@RequestParam(defaultValue = "cluster1") String clusterId,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            int deletedCount = hdfsService.cleanExpiredTrash(clusterId, userId);
            return success(Map.of("deletedCount", deletedCount, "message", "清理完成，已删除 " + deletedCount + " 个过期文件"));
        } catch (Exception e) {
            return error(500, "清理过期文件失败: " + e.getMessage());
        }
    }

    @PostMapping("/move")
    public Map<String, Object> movePath(@RequestParam(defaultValue = "cluster1") String clusterId,
                                         @RequestParam String path,
                                         @RequestParam("destDir") String destDir,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? 1L : 1L;
        try {
            // Extract filename from source path
            String srcPath = path;
            String fileName = srcPath.contains("/") ? srcPath.substring(srcPath.lastIndexOf("/") + 1) : srcPath;
            String destPath = destDir.endsWith("/") ? destDir + fileName : destDir + "/" + fileName;
            hdfsService.renameFile(clusterId, srcPath, destPath, userId);
            return success(Map.of("srcPath", srcPath, "destDir", destDir, "destPath", destPath));
        } catch (org.apache.hadoop.security.AccessControlException e) {
            return error(403, "权限不足，无法移动: " + path + "（需要写权限）");
        } catch (Exception e) {
            return error(500, "移动失败: " + e.getMessage());
        }
    }

    // === HDFS Health Endpoint (NN JMX) ===

    /**
     * GET /api/v1/hdfs/health — NameNode JMX health metrics
     */
    @GetMapping("/health")
    public Map<String, Object> getHealth(@RequestParam(defaultValue = "cluster1") String clusterId) {
        try {
            return success(hdfsService.getHealthMetrics(clusterId));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    // === DataNode JMX Metrics ===

    /**
     * GET /api/v1/hdfs/datanodes/metrics — DataNode metrics via NN JMX
     */
    @GetMapping("/datanodes/metrics")
    public Map<String, Object> getDataNodeMetrics(@RequestParam(defaultValue = "cluster1") String clusterId) {
        try {
            return success(hdfsService.getDataNodeMetrics(clusterId));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @GetMapping("/nodes")
    public Map<String, Object> getNodes() {
        RestTemplate rt = new RestTemplate();
        Map<String, Object> result = new HashMap<>();
        HermesProperties.Hdfs hdfs = props.getHdfs();
        try {
            // NN1 状态
            String nnUrl = "http://" + hdfs.getNn1Web() + "/jmx";
            String nnStatus = rt.getForObject(nnUrl + "?qry=Hadoop:service=NameNode,name=NameNodeStatus", String.class);
            String nnFS = rt.getForObject(nnUrl + "?qry=Hadoop:service=NameNode,name=FSNamesystem", String.class);
            parseJmx(nnStatus, result, "nnStatus");
            parseJmx(nnFS, result, "nnFS");
            result.put("nnStatus.HostAndPort", hdfs.getNn1Rpc());

            // NN2 状态（HA 备用节点）
            try {
                String nn2Url = "http://" + hdfs.getNn2Web() + "/jmx";
                String nn2Status = rt.getForObject(nn2Url + "?qry=Hadoop:service=NameNode,name=NameNodeStatus", String.class);
                Map<String, Object> nn2Data = new HashMap<>();
                parseJmx(nn2Status, nn2Data, "nn2Status");
                result.put("nn2State", nn2Data.get("nn2Status.State"));
                result.put("nn2HostPort", hdfs.getNn2Rpc());
            } catch (Exception e) {
                result.put("nn2State", "unknown");
                result.put("nn2Error", e.getMessage());
            }

            // DataNodes 信息
            String nnInfo = rt.getForObject(nnUrl + "?qry=Hadoop:service=NameNode,name=NameNodeInfo", String.class);
            Map<String, Object> infoData = parseJson(parseBean(nnInfo, "NameNodeInfo"));
            if (infoData != null && infoData.get("LiveNodes") instanceof String) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<?,?> liveNodes = mapper.readValue((String)infoData.get("LiveNodes"), Map.class);
                List<Map<String, Object>> dnList = new ArrayList<>();
                List<Map<String, Object>> volumesList = new ArrayList<>();
                if (liveNodes != null) {
                    for (Map.Entry<?,?> entry : liveNodes.entrySet()) {
                        String dnId = (String)entry.getKey();
                        Map<?,?> dnData = (Map<?,?>)((Map)entry.getValue());
                        Map<String, Object> dn = new HashMap<>();
                        dn.put("id", dnId);
                        dn.put("infoAddr", dnData.get("infoAddr"));
                        dn.put("lastContact", dnData.get("lastContact"));
                        dn.put("usedSpace", dnData.get("usedSpace"));
                        dn.put("nonDfsUsedSpace", dnData.get("nonDfsUsedSpace"));
                        dn.put("capacity", dnData.get("capacity"));
                        dn.put("numBlocks", dnData.get("numBlocks"));
                        dn.put("adminState", dnData.get("adminState"));
                        dn.put("version", dnData.get("version"));
                        dnList.add(dn);

                        // 查询每个 DN 的磁盘卷容量
                        String dnAddr = (String)dnData.get("infoAddr");
                        if (dnAddr != null) {
                            try {
                                String volJmx = rt.getForObject("http://" + dnAddr + "/jmx?qry=Hadoop:service=DataNode,name=FSDatasetState", String.class);
                                Map<String, Object> volData = parseJson(parseBean(volJmx, "FSDatasetState"));
                                if (volData != null) {
                                    volData.put("dnId", dnId);
                                    volumesList.add(volData);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                result.put("dataNodes", dnList);
                result.put("dataNodeVolumes", volumesList);
            }
        } catch (Exception e) {
            result.put("nnError", e.getMessage());
        }

        // JournalNode 信息
        List<Map<String, Object>> jnList = new ArrayList<>();
        for (String ip : props.getJournalNode().getHosts()) {
            try {
                Map<String, Object> jn = new HashMap<>();
                int port = props.getJournalNode().getWebPort();
                String info = rt.getForObject("http://" + ip + ":" + port + "/jmx?qry=Hadoop:service=JournalNode,name=JournalNodeInfo", String.class);
                String sync = rt.getForObject("http://" + ip + ":" + port + "/jmx?qry=Hadoop:service=JournalNode,name=Journal-mycluster", String.class);
                String infoBean = parseBean(info, "JournalNodeInfo");
                if (infoBean != null) {
                    jn = parseJson(infoBean);
                    jn.put("ip", ip);
                }
                String syncBean = parseBean(sync, "Journal-mycluster");
                if (syncBean != null) jn.putAll(parseJson(syncBean));
                if (!jn.isEmpty()) jnList.add(jn);
            } catch (Exception ignored) {}
        }
        result.put("journalNodes", jnList);
        // NameNode 节点列表（HA 配置）
        result.put("nnNodes", List.of(props.getHdfs().getHaNamenodes().split(",")));
        return success(result);
    }

    @GetMapping("/blocks")
    public Map<String, Object> getFileBlocks(@RequestParam(defaultValue = "/") String path) {
        RestTemplate rt = new RestTemplate();
        try {
            String baseUrl = "http://" + props.getHdfs().getNn1Web() + "/webhdfs/v1";
            String json = rt.getForObject(baseUrl + path + "?op=GETFILESTATUS", String.class);
            Map<String, Object> result = new HashMap<>();
            result.put("fileStatus", parseJson(json));
            result.put("path", path);
            try {
                String locJson = rt.getForObject(baseUrl + path + "?op=GET_FILE_BLOCK_LOCATIONS", String.class);
                result.put("blockLocations", parseJson(locJson));
            } catch (Exception e) {
                result.put("blockLocations", null);
            }
            return success(result);
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    @PostMapping("/switch-nn")
    public Map<String, Object> switchNameNode(@RequestParam(defaultValue = "auto") String from,
                                                @RequestParam(defaultValue = "auto") String to) {
        List<String> logs = new ArrayList<>();
        try {
            // 自动探测当前 active 的 NN
            String nn1Before = runCmd("docker exec nn1 hdfs haadmin -getServiceState nn1 2>/dev/null", logs).trim();
            String nn2Before = runCmd("docker exec nn2 hdfs haadmin -getServiceState nn2 2>/dev/null", logs).trim();
            String active = nn1Before.contains("active") ? "nn1" : nn2Before.contains("active") ? "nn2" : null;
            String standby = nn1Before.contains("standby") ? "nn1" : nn2Before.contains("standby") ? "nn2" : null;

            if (active == null || standby == null) {
                return error(500, "无法确定 NN 状态: nn1=" + nn1Before + " nn2=" + nn2Before);
            }
            if ("auto".equals(from)) from = active;
            if ("auto".equals(to)) to = standby;

            logs.add("探测: active=" + active + ", standby=" + standby + " → 切换 " + from + "→" + to);

            // 把当前 active 降为 standby
            if (active.equals(from)) {
                runCmd("echo Y | docker exec -i " + active + " hdfs haadmin -transitionToStandby " + active + " --forcemanual 2>&1 | tail -3", logs);
                Thread.sleep(1500);
            }

            // 把目标提升为 active
            runCmd("echo Y | docker exec -i " + to + " hdfs haadmin -transitionToActive " + to + " --forcemanual 2>&1 | tail -3", logs);
            Thread.sleep(2000);

            String sFrom = runCmd("docker exec " + from + " hdfs haadmin -getServiceState " + from + " 2>/dev/null", null);
            String sTo = runCmd("docker exec " + to + " hdfs haadmin -getServiceState " + to + " 2>/dev/null", null);
            logs.add("结果: " + from + "=" + sFrom.trim() + ", " + to + "=" + sTo.trim());
            return success(Map.of(
                "beforeState", Map.of("nn1", nn1Before, "nn2", nn2Before),
                "from", from, "to", to,
                "fromState", sFrom.trim(), "toState", sTo.trim(),
                "logs", logs
            ));
        } catch (Exception e) {
            return error(500, "切换失败: " + e.getMessage());
        }
    }

    @PostMapping("/manage-nn")
    public Map<String, Object> manageNameNode(@RequestParam String node,    // nn1 or nn2
                                               @RequestParam String action) { // stop / start / restart
        List<String> logs = new ArrayList<>();
        try {
            if (!List.of("nn1", "nn2").contains(node)) {
                return error(400, "无效节点: " + node + "（可用: nn1, nn2）");
            }
            if (!List.of("stop", "start", "restart").contains(action)) {
                return error(400, "无效操作: " + action + "（可用: stop, start, restart）");
            }

            switch (action) {
                case "stop":
                    logs.add("执行: docker stop " + node);
                    runCmd("docker stop " + node + " 2>&1", logs);
                    logs.add("✅ " + node + " 已停止");
                    break;
                case "start":
                    logs.add("执行: docker start " + node);
                    runCmd("docker start " + node + " 2>&1", logs);
                    Thread.sleep(3000);
                    // 启动后尝试恢复为 standby
                    String startState = runCmd("docker exec " + node + " hdfs haadmin -getServiceState " + node + " 2>/dev/null || echo 'not_ready'", null);
                    if ("standby".equals(startState.trim())) {
                        logs.add("✅ " + node + " 已启动，当前状态: standby");
                    } else {
                        logs.add("✅ " + node + " 已启动，尝试设置为 standby...");
                        runCmd("echo Y | docker exec -i " + node + " hdfs haadmin -transitionToStandby " + node + " --forcemanual 2>&1 | tail -3", logs);
                    }
                    break;
                case "restart":
                    logs.add("执行: docker restart " + node);
                    runCmd("docker restart " + node + " 2>&1", logs);
                    Thread.sleep(3000);
                    String state = runCmd("docker exec " + node + " hdfs haadmin -getServiceState " + node + " 2>/dev/null || echo 'not_ready'", null);
                    if (!"not_ready".equals(state.trim())) {
                        runCmd("echo Y | docker exec -i " + node + " hdfs haadmin -transitionToStandby " + node + " --forcemanual 2>&1 | tail -3", logs);
                        logs.add("✅ " + node + " 已重启，状态: " + state.trim());
                    } else {
                        logs.add("✅ " + node + " 已重启");
                    }
                    break;
            }

            // 验证最终状态
            Thread.sleep(2000);
            String finalState = runCmd("docker exec " + node + " hdfs haadmin -getServiceState " + node + " 2>/dev/null || echo 'unknown'", null);
            return success(Map.of("node", node, "action", action, "state", finalState.trim(), "logs", logs));
        } catch (Exception e) {
            return error(500, "操作失败: " + e.getMessage());
        }
    }

    // === DataNode Decommission / Maintenance Mode API ===

    /**
     * POST /api/v1/hdfs/datanodes/decommission — Put a DataNode into/out of maintenance/decommission mode
     */
    @PostMapping("/datanodes/decommission")
    public Map<String, Object> decommissionDataNode(@RequestParam(defaultValue = "cluster1") String clusterId,
                                                     @RequestParam String hostname,
                                                     @RequestParam(defaultValue = "true") boolean decom) {
        try {
            String result = hdfsService.decommissionDataNode(clusterId, hostname, decom);
            return success(Map.of("hostname", hostname, "decom", decom, "result", result));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    /**
     * POST /api/v1/hdfs/rebalance — Trigger HDFS balancer rebalance
     */
    @PostMapping("/rebalance")
    public Map<String, Object> triggerRebalance(@RequestParam(defaultValue = "cluster1") String clusterId) {
        try {
            String result = hdfsService.triggerRebalance(clusterId);
            return success(Map.of("result", result));
        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    private String runCmd(String cmd, List<String> logs) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"bash", "-c", cmd});
        String out = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        if (logs != null) logs.add(out);
        return out;
    }

    @PostMapping("/scale-datanode")
    public Map<String, Object> scaleDataNode(@RequestParam String hostname,
                                              @RequestParam(defaultValue = "352") int capacityGB) {
        try {
            String netCheckCmd = "docker inspect nn1 --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null";
            String netName = new String(Runtime.getRuntime().exec(new String[]{"bash", "-c", netCheckCmd}).getInputStream().readAllBytes()).trim();
            if (netName.isEmpty()) netName = "hadoop-ha_hadoop-net";

            List<String> commands = Arrays.asList(
                "# 1. 创建数据目录",
                "mkdir -p /data/software/hadoop-ha/data/" + hostname,
                "",
                "# 2. 启动新的 DataNode + NodeManager 容器",
                "docker run -d \\",
                "  --name " + hostname + " \\",
                "  --hostname " + hostname + " \\",
                "  --network " + netName + " \\",
                "  -v /data/software/hadoop-ha/config:/opt/hadoop/etc/hadoop:ro \\",
                "  -v /data/software/hadoop-ha/data/" + hostname + ":/hadoop/dfs/data \\",
                "  apache/hadoop:3.4.3 \\",
                "  bash -c \"hdfs datanode & yarn nodemanager & wait -n\"",
                "",
                "# 3. 验证",
                "docker ps | grep " + hostname
            );

            return success(Map.of(
                "hostname", hostname, "capacityGB", capacityGB,
                "network", netName, "commands", commands,
                "note", "执行上述命令即可部署新的 DataNode。执行后约 10 秒，刷新 HDFS 页面即可看到新节点。"
            ));
        } catch (Exception e) {
            return error(500, "Failed: " + e.getMessage());
        }
    }

    @GetMapping("/checkpoint")
    public Map<String, Object> getCheckpointInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            // fsimage: NN /hadoop/dfs/name/current/ 下最新
            String fsimageCmd = "docker exec nn1 ls -lh --sort=time /hadoop/dfs/name/current/ 2>/dev/null | grep fsimage";
            String fsimageOut = runCmd(fsimageCmd, null);
            String[] fsimageLines = fsimageOut.split("\n");
            Map<String, Object> latestFsimage = null;
            for (String line : fsimageLines) {
                line = line.trim();
                if (line.isEmpty() || line.contains(".md5")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length >= 8) {
                    latestFsimage = new LinkedHashMap<>();
                    latestFsimage.put("name", parts[parts.length-1]);
                    latestFsimage.put("size", parts[4]);
                    latestFsimage.put("date", parts[5] + " " + parts[6]);
                    latestFsimage.put("yearOrTime", parts[7].contains(":") ? parts[7] : "");
                    break; // 第一条非 .md5 的 fsimage 就是最新的
                }
            }
            String fsimageDir = "/hadoop/dfs/name/current/";
            result.put("fsimage", latestFsimage != null ? latestFsimage : Map.of("name", "无", "note", "未找到 fsimage 文件"));
            result.put("fsimageDir", fsimageDir);

            // edits: JN /hadoop/dfs/journalnode/mycluster/current/ 下最新 completed + inprogress
            String editsCmd = "docker exec jn1 ls -lh --sort=time /hadoop/dfs/journalnode/mycluster/current/ 2>/dev/null | grep -E 'edits' | head -5";
            String editsOut = runCmd(editsCmd, null);
            String[] editsLines = editsOut.split("\n");
            Map<String, Object> latestEdits = null;
            Map<String, Object> inProgressEdits = null;
            long maxTxId = -1;
            for (String line : editsLines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length >= 8) {
                    String fname = parts[parts.length-1];
                    if (fname.contains("inprogress")) {
                        inProgressEdits = new LinkedHashMap<>();
                        inProgressEdits.put("name", fname);
                        inProgressEdits.put("size", parts[4]);
                        inProgressEdits.put("date", parts[5] + " " + parts[6]);
                        inProgressEdits.put("yearOrTime", parts[7].contains(":") ? parts[7] : "");
                    } else if (!fname.contains(".md5")) {
                        // 提取最大 txid 作为最新已完成的 edits
                        String txIdStr = fname.replaceAll(".*edits_", "").split("-")[1];
                        try {
                            long txId = Long.parseLong(txIdStr);
                            if (txId > maxTxId) {
                                maxTxId = txId;
                                latestEdits = new LinkedHashMap<>();
                                latestEdits.put("name", fname);
                                latestEdits.put("size", parts[4]);
                                latestEdits.put("date", parts[5] + " " + parts[6]);
                                latestEdits.put("yearOrTime", parts[7].contains(":") ? parts[7] : "");
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            String editsDir = "/hadoop/dfs/journalnode/mycluster/current/";
            result.put("editlog", latestEdits != null ? latestEdits : Map.of("name", "无", "note", "未找到已完成的 edtis 文件"));
            result.put("editlogInProgress", inProgressEdits != null ? inProgressEdits : Map.of("name", "无", "note", "无进行中的 edtis 文件"));
            result.put("editlogDir", editsDir);

            return success(result);
        } catch (Exception e) {
            return error(500, "获取 checkpoint 信息失败: " + e.getMessage());
        }
    }

    @PostMapping("/trigger-checkpoint")
    public Map<String, Object> triggerCheckpoint() {
        List<String> logs = new ArrayList<>();
        try {
            // 1. 自动探测当前 active 的 NN
            String nn1State = runCmd("docker exec nn1 hdfs haadmin -getServiceState nn1 2>/dev/null", logs).trim();
            String nn2State = runCmd("docker exec nn2 hdfs haadmin -getServiceState nn2 2>/dev/null", logs).trim();
            String active = nn1State.contains("active") ? "nn1" : nn2State.contains("active") ? "nn2" : null;
            String standby = nn1State.contains("standby") ? "nn1" : nn2State.contains("standby") ? "nn2" : null;

            if (active == null) {
                return error(500, "无法确定 active NameNode: nn1=" + nn1State + " nn2=" + nn2State);
            }
            logs.add("探测: active=" + active + ", standby=" + (standby != null ? standby : "无"));

            // 2. 触发 checkpoint — saveNamespace
            logs.add("--- 开始触发 checkpoint 在 " + active + " ---");
            String saveOut = runCmd("docker exec " + active + " hdfs dfsadmin -saveNamespace 2>&1", logs);
            logs.add("saveNamespace 输出: " + saveOut);

            // 3. 等待 5 秒确保 checkpoint 完成刷盘
            logs.add("等待 5 秒让 checkpoint 完成...");
            Thread.sleep(5000);

            // 4. 验证：列出两个 NN 最新的 fsimage 文件（取前 5 条非 .md5 的）
            Map<String, List<Map<String, Object>>> fsimageFiles = new LinkedHashMap<>();
            for (String node : new String[]{"nn1", "nn2"}) {
                String fsimageCmd = "docker exec " + node + " ls -lh --sort=time /hadoop/dfs/name/current/ 2>/dev/null | grep fsimage";
                String fsimageOut = runCmd(fsimageCmd, null);
                String[] lines = fsimageOut.split("\n");
                List<Map<String, Object>> images = new ArrayList<>();
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.contains(".md5")) continue;
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 8) {
                        Map<String, Object> img = new LinkedHashMap<>();
                        img.put("name", parts[parts.length - 1]);
                        img.put("size", parts[4]);
                        img.put("date", parts[5] + " " + parts[6]);
                        img.put("yearOrTime", parts[7].contains(":") ? parts[7] : "");
                        images.add(img);
                    }
                }
                fsimageFiles.put(node, images.isEmpty() ? List.of(Map.of("note", "未找到 fsimage")) : images);
            }

            // 提取 nn1 最新 fsimage 的 txid 做对比
            String txidBefore = "";
            List<Map<String, Object>> nn1Images = fsimageFiles.get("nn1");
            if (nn1Images != null && !nn1Images.isEmpty()) {
                Map<String, Object> latest = nn1Images.get(0);
                String name = (String) latest.get("name");
                if (name != null) {
                    txidBefore = name.replace("fsimage_", "").replace(".ckpt", "");
                }
            }

            logs.add("验证完成: " + active + " 上的 saveNamespace 已执行");
            logs.add("最新 fsimage txid: " + (txidBefore.isEmpty() ? "未知" : txidBefore));

            return success(Map.of(
                "activeNode", active,
                "standbyNode", standby != null ? standby : "无",
                "states", Map.of("nn1", nn1State.trim(), "nn2", nn2State.trim()),
                "saveNamespaceOutput", saveOut,
                "fsimageFiles", fsimageFiles,
                "note", "HDFS checkpoint 已触发，新的 fsimage 已保存到磁盘",
                "logs", logs
            ));
        } catch (Exception e) {
            return error(500, "触发 checkpoint 失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs")
    public Map<String, Object> getLogs(@RequestParam String role,   // namenode / datanode / journalnode
                                       @RequestParam String node,    // 容器名，如 nn1, dn2, jn3
                                       @RequestParam(defaultValue = "200") int lines,  // 返回行数
                                       @RequestParam(required = false) String level,   // 日志级别: DEBUG/INFO/WARN/ERROR
                                       @RequestParam(required = false) String pattern, // 正则匹配
                                       @RequestParam(required = false) String since,   // 起始时间 ISO（如 2026-06-15T10:00:00）
                                       @RequestParam(required = false) String until) { // 截止时间 ISO
        try {
            // 验证角色
            String validRole = validateRole(role, node);
            if (validRole != null) return error(400, validRole);

            // 构建 docker logs 命令
            int tail = Math.min(Math.max(lines, 50), 3000);
            StringBuilder cmd = new StringBuilder("docker logs " + node + " --timestamps --tail " + tail + " 2>&1");

            // 时间范围过滤（用 docker 原生的 --since/--until）
            if (since != null && !since.isEmpty())
                cmd.append(" --since \"").append(since).append("\"");
            if (until != null && !until.isEmpty())
                cmd.append(" --until \"").append(until).append("\"");

            // 级别 + 正则过滤（用 grep）
            boolean pipe = false;
            if (level != null && !level.isEmpty()) {
                // 支持多级过滤：逗号分隔，如 "ERROR,WARN"
                String[] levels = level.toUpperCase().split(",");
                StringBuilder grepPattern = new StringBuilder();
                for (int li = 0; li < levels.length; li++) {
                    String lv = levels[li].trim();
                    if (lv.isEmpty()) continue;
                    if (!List.of("DEBUG","INFO","WARN","ERROR").contains(lv))
                        return error(400, "无效的日志级别: " + lv + "（可用: DEBUG, INFO, WARN, ERROR）");
                    if (grepPattern.length() > 0) grepPattern.append("\\|");
                    grepPattern.append(" ").append(lv).append(" ");
                }
                if (grepPattern.length() > 0) {
                    cmd.append(" | grep -i \"").append(grepPattern).append("\"");
                    pipe = true;
                }
            }
            if (pattern != null && !pattern.isEmpty()) {
                cmd.append(pipe ? " | grep -E \"" : " | grep -E \"").append(pattern).append("\"");
                pipe = true;
            }

            // 执行命令
            String output = runCmd(cmd.toString(), null);

            // 解析行
            String[] linesArr = output.split("\n");
            int from = Math.max(0, linesArr.length - tail);
            List<Map<String, Object>> logLines = new ArrayList<>();
            for (int i = from; i < linesArr.length; i++) {
                String line = linesArr[i].trim();
                if (line.isEmpty()) continue;
                Map<String, Object> entry = new HashMap<>();
                // 提取 Docker 时间戳
                if (line.length() > 30 && line.charAt(10) == 'T') {
                    entry.put("time", line.substring(0, 30).replace('T', ' ').replace("Z", ""));
                    entry.put("msg", line.substring(30).trim());
                } else {
                    entry.put("time", "");
                    entry.put("msg", line);
                }
                // 提取日志级别
                String msg = (String)entry.get("msg");
                if (msg.contains(" DEBUG ")) entry.put("level", "DEBUG");
                else if (msg.contains(" INFO ")) entry.put("level", "INFO");
                else if (msg.contains(" WARN ")) entry.put("level", "WARN");
                else if (msg.contains(" ERROR ") || msg.contains(" FATAL ")) entry.put("level", "ERROR");
                else entry.put("level", "OTHER");
                logLines.add(entry);
            }

            return success(Map.of(
                "role", role, "node", node, "total", logLines.size(), "lines", logLines
            ));
        } catch (Exception e) {
            return error(500, "日志查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/log-files")
    public Map<String, Object> listLogFiles() {
        String baseDir = "/data/software/hadoop-ha/logs";
        Map<String, Object> result = new LinkedHashMap<>();
        java.io.File base = new java.io.File(baseDir);
        if (!base.isDirectory()) return success(result);
        for (java.io.File roleDir : base.listFiles(java.io.File::isDirectory)) {
            String role = roleDir.getName();
            List<Map<String, Object>> nodesList = new ArrayList<>();
            for (java.io.File nodeDir : roleDir.listFiles(java.io.File::isDirectory)) {
                Map<String, Object> nodeInfo = new HashMap<>();
                nodeInfo.put("node", nodeDir.getName());
                List<Map<String, Object>> files = new ArrayList<>();
                long totalBytes = 0;
                for (java.io.File f : nodeDir.listFiles()) {
                    Map<String, Object> fi = new HashMap<>();
                    fi.put("name", f.getName());
                    fi.put("size", f.length());
                    fi.put("isLink", f.isFile() && f.getAbsolutePath().endsWith(".log"));
                    if (f.getName().equals("container.log")) {
                        fi.put("type", "docker-json");
                    } else {
                        fi.put("type", f.getName().endsWith(".log") ? "text" : "other");
                    }
                    files.add(fi);
                    totalBytes += f.length();
                }
                nodeInfo.put("files", files);
                nodeInfo.put("totalSize", totalBytes);
                nodesList.add(nodeInfo);
            }
            result.put(role, nodesList);
        }
        return success(result);
    }

    @GetMapping("/log-files/content")
    public Map<String, Object> readLogFile(@RequestParam String role,
                                            @RequestParam String node,
                                            @RequestParam(defaultValue = "container.log") String file,
                                            @RequestParam(defaultValue = "200") int lines,
                                            @RequestParam(required = false) String level,
                                            @RequestParam(required = false) String pattern) {
        try {
            java.io.File logFile = new java.io.File(
                "/data/software/hadoop-ha/logs/" + role + "/" + node + "/" + file);
            if (!logFile.isFile()) return error(404, "日志文件不存在: " + logFile.getPath());

            // 读取全部行
            List<String> rawLines = java.nio.file.Files.readAllLines(logFile.toPath());
            int totalLines = rawLines.size();
            int tail = Math.min(lines, totalLines);
            List<Map<String, Object>> resultLines = new ArrayList<>();

            for (int i = totalLines - tail; i < totalLines; i++) {
                String line = rawLines.get(i).trim();
                if (line.isEmpty()) continue;
                Map<String, Object> entry = new HashMap<>();

                // 解析 Docker JSON 日志格式
                String logMsg = line;
                String logTime = "";
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<?,?> jsonLine = mapper.readValue(line, Map.class);
                    if (jsonLine.containsKey("log")) {
                        logMsg = ((String)jsonLine.get("log")).replace("\n", "").replace("\r", "");
                    }
                    if (jsonLine.containsKey("time")) {
                        logTime = ((String)jsonLine.get("time")).replace('T', ' ').substring(0, 23);
                    }
                } catch (Exception ignored) {
                    logMsg = line.length() > 120 ? line.substring(0, 120) + "..." : line;
                }

                // 级别提取
                String lv = "OTHER";
                if (logMsg.contains(" DEBUG ")) lv = "DEBUG";
                else if (logMsg.contains(" INFO ")) lv = "INFO";
                else if (logMsg.contains(" WARN ")) lv = "WARN";
                else if (logMsg.contains(" ERROR ") || logMsg.contains(" FATAL ")) lv = "ERROR";

                // 级别过滤（支持多选：逗号分隔）
                if (level != null && !level.isEmpty()) {
                    String selLevels = level.toUpperCase();
                    // Check if the log line's level is among the comma-separated selected levels
                    boolean matchesSelected = false;
                    for (String sel : selLevels.split(",")) {
                        sel = sel.trim();
                        if (!sel.isEmpty() && sel.equals(lv)) {
                            matchesSelected = true;
                            break;
                        }
                    }
                    if (!matchesSelected) continue;
                }
                // 正则过滤
                if (pattern != null && !pattern.isEmpty() && !logMsg.matches(".*" + pattern + ".*")) continue;

                entry.put("time", logTime);
                entry.put("level", lv);
                entry.put("msg", logMsg);
                resultLines.add(entry);
            }

            return success(Map.of(
                "role", role, "node", node, "file", file,
                "total", resultLines.size(), "fileSize", logFile.length(),
                "lines", resultLines
            ));
        } catch (Exception e) {
            return error(500, "读取日志文件失败: " + e.getMessage());
        }
    }

    private String validateRole(String role, String node) {
        Map<String, List<String>> roleMap = Map.of(
            "namenode", List.of("nn1", "nn2"),
            "datanode", List.of("dn1", "dn2", "dn3"),
            "journalnode", List.of("jn1", "jn2", "jn3")
        );
        List<String> valid = roleMap.get(role);
        if (valid == null) return "无效的角色: " + role + "（可用: namenode, datanode, journalnode）";
        if (!valid.contains(node)) return "节点 " + node + " 不属于角色 " + role + "（可选: " + String.join(", ", valid) + "）";
        return null;
    }

    private void parseJmx(String json, Map<String, Object> target, String prefix) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<?,?> root = mapper.readValue(json, Map.class);
            Object beans = root.get("beans");
            if (beans instanceof List && !((List<?>)beans).isEmpty()) {
                Map<?,?> bean = (Map<?,?>)((List<?>)beans).get(0);
                for (Map.Entry<?,?> e : bean.entrySet()) {
                    String key = (String)e.getKey();
                    if (!"name".equals(key) && !"modelerType".equals(key)) {
                        target.put(prefix + "." + key, e.getValue());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private String parseBean(String json, String beanName) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<?,?> root = mapper.readValue(json, Map.class);
            Object beans = root.get("beans");
            if (beans instanceof List) {
                for (Object b : (List<?>)beans) {
                    Object n = b instanceof Map ? ((Map<?,?>)b).get("name") : null;
                    if (n instanceof String && ((String)n).contains(beanName)) {
                        return mapper.writeValueAsString(b);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Map<String, Object> parseJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) { return new HashMap<>(); }
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", 0);
        m.put("msg", "success");
        m.put("data", data);
        return m;
    }

    private Map<String, Object> error(int code, String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("msg", msg);
        m.put("data", null);
        return m;
    }
}
