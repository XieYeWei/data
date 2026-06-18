package com.hermes.service.catalog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hermes.entity.CatalogColumn;
import com.hermes.entity.CatalogTable;
import com.hermes.entity.CatalogTag;
import com.hermes.entity.OperationLog;
import com.hermes.mapper.CatalogColumnMapper;
import com.hermes.mapper.CatalogTableMapper;
import com.hermes.mapper.CatalogTagMapper;
import com.hermes.mapper.OperationLogMapper;
import com.hermes.service.hdfs.HdfsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CatalogService {

    @Autowired
    private CatalogTableMapper catalogTableMapper;

    @Autowired
    private CatalogColumnMapper catalogColumnMapper;

    @Autowired
    private CatalogTagMapper catalogTagMapper;

    @Autowired(required = false)
    private OperationLogMapper operationLogMapper;

    @Autowired(required = false)
    private HdfsService hdfsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== Table CRUD ====================

    public CatalogTable createTable(CatalogTable table) {
        if (table.getCreateTime() == null) {
            table.setCreateTime(LocalDateTime.now());
        }
        if (table.getUpdateTime() == null) {
            table.setUpdateTime(LocalDateTime.now());
        }
        catalogTableMapper.insert(table);
        log.info("Created catalog table: id={}, name={}", table.getId(), table.getName());
        return table;
    }

    public CatalogTable updateTable(Long id, CatalogTable table) {
        CatalogTable existing = catalogTableMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Catalog table not found: " + id);
        }
        table.setId(id);
        table.setUpdateTime(LocalDateTime.now());
        catalogTableMapper.updateById(table);
        log.info("Updated catalog table: id={}", id);
        return catalogTableMapper.selectById(id);
    }

    @Transactional
    public void deleteTable(Long id) {
        CatalogTable existing = catalogTableMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Catalog table not found: " + id);
        }
        // Delete associated columns
        catalogColumnMapper.delete(new QueryWrapper<CatalogColumn>().eq("table_id", id));
        // Delete table-tag associations
        jdbcTemplate.update("DELETE FROM catalog_table_tag WHERE table_id = ?", id);
        catalogTableMapper.deleteById(id);
        log.info("Deleted catalog table: id={}", id);
    }

    public CatalogTable getTable(Long id) {
        return catalogTableMapper.selectById(id);
    }

    public IPage<CatalogTable> listTables(int page, int size, String name, String schema, String format, Long tagId) {
        QueryWrapper<CatalogTable> qw = new QueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            qw.like("name", name);
        }
        if (schema != null && !schema.isEmpty()) {
            qw.eq("schema_name", schema);
        }
        if (format != null && !format.isEmpty()) {
            qw.eq("format", format);
        }
        // If tagId is specified, first find table_ids that have this tag
        if (tagId != null) {
            List<Long> tableIds = jdbcTemplate.queryForList(
                "SELECT table_id FROM catalog_table_tag WHERE tag_id = ?", Long.class, tagId
            );
            if (tableIds.isEmpty()) {
                // No tables match this tag, return empty page
                return new Page<>(page, size, 0);
            }
            qw.in("id", tableIds);
        }
        qw.orderByDesc("update_time");
        return catalogTableMapper.selectPage(new Page<>(page, size), qw);
    }

    // ==================== Column CRUD ====================

    public CatalogColumn addColumn(CatalogColumn column) {
        if (column.getCreateTime() == null) {
            column.setCreateTime(LocalDateTime.now());
        }
        catalogColumnMapper.insert(column);
        return column;
    }

    public CatalogColumn updateColumn(Long id, CatalogColumn column) {
        CatalogColumn existing = catalogColumnMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Catalog column not found: " + id);
        }
        column.setId(id);
        catalogColumnMapper.updateById(column);
        return catalogColumnMapper.selectById(id);
    }

    public void deleteColumn(Long id) {
        CatalogColumn existing = catalogColumnMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Catalog column not found: " + id);
        }
        catalogColumnMapper.deleteById(id);
    }

    public List<CatalogColumn> getColumnsByTableId(Long tableId) {
        return catalogColumnMapper.selectList(
            new QueryWrapper<CatalogColumn>().eq("table_id", tableId).orderByAsc("ordinal_position")
        );
    }

    // ==================== Tag CRUD ====================

    public CatalogTag createTag(CatalogTag tag) {
        if (tag.getCreateTime() == null) {
            tag.setCreateTime(LocalDateTime.now());
        }
        catalogTagMapper.insert(tag);
        return tag;
    }

    @Transactional
    public void deleteTag(Long id) {
        CatalogTag existing = catalogTagMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Catalog tag not found: " + id);
        }
        // Remove all table-tag associations for this tag
        jdbcTemplate.update("DELETE FROM catalog_table_tag WHERE tag_id = ?", id);
        catalogTagMapper.deleteById(id);
    }

    public List<CatalogTag> listTags() {
        return catalogTagMapper.selectList(new QueryWrapper<CatalogTag>().orderByAsc("name"));
    }

    // ==================== Table-Tag Operations ====================

    @Transactional
    public void setTableTags(Long tableId, List<Long> tagIds) {
        // Verify table exists
        CatalogTable table = catalogTableMapper.selectById(tableId);
        if (table == null) {
            throw new RuntimeException("Catalog table not found: " + tableId);
        }
        // Remove existing associations
        jdbcTemplate.update("DELETE FROM catalog_table_tag WHERE table_id = ?", tableId);
        // Insert new associations
        if (tagIds != null && !tagIds.isEmpty()) {
            for (Long tagId : tagIds) {
                jdbcTemplate.update("INSERT INTO catalog_table_tag (table_id, tag_id) VALUES (?, ?)", tableId, tagId);
            }
        }
    }

    public List<Long> getTableTagIds(Long tableId) {
        return jdbcTemplate.queryForList(
            "SELECT tag_id FROM catalog_table_tag WHERE table_id = ?", Long.class, tableId
        );
    }

    public List<CatalogTag> getTableTags(Long tableId) {
        List<Long> tagIds = getTableTagIds(tableId);
        if (tagIds.isEmpty()) {
            return new ArrayList<>();
        }
        return catalogTagMapper.selectList(
            new QueryWrapper<CatalogTag>().in("id", tagIds)
        );
    }

    // ==================== HDFS Scan ====================

    public CatalogTable scanTable(Long tableId) {
        CatalogTable table = catalogTableMapper.selectById(tableId);
        if (table == null) {
            throw new RuntimeException("Catalog table not found: " + tableId);
        }
        if (hdfsService == null) {
            throw new RuntimeException("HdfsService not available, cannot scan HDFS path");
        }

        try {
            String clusterId = table.getClusterId();
            String hdfsPath = table.getHdfsPath();

            // Use HdfsService to read the directory contents and get file info
            // getContentSummary gives us file count and total size
            var summary = hdfsService.getContentSummary(clusterId, hdfsPath, 1L);
            if (summary != null) {
                Object fileCount = summary.get("fileCount");
                Object length = summary.get("length");
                if (fileCount instanceof Number) {
                    table.setFileCount(((Number) fileCount).intValue());
                }
                if (length instanceof Number) {
                    table.setTotalSizeBytes(((Number) length).longValue());
                }
            }

            // Estimate row count by reading first 10 lines of a sample file
            long estimatedRows = estimateRowCount(clusterId, hdfsPath);
            table.setRowCount(estimatedRows);

            table.setUpdateTime(LocalDateTime.now());
            catalogTableMapper.updateById(table);
            log.info("Scanned catalog table id={}, path={}, files={}, size={}, rows={}",
                tableId, hdfsPath, table.getFileCount(), table.getTotalSizeBytes(), estimatedRows);

        } catch (Exception e) {
            log.warn("Failed to scan HDFS path for table id={}: {}", tableId, e.getMessage());
            throw new RuntimeException("HDFS scan failed: " + e.getMessage(), e);
        }

        return catalogTableMapper.selectById(tableId);
    }

    private long estimateRowCount(String clusterId, String hdfsPath) {
        long totalRows = 0;
        int filesChecked = 0;
        try {
            FileSystem fs = getFileSystem(clusterId);
            if (fs == null) return 0;

            Path path = new Path(hdfsPath);
            FileStatus[] statuses = fs.listStatus(path);

            for (FileStatus status : statuses) {
                if (status.isFile() && status.getLen() > 0) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(fs.open(status.getPath())))) {
                        int lines = 0;
                        String line;
                        while ((line = reader.readLine()) != null && lines < 10) {
                            lines++;
                        }
                        // Estimate: if file has N lines in first 10, extrapolate
                        if (lines > 0) {
                            // Use average of lines seen to estimate total
                            long fileRows = (status.getLen() / Math.max(1, (status.getLen() / Math.max(1, lines)))) * lines;
                            totalRows += fileRows;
                            filesChecked++;
                        }
                        if (filesChecked >= 3) break; // Check at most 3 files
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error estimating row count for {}: {}", hdfsPath, e.getMessage());
        }
        return totalRows;
    }

    private FileSystem getFileSystem(String clusterId) {
        try {
            var field = hdfsService.getClass().getDeclaredField("hadoopConfig");
            field.setAccessible(true);
            var hadoopConfig = field.get(hdfsService);
            var method = hadoopConfig.getClass().getMethod("getFileSystem", String.class);
            return (FileSystem) method.invoke(hadoopConfig, clusterId);
        } catch (Exception e) {
            log.warn("Cannot get FileSystem: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Auto Discover ====================

    public List<CatalogTable> autoDiscover(String clusterId, String basePath) {
        List<CatalogTable> created = new ArrayList<>();
        if (hdfsService == null) {
            throw new RuntimeException("HdfsService not available, cannot auto-discover");
        }

        try {
            FileSystem fs = getFileSystem(clusterId);
            if (fs == null) {
                throw new RuntimeException("Cannot get FileSystem for cluster: " + clusterId);
            }

            Path base = new Path(basePath);
            FileStatus[] dirs = fs.listStatus(base);
            for (FileStatus dir : dirs) {
                if (dir.isDirectory()) {
                    String dirName = dir.getPath().getName();
                    String dirPath = dir.getPath().toString();

                    // Check if table already exists
                    QueryWrapper<CatalogTable> qw = new QueryWrapper<>();
                    qw.eq("cluster_id", clusterId).eq("hdfs_path", dirPath);
                    if (catalogTableMapper.selectCount(qw) > 0) {
                        log.debug("Table already exists for path: {}", dirPath);
                        continue;
                    }

                    CatalogTable table = new CatalogTable();
                    table.setClusterId(clusterId);
                    table.setName(dirName);
                    table.setHdfsPath(dirPath);
                    table.setSchemaName("default");
                    table.setFormat("TEXT");
                    table.setOwner(dir.getOwner());
                    table.setRowCount(0L);
                    table.setFileCount(0);
                    table.setTotalSizeBytes(dir.getLen());
                    table.setCreateTime(LocalDateTime.now());
                    table.setUpdateTime(LocalDateTime.now());
                    catalogTableMapper.insert(table);
                    created.add(table);
                    log.info("Auto-discovered table: {} at {}", dirName, dirPath);
                }
            }
        } catch (Exception e) {
            log.warn("Auto-discover failed for cluster={}, path={}: {}", clusterId, basePath, e.getMessage());
            throw new RuntimeException("Auto-discover failed: " + e.getMessage(), e);
        }

        return created;
    }

    // ==================== Lineage ====================

    public List<OperationLog> getLineage(Long tableId) {
        CatalogTable table = catalogTableMapper.selectById(tableId);
        if (table == null) {
            throw new RuntimeException("Catalog table not found: " + tableId);
        }
        if (operationLogMapper == null) {
            return new ArrayList<>();
        }

        String hdfsPath = table.getHdfsPath();
        // Find operation_log entries where target contains this HDFS path
        // This is a simple heuristic — in production you'd have a proper lineage store
        QueryWrapper<OperationLog> qw = new QueryWrapper<>();
        qw.like("target", hdfsPath)
           .orderByDesc("create_time");
        return operationLogMapper.selectList(qw);
    }

    // ==================== Schema List ====================

    public List<String> listSchemas() {
        return catalogTableMapper.selectList(
            new QueryWrapper<CatalogTable>().select("DISTINCT schema_name")
        ).stream().map(CatalogTable::getSchemaName).distinct().collect(Collectors.toList());
    }
}
