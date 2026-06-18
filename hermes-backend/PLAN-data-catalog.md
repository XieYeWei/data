# 数据目录 / 元数据管理 — 开发计划

## 概览
构建轻量级数据目录系统（无 Hive Metastore 依赖），管理 HDFS 上的数据集元数据，支持表浏览、列定义、标签、搜索、数据血缘。

---

## 1. 数据模型 (4 张新表)

### 1.1 `catalog_table` — 表/数据集注册
```sql
CREATE TABLE IF NOT EXISTS `catalog_table` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `cluster_id` VARCHAR(50) NOT NULL,
    `name` VARCHAR(200) NOT NULL,           -- 表名
    `hdfs_path` VARCHAR(1000) NOT NULL,     -- HDFS 路径
    `schema_name` VARCHAR(200) DEFAULT 'default', -- 逻辑 schema/数据库名
    `format` VARCHAR(50) DEFAULT 'TEXT',    -- 格式: TEXT/CSV/Parquet/ORC/Avro/SequenceFile
    `owner` VARCHAR(100),
    `description` TEXT,
    `row_count` BIGINT DEFAULT 0,           -- 行数(采样估算)
    `file_count` INT DEFAULT 0,             -- 文件数
    `total_size_bytes` BIGINT DEFAULT 0,    -- 总大小
    `partition_columns` VARCHAR(500),       -- 分区列(逗号分隔)
    `created_by` VARCHAR(100),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ct_cluster_schema ON catalog_table(cluster_id, schema_name);
CREATE INDEX IF NOT EXISTS idx_ct_cluster_name ON catalog_table(cluster_id, name);
```

### 1.2 `catalog_column` — 列定义
```sql
CREATE TABLE IF NOT EXISTS `catalog_column` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `table_id` BIGINT NOT NULL,
    `name` VARCHAR(200) NOT NULL,
    `type` VARCHAR(100) NOT NULL,           -- STRING/INT/LONG/DOUBLE/DECIMAL/DATE/TIMESTAMP/BOOLEAN
    `comment` VARCHAR(500),
    `nullable` BOOLEAN DEFAULT TRUE,
    `is_partition` BOOLEAN DEFAULT FALSE,
    `ordinal_position` INT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cc_table ON catalog_column(table_id);
```

### 1.3 `catalog_tag` — 标签体系
```sql
CREATE TABLE IF NOT EXISTS `catalog_tag` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL UNIQUE,   -- 标签名
    `color` VARCHAR(20) DEFAULT '#3b82f6', -- 标签颜色
    `description` VARCHAR(500),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 1.4 `catalog_table_tag` — 表-标签关联
```sql
CREATE TABLE IF NOT EXISTS `catalog_table_tag` (
    `table_id` BIGINT NOT NULL,
    `tag_id` BIGINT NOT NULL,
    PRIMARY KEY (`table_id`, `tag_id`)
);
```

---

## 2. 后端 API (`CatalogController`)

| 方法 | 路径 | 说明 | 分页 | 权限 |
|------|------|------|------|------|
| GET | `/api/v1/catalog/tables` | 列表搜索(名称/路径/格式/标签) | ✅ page/size | view |
| GET | `/api/v1/catalog/tables/{id}` | 表详情(含列+标签) | - | view |
| POST | `/api/v1/catalog/tables` | 注册新表 | - | admin/operator |
| PUT | `/api/v1/catalog/tables/{id}` | 更新表元数据 | - | admin/operator |
| DELETE | `/api/v1/catalog/tables/{id}` | 删除表注册 | - | admin |
| POST | `/api/v1/catalog/tables/{id}/scan` | 扫描 HDFS 路径估算行数/文件数 | - | operator |
| GET | `/api/v1/catalog/tables/{id}/columns` | 列列表 | ✅ | view |
| POST | `/api/v1/catalog/columns` | 新增列 | - | operator |
| PUT | `/api/v1/catalog/columns/{id}` | 更新列 | - | operator |
| DELETE | `/api/v1/catalog/columns/{id}` | 删除列 | - | admin |
| GET | `/api/v1/catalog/tags` | 标签列表 | ✅ | view |
| POST | `/api/v1/catalog/tags` | 创建标签 | - | operator |
| DELETE | `/api/v1/catalog/tags/{id}` | 删除标签 | - | admin |
| POST | `/api/v1/catalog/tables/{id}/tags` | 设置标签(传 tag_id 数组) | - | operator |
| GET | `/api/v1/catalog/tables/{id}/lineage` | 数据血缘(依赖的上/下游表) | - | view |
| POST | `/api/v1/catalog/auto-discover` | 自动扫描 HDFS 目录发现新表 | - | admin |
| GET | `/api/v1/catalog/schemas` | 列出所有 schema 名称 | - | view |

---

## 3. 前端

### 3.1 新标签页: "📚 数据目录"
- 位置：侧边栏，排在"总览"之后
- 视图：
  - **表列表** — 表格视图(el-table)：名称、格式、Schema、行数(估算)、大小、标签(彩色Badge)、操作(编辑/删除/扫描)
  - **搜索/筛选** — 名称搜索、格式下拉、标签多选、Schema 筛选
  - **详情抽屉** — el-drawer 显示完整表信息、列定义列表(类型/nullable/分区标记)、标签编辑

### 3.2 注册弹窗
- el-dialog 表单：名称、HDFS 路径、格式、Schema 名、描述、分区列
- 提交时创建 + 自动扫描目录获取文件数和大小

### 3.3 标签管理
- el-dialog 管理标签：已有标签列表 + 新建标签输入
- 标签选择器：在表详情中通过 el-select(可多选) 选择标签

---

## 4. 集成点

### 4.1 与现有 HDFS 文件系统集成
- 在 HDFS 文件浏览器的文件右键菜单增加"注册到数据目录"
- 表详情页显示关联的文件路径，可点击跳转到文件系统

### 4.2 与现有 FileNote 集成
- 在目录表的详情中显示关联的文件笔记

### 4.3 与数据血缘集成(现有 YARN 分析)
- YarnService 已解析 App 的输入输出路径 → 可自动建立 CatalogTable 之间的 lineage 关系
- 存储到 `catalog_table_lineage` 表(可选扩展)

### 4.4 自动发现
- 扫描 HDFS 下特定目录(如 `/data`, `/warehouse`, `/user/hive/warehouse`)
- 识别子目录作为候选表
- 采样读取前 10 行估算格式和列

---

## 5. 实施顺序

1. **阶段 A** (基础): schema 创建 + 实体 + Mapper + Controller CRUD + 前端表列表 + 注册弹窗
2. **阶段 B** (增强): 标签体系 + 列管理 + 详情 Drawer + 搜索筛选
3. **阶段 C** (自动化): 自动扫描发现 + 血缘集成 + HDFS 右键注册
