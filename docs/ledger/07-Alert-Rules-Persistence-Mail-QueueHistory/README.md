# 变更台账 - 告警规则完整持久化 + Spring Mail 通知 + 队列历史数据优化

**Date**: 2026-06-15
**Version**: v0.8

## 本次实现内容

### 1. 前端告警规则完整持久化 + 列表管理
- 实现完整的前端告警规则 CRUD
- 加载、添加、删除规则
- 与后端完整对接

### 2. 真正的 Spring Mail 邮件通知
- 添加 `spring-boot-starter-mail`
- `NotificationService` 实现邮件发送
- 在触发告警时自动发送邮件

### 3. 队列历史数据持久化优化
- 增强 `MetricsCollector` 收集每个队列的快照
- 使用 `extraJson` 存储队列级详细数据
- 优化历史查询接口

**Status**: 已完成
