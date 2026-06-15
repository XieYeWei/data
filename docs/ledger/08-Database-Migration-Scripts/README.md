# 变更台账 - 数据库迁移脚本管理

**Date**: 2026-06-15
**Version**: v0.9

## 本次建立规范

- 新增专用目录 `hermes-backend/src/main/resources/db/migration/`
- 所有涉及表结构更新的代码修改，必须同步写入对应的 SQL 迁移文件
- 采用 Flyway 命名规范：`V{version}__{description}.sql`

## 当前已创建的初始化脚本

- `V1__init_all_tables.sql`：初始化所有表结构
  - user
  - cluster
  - operation_log
  - metric_snapshot
  - job_template
  - queue_alert_rule

**Status**: 规范已建立
