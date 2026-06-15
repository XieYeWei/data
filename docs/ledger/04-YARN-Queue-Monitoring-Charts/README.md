# 变更台账 - YARN 队列监控图表

**Date**: 2026-06-15
**Version**: v0.5

## 本次更新内容

### 后端增强
- `YarnService` 新增 `getAllQueues()` 方法
- 使用官方 `YarnClient.getAllQueues()` + `QueueInfo`
- 返回队列名称、容量、已使用容量、应用数、资源使用情况
- 新增 `YarnController` 接口 `/api/v1/yarn/queues`

### 前端图表
- 在 YARN Tab 中添加队列监控区域
- ECharts 柱状图：各队列容量使用率
- ECharts 条状图：每队列 Running Apps 数量
- 支持实时刷新

### YARN 队列管理策略扩展
- 结合前期队列支持，现在可视化监控各队列资源使用情况
- 为后续动态调整队列权重、告警提供数据支持

**Status**: 已完成
