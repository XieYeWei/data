# 企业级 Hadoop 集群管理平台 (Hadoop Cluster Management Platform)

**GitHub**: https://github.com/XieYeWei/data

## 当前已完成功能 (2026-06-15 最新)

- ✅ 多集群动态 FileSystem / YarnClient
- ✅ HDFS 文件浏览器 (CRUD 基础)
- ✅ YARN 应用列表 / 提交 / Kill / 指标
- ✅ **完整前端 SPA**：登录弹窗 + JWT 自动附加 + **3个 Tab**
  - **Dashboard**：ECharts 图表 (HDFS 存储饼图 + YARN 资源柱状图) + 实时刷新
  - **HDFS 文件管理**
  - **YARN 应用管理** (提交弹窗 + Kill)
- ✅ Spring Security + JWT (完整登录流程)
- ✅ MyBatis-Plus + H2 审计表 (User / Cluster / OperationLog)
- ✅ Kerberos keytab 支持架构
- ✅ Dashboard API 集成 HDFS + YARN 指标

## 快速开始

```bash
# 后端
cd hermes-backend && mvn clean package && java -jar target/*.jar

# 前端
cd hermes-frontend && npm install && npm run dev
```

**Demo 账号**：`admin` / `admin` （或 `user` / `user`）

登录后即可使用所有功能。

## 项目状态

前端已实现完整可视化交互平台，后端核心功能齐全。
可直接用于演示或作为企业内部开发基础。

欢迎继续提交 PR 或请求新功能！