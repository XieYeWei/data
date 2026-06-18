# Hermes Platform — Complete Feature Checklist

## ✅ Core Platform (30+ features)
✅ Design System CSS (colors, skeleton, loading bar)
✅ Dashboard enhancements (health score, key metrics, quick actions)
✅ YARN Applications page (full table, filters, kill, details drawer)
✅ YARN Queue tree view + capacity editor
✅ DataNode page (badges, storage chart, logs link)
✅ File system (breadcrumbs, search, icons, rename/move, batch, drag-drop, shortcuts, preview, status bar, copy path, open in new tab, ACL display)
✅ JournalNode checkpoint monitor + manual trigger
✅ Log viewer (multi-level, auto-tail, highlighting, copy/clear, ms toggle)
✅ NN management (one-click switch, selectable stop/start/restart)
✅ HDFS Recycle bin (trash, restore, empty)
✅ Operation Audit Log page
✅ Command Palette (Ctrl+K)
✅ User management (CRUD, password reset, roles admin/operator/viewer)
✅ Role-based access control
✅ Alert center (rules CRUD + history)
✅ File upload/download with progress bars
✅ Data lineage (job input/output file tracking)
✅ Spark app detail (stages, executors, tasks in YARN drawer)
✅ Monitoring dashboard (in-app sparkline charts + Grafana embed)
✅ Compact/comfort table mode toggle
✅ Hover action buttons + success row highlight
✅ Notification bell (30s polling for failed ops)
✅ Session timeout handling (401 auto-redirect)
✅ Global error boundary + enhanced error messages
✅ Dynamic page titles + footer
✅ Cluster connection indicator (green/red dot)
✅ Help modal (shortcuts, system info, quick start)
✅ Page transition animations + success flash

## ✅ Phase 2 Enhancements (8 features)
✅ HDFS ACL operations (setfacl/getfacl)
✅ Notebook integration (Jupyter URL input + iframe embed)
✅ System health check endpoint (6 components: HDFS HA, YARN RM, NN, DN, JN, ZK)
✅ Multi-cluster management (backend CRUD + frontend cluster selector dropdown)
✅ File system server-side pagination (cache + offset/limit, 60s TTL)
✅ Grafana dashboard embed (mode toggle, URL config from yml + localStorage)
✅ System config page (read-only grouped property display, 6 config groups)
✅ Cross-page state persistence (tab, path, pagination saved to localStorage)

## ✅ Phase 3 Features (2 new major modules)

### 📚 数据目录 / 元数据管理
✅ **4 张新表**: catalog_table, catalog_column, catalog_tag, catalog_table_tag
✅ **后端 17 个 API**: 表 CRUD + 列管理 + 标签体系 + 自动发现 + 数据血缘
✅ **前端 📚 数据目录标签页**: 表格列表 + 搜索筛选 + 注册/编辑弹窗 + 详情 Drawer + 标签管理
✅ **HDFS 右键注册**: 文件浏览器右键菜单"注册到数据目录"
✅ **自动扫描发现**: POST /api/v1/catalog/discover 扫描 HDFS 目录自动创建表记录
✅ **数据血缘**: 从 operation_log 追踪表的上游/下游依赖

### 🔗 工作流编排引擎
✅ **4 张新表**: workflow_definition, workflow_step, workflow_execution, workflow_step_execution
✅ **后端 15 个 API**: 工作流/步骤 CRUD + 执行触发/取消 + 历史查询
✅ **DAG 执行引擎**: 拓扑排序(Kahn) + 线程池异步执行 + 依赖推进 + 环检测
✅ **4 种步骤类型**: MAPREDUCE(复用 MrService) / SHELL(ProcessBuilder) / WAIT / HTTP
✅ **定时调度器**: @Scheduled 每分钟轮询 cron 表达式
✅ **Webhook 通知**: 执行完成后 POST 回调到配置 URL
✅ **前端 🔗 工作流标签页**: 工作流列表 + 步骤管理 + 执行触发 + 执行历史 + 详情 Drawer

## Total: 55+ features, all verified
