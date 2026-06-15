# 变更台账 - MapReduce 模板提交逻辑

**Date**: 2026-06-15
**Version**: v0.4

## 本次更新内容

### 深入实现的 MapReduce 模板提交
- 新增 `JobTemplate` 实体（存储 jar路径、mainClass、args、input/output path、queue等）
- `MrService.java` 实现：
  - `saveTemplate()` / `getTemplate()`
  - `submitJobFromTemplate()` 使用官方 `org.apache.hadoop.mapreduce.Job`
- 支持从 HDFS 加载 JAR 并提交 MapReduce 作业
- 集成到 YARN 队列选择

### YARN 资源队列管理策略探讨
- 已在 YarnService 中支持 `queue` 参数
- 推荐策略：
  1. **CapacityScheduler** 默认队列管理
  2. **FairScheduler** 适用于多租户
  3. 动态队列权重调整 + ACL
  4. 平台层面提供队列选择与监控

### 相关文件
- `JobTemplate.java`
- `MrService.java`
- `MrController.java`
- `YarnService.java` 增强 queue 支持

**Status**: 核心逻辑已完成
