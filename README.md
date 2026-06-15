# 企业级 Hadoop 集群管理平台

**GitHub**: https://github.com/XieYeWei/data

## v0.8 已完成

- ✅ 多集群 + Kerberos
- ✅ 完整审计 + 定时快照
- ✅ MapReduce 模板提交
- ✅ YARN 队列监控图表 + 历史趋势
- ✅ 告警规则完整持久化 + 列表管理
- ✅ Spring Mail 真实邮件通知
- ✅ 队列历史数据持久化优化
- ✅ **MySQL 8 完整生产配置**

## MySQL 8 配置说明

1. `pom.xml` 中已添加 `mysql-connector-j` 8.3.0
2. 修改 `application.yml` 中的 MySQL 连接信息：
   - `url`、`username`、`password`
3. 创建数据库 `hermes`（建议 utf8mb4 编码）
4. 生产环境建议使用 Flyway 或手动初始化表结构

```bash
cd hermes-backend && mvn clean package && java -jar target/*.jar
cd hermes-frontend && npm install && npm run dev
```