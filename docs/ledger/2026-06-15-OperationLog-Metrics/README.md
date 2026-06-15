# 变更台账 - OperationLog 实时审计 + 定时指标快照

**日期**: 2026-06-15
**版本**: v0.2

## 本次更新内容

### 1. 实时操作审计日志 (OperationLog)
- HdfsService 和 YarnService 关键方法自动写入 `operation_log` 表
- 包含字段：userId, clusterId, module, action, target, result, createTime
- 已实现的操作：
  - HDFS: list, getStatus, getSummary
  - YARN: listApps, submitApp, killApp

### 2. 定时指标快照 (MetricSnapshot)
- 新增 `MetricsCollector.java`
- `@Scheduled(fixedRate = 300000)` 每 5 分钟自动采集
- 同时收集 HDFS 和 YARN 指标
- 存入 `metric_snapshot` 表

### 3. 相关文件变更
- `HermesApplication.java` 添加 `@EnableScheduling`
- `MetricSnapshot.java` + `MetricSnapshotMapper.java`
- HdfsService / YarnService 增强审计写入逻辑

### 4. 测试方式
- 登录后操作 HDFS/YARN
- H2 Console 查看 `operation_log` 和 `metric_snapshot` 表

**Status**: 已完成并已测试
