# Shared Context for Parallel Agents

## Project Structure
- Frontend single file: /data/datapalform/data/hermes-frontend/src/App.vue (Vue 3 + Element Plus)
- Backend: Spring Boot 3.2.5 in /data/datapalform/data/hermes-backend/
- Java source: src/main/java/com/hermes/

## API Patterns
- Base: /api/v1/
- Auth: POST /api/v1/auth/login (admin/admin) → returns {code:0, data:{token:"xxx"}}
- Auth header: Authorization: Bearer <token>
- Response: {code: 0, msg: "success", data: {...}} for success
- Error: {code: 500, msg: "error message", data: null}
- All endpoints require auth except /api/v1/auth/**

## Frontend Conventions
- Single file component App.vue with <template>, <script setup>, <style scoped>
- Script imports: ref, reactive, computed, onMounted, watch, onUnmounted, nextTick from vue
- Plus: axios, ElMessage, ElMessageBox from element-plus
- Dark theme colors:
  - bg: #0f172a, card: #1e2938, border: #334155
  - accent: #3b82f6 (blue), #22c55e (green), #eab308 (yellow), #ef4444 (red)
  - text: #f1f5f9 (primary), #94a3b8 (secondary)

## Backend Conventions
- Controllers use @RestController + @RequestMapping("/api/v1/...")
- Services use @Service + @Autowired
- HDFS operations via Hadoop FileSystem API (HdfsService)
- YARN operations via YarnClient API (YarnService)
- Docker exec via Runtime.getRuntime().exec() in HdfsController for some ops
- All controllers inject HdfsService, YarnService, HermesProperties
- Hadoop 3.3.6 API used throughout

## Existing Modules
- HDFS: files, nodes, blocks, mkdir, chmod, delete, upload, download, switch-nn, manage-nn, checkpoint, trigger-checkpoint
- HDFS Trash: move-to-trash (POST /api/v1/hdfs/trash), restore (POST /api/v1/hdfs/trash/restore), list (GET /api/v1/hdfs/trash/list), empty (POST /api/v1/hdfs/trash/empty)
- YARN: apps list/detail/kill, metrics, queues
- Dashboard: overview (combines HDFS+YARN data)
- MR Templates: list/create/submit

## Tabs/Pages
tabs array: dashboard, hdfs, yarn-apps, yarn-queues, mr
hdfs subTabs: files, trash, datanodes, journalnodes, monitor, logs
