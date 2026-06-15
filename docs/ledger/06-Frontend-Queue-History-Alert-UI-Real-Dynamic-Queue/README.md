# 变更台账 - 前端队列历史趋势 + 告警管理界面 + 真实动态队列更新 + 告警通知

**Date**: 2026-06-15
**Version**: v0.7

## 本次实现内容

### 1. 前端完善
- YARN Tab 添加 **历史趋势折线图**
- **告警规则管理界面**（添加规则 Form + 当前规则列表 + 检查告警按钮）

### 2. 真实动态队列更新
- 使用 YARN ResourceManager REST API 实现队列容量调整
- 支持动态更新 CapacityScheduler 队列

### 3. 告警通知
- 集成 Spring Mail 邮件通知
- 支持钉钉机器人 Webhook 通知
- 在 `checkQueueAlerts` 中触发后自动发送

**Status**: 核心功能已完成
