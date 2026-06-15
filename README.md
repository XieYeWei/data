# 企业级 Hadoop 集群管理平台

**GitHub**: https://github.com/XieYeWei/data

## 当前已完成功能

- ✅ 多集群 HDFS + YARN 客户端集成
- ✅ Spring Security + JWT 完整登录
- ✅ MyBatis-Plus 审计表 (User / Cluster / OperationLog / MetricSnapshot)
- ✅ **实时审计日志**：HDFS/YARN 关键操作自动写入 OperationLog
- ✅ **定时指标采集**：@Scheduled 每 5 分钟收集 HDFS/YARN 指标并存入 MetricSnapshot 表
- ✅ 完整前端 SPA + ECharts Dashboard + YARN 管理
- ✅ Kerberos 架构支持

## 快速开始

```bash
cd hermes-backend && mvn clean package && java -jar target/*.jar
cd hermes-frontend && npm install && npm run dev
```

**Demo 账号**：admin / admin

现在已具备完整的运维、审计、可视化能力。