# 企业级 Hadoop 集群管理平台

**GitHub**: https://github.com/XieYeWei/data

## 当前已完成功能 (v0.5)

- ✅ 多集群动态加载 + Kerberos
- ✅ 完整审计日志 + 定时快照
- ✅ MapReduce 模板提交 (官方 Job API)
- ✅ **YARN 队列监控图表** (ECharts 实时容量使用率 + Running Apps)
- ✅ `docs/ledger/` 台账系统

## 快速开始

```bash
cd hermes-backend && mvn clean package && java -jar target/*.jar
cd hermes-frontend && npm install && npm run dev
```