# 企业级 Hadoop 集群管理平台

**GitHub**: https://github.com/XieYeWei/data

## 当前已完成功能 (v0.4)

- ✅ 多集群动态加载 + Kerberos keytab 真实支持
- ✅ 完整审计日志 + 定时指标快照
- ✅ **MapReduce 模板提交** 深度实现（官方 Job API + HDFS JAR）
- ✅ YARN 队列管理策略支持
- ✅ 完整前端 SPA + ECharts Dashboard
- ✅ `docs/ledger/` 台账系统（优化命名规范：`NN-Feature-Name` + 详细 README）

## 更新

所有重大更新都在 `docs/ledger/` 下以编号方式记录，方便团队审计与回溯。

## 快速开始

```bash
# 后端
cd hermes-backend && mvn clean package && java -jar target/hermes-backend-0.0.1-SNAPSHOT.jar

# 前端
cd hermes-frontend && npm install && npm run dev
```
