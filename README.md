# 企业级 Hadoop 集群管理平台

**GitHub**: https://github.com/XieYeWei/data

## v0.9 已完成

- ✅ 多集群 + Kerberos
- ✅ 完整审计 + 定时快照
- ✅ MapReduce 模板提交
- ✅ **YARN 队列监控图表** + **历史趋势** (美化折线图 + 时间筛选)
- ✅ **告警规则完整持久化 + 编辑功能**
- ✅ **队列历史数据可视化增强** (Multi-queue support)
- ✅ `docs/ledger/` 台账系统 + `db/migration/` 迁移脚本

```bash
cd hermes-backend && mvn clean package && java -jar target/*.jar
cd hermes-frontend && npm install && npm run dev
```