package com.hermes.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hermes.entity.CatalogColumn;
import com.hermes.entity.CatalogTable;
import com.hermes.entity.CatalogTag;
import com.hermes.service.catalog.CatalogService;
import com.hermes.util.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    @Autowired
    private CatalogService catalogService;

    // ==================== Tables ====================

    @GetMapping("/tables")
    public R listTables(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) Long tagId) {
        IPage<CatalogTable> result = catalogService.listTables(page, size, name, schema, format, tagId);
        Map<String, Object> data = new HashMap<>();
        data.put("items", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("size", result.getSize());
        return R.success(data);
    }

    @GetMapping("/tables/{id}")
    public R getTable(@PathVariable Long id) {
        CatalogTable table = catalogService.getTable(id);
        if (table == null) {
            return R.error(404, "Catalog table not found: " + id);
        }
        List<CatalogColumn> columns = catalogService.getColumnsByTableId(id);
        List<CatalogTag> tags = catalogService.getTableTags(id);
        Map<String, Object> data = new HashMap<>();
        data.put("table", table);
        data.put("columns", columns);
        data.put("tags", tags);
        return R.success(data);
    }

    @PostMapping("/tables")
    public R createTable(@RequestBody CatalogTable table) {
        try {
            CatalogTable created = catalogService.createTable(table);
            log.info("Created catalog table: id={}, name={}", created.getId(), created.getName());
            return R.success(created);
        } catch (Exception e) {
            log.error("Failed to create catalog table", e);
            return R.error("Failed to create table: " + e.getMessage());
        }
    }

    @PutMapping("/tables/{id}")
    public R updateTable(@PathVariable Long id, @RequestBody CatalogTable table) {
        try {
            CatalogTable updated = catalogService.updateTable(id, table);
            log.info("Updated catalog table: id={}", id);
            return R.success(updated);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return R.error(404, e.getMessage());
            }
            log.error("Failed to update catalog table id={}", id, e);
            return R.error("Failed to update table: " + e.getMessage());
        }
    }

    @DeleteMapping("/tables/{id}")
    public R deleteTable(@PathVariable Long id) {
        try {
            catalogService.deleteTable(id);
            log.info("Deleted catalog table: id={}", id);
            return R.success();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return R.error(404, e.getMessage());
            }
            log.error("Failed to delete catalog table id={}", id, e);
            return R.error("Failed to delete table: " + e.getMessage());
        }
    }

    @PostMapping("/tables/{id}/scan")
    public R scanTable(@PathVariable Long id) {
        try {
            CatalogTable scanned = catalogService.scanTable(id);
            log.info("Scanned catalog table: id={}", id);
            return R.success(scanned);
        } catch (RuntimeException e) {
            log.error("Failed to scan catalog table id={}", id, e);
            return R.error("Failed to scan table: " + e.getMessage());
        }
    }

    // ==================== Columns ====================

    @GetMapping("/tables/{id}/columns")
    public R getColumns(@PathVariable Long id) {
        List<CatalogColumn> columns = catalogService.getColumnsByTableId(id);
        return R.success(columns);
    }

    @PostMapping("/columns")
    public R addColumn(@RequestBody CatalogColumn column) {
        try {
            CatalogColumn created = catalogService.addColumn(column);
            return R.success(created);
        } catch (Exception e) {
            log.error("Failed to add column", e);
            return R.error("Failed to add column: " + e.getMessage());
        }
    }

    @PutMapping("/columns/{id}")
    public R updateColumn(@PathVariable Long id, @RequestBody CatalogColumn column) {
        try {
            CatalogColumn updated = catalogService.updateColumn(id, column);
            return R.success(updated);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return R.error(404, e.getMessage());
            }
            log.error("Failed to update column id={}", id, e);
            return R.error("Failed to update column: " + e.getMessage());
        }
    }

    @DeleteMapping("/columns/{id}")
    public R deleteColumn(@PathVariable Long id) {
        try {
            catalogService.deleteColumn(id);
            return R.success();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return R.error(404, e.getMessage());
            }
            log.error("Failed to delete column id={}", id, e);
            return R.error("Failed to delete column: " + e.getMessage());
        }
    }

    // ==================== Tags ====================

    @GetMapping("/tags")
    public R listTags() {
        List<CatalogTag> tags = catalogService.listTags();
        return R.success(tags);
    }

    @PostMapping("/tags")
    public R createTag(@RequestBody CatalogTag tag) {
        try {
            CatalogTag created = catalogService.createTag(tag);
            return R.success(created);
        } catch (Exception e) {
            log.error("Failed to create tag", e);
            return R.error("Failed to create tag: " + e.getMessage());
        }
    }

    @DeleteMapping("/tags/{id}")
    public R deleteTag(@PathVariable Long id) {
        try {
            catalogService.deleteTag(id);
            return R.success();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return R.error(404, e.getMessage());
            }
            log.error("Failed to delete tag id={}", id, e);
            return R.error("Failed to delete tag: " + e.getMessage());
        }
    }

    // ==================== Table-Tag ====================

    @PostMapping("/tables/{id}/tags")
    public R setTableTags(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        try {
            List<Long> tagIds = body.get("tagIds");
            catalogService.setTableTags(id, tagIds);
            log.info("Set tags for table id={}: {}", id, tagIds);
            return R.success();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return R.error(404, e.getMessage());
            }
            log.error("Failed to set tags for table id={}", id, e);
            return R.error("Failed to set tags: " + e.getMessage());
        }
    }

    // ==================== Schemas ====================

    @GetMapping("/schemas")
    public R listSchemas() {
        List<String> schemas = catalogService.listSchemas();
        return R.success(schemas);
    }

    // ==================== Lineage ====================

    @GetMapping("/tables/{id}/lineage")
    public R getLineage(@PathVariable Long id) {
        try {
            var lineage = catalogService.getLineage(id);
            return R.success(lineage);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return R.error(404, e.getMessage());
            }
            log.error("Failed to get lineage for table id={}", id, e);
            return R.error("Failed to get lineage: " + e.getMessage());
        }
    }

    // ==================== Auto Discover ====================

    @PostMapping("/discover")
    public R autoDiscover(@RequestBody Map<String, String> body) {
        String clusterId = body.get("clusterId");
        String basePath = body.get("basePath");
        if (clusterId == null || basePath == null) {
            return R.error("clusterId and basePath are required");
        }
        try {
            var tables = catalogService.autoDiscover(clusterId, basePath);
            log.info("Auto-discovered {} tables from {}{}", tables.size(), clusterId, basePath);
            return R.success(tables);
        } catch (RuntimeException e) {
            log.error("Auto-discover failed", e);
            return R.error("Auto-discover failed: " + e.getMessage());
        }
    }
}
