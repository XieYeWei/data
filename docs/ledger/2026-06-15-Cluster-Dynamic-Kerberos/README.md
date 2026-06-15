# 变更台账 - Cluster 动态加载 + Kerberos 支持

**日期**: 2026-06-15
**版本**: v0.3

## 本次更新内容

### 1. Cluster 表动态加载
- `HadoopConfig` 增强从 `ClusterMapper` 动态加载集群配置
- 支持多集群切换（通过 clusterId 查询 DB）
- 配置项：namenode, resourcemanager, version, authType, keytabPath, principal

### 2. Kerberos 真实支持
- 当 `authType = kerberos` 时自动执行 `UserGroupInformation.loginUserFromKeytab()`
- 支持代理用户 `doAs`
- 配置示例已在 `application.yml` 和 Cluster 表中预留

### 3. 相关文件变更
- `HadoopConfig.java` 重构为动态加载模式
- 新增 `getClusterEntity()` 方法
- 更新的 `buildConfForCluster()` 支持 Kerberos

**Status**: 已完成
