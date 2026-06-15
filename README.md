# 企业级 Hadoop 集群管理平台

**GitHub**: https://github.com/XieYeWei/data

## v0.6 已完成功能

- ✅ 多集群 + Kerberos
- ✅ 完整审计日志 + 定时快照
- ✅ MapReduce 模板提交
- ✅ **YARN 队列监控图表** + **历史趋势**
- ✅ **队列告警规则** + **动态权重调整**
- ✅ `docs/ledger/` 台账系统

## 快速开始

```bash
cd hermes-backend && mvn clean package && java -jar target/*.jar
cd hermes-frontend && npm install && npm run dev
```