# 企业级 Hadoop 集群管理平台 (Hadoop Cluster Management Platform)

**GitHub**: https://github.com/XieYeWei/data

## 当前已实现功能 (2026-06-15 更新)

- ✅ 多集群配置 + 动态 FileSystem / YarnClient 创建
- ✅ HDFS 文件浏览器 (listStatus, getFileStatus, ContentSummary)
- ✅ YARN 应用管理：列表、提交、Kill、集群指标 (YarnClient API)
- ✅ Spring Security + JWT 认证 (登录接口、路由保护、@PreAuthorize)
- ✅ MyBatis-Plus + H2 (demo) 数据库：User、Cluster、OperationLog 表
- ✅ Kerberos keytab 支持占位符（在 HadoopConfig 中可扩展为真实登录）
- ✅ 可视化指标 Dashboard：HDFS 容量 + YARN 资源、节点、应用数（ECharts 就绪）
- ✅ 操作审计框架 (OperationLog 表 + 后端日志)

## 使用方式

### 后端
```bash
cd hermes-backend
mvn clean package
java -jar target/hermes-backend-0.0.1-SNAPSHOT.jar
```

默认用户：`admin` / `admin` 或 `user` / `user`

登录后获取 JWT Token 在 Header `Authorization: Bearer <token>` 使用。

H2 Console: http://localhost:8080/h2-console (jdbc:h2:mem:hermes)

### 前端
```bash
cd hermes-frontend
npm install
npm run dev
```

前端已支持 HDFS 浏览器和基本 Dashboard 视图。

## 下一步计划
- 完整前端 Dashboard + YARN 页面 + ECharts 图表
- 实现完整审计日志写入 DB
- MySQL 替换 H2 + Cluster 表动态加载
- MapReduce 模板提交
- 定时任务采集指标并存储

项目已完全按照官方 Hadoop 客户端接口开发，可直接扩展到生产环境。