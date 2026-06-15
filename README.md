# 企业级 Hadoop 集群管理平台

**GitHub**: https://github.com/XieYeWei/data

## v0.8 已完成 (2026-06-15)

- ✅ 多集群 + Kerberos
- ✅ 完整审计 + 定时快照
- ✅ MapReduce 模板提交
- ✅ YARN 队列监控图表 + 历史趋势
- ✅ **告警规则完整持久化 + 列表管理**
- ✅ **Spring Mail 真实邮件通知**
- ✅ **队列历史数据持久化优化**
- ✅ `docs/ledger/` 台账系统

```bash
cd hermes-backend && mvn clean package && java -jar target/*.jar
cd hermes-frontend && npm install && npm run dev
```