# 变更台账 - 队列历史趋势 + 告警规则 + 动态权重调整

**Date**: 2026-06-15
**Version**: v0.6

## 本次同时实现的 3 个需求

### 1. 前端队列历史趋势图表
- 扩展 `MetricSnapshot` 支持队列级数据
- 新增 `/api/v1/metrics/queue-history` 接口
- 前端 ECharts **折线图** 展示历史容量使用率趋势

### 2. 队列告警规则
- 新增 `QueueAlertRule` 实体
- 支持阈值设置（usedCapacity > X% 或 numApplications > N）
- 提供检查接口 `/api/v1/yarn/check-alerts`

### 3. 动态队列权重调整接口
- 新增 `POST /api/v1/yarn/queues/adjust-weight`
- 支持修改队列 capacity / maxCapacity
- 集成审计日志
- 提供实际 YARN refreshQueues 指导

**Status**: 核心功能已完成
