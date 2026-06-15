# 企业级 Hadoop 集群管理平台 (Hadoop Cluster Management Platform)

> 基于 Apache Hadoop 官方仓库中 HDFS、YARN、MapReduce 核心模块的客户端实现（client libraries），构建一个现代化、易用、安全的前后端分离的 Web 管理平台。

**GitHub**: https://github.com/XieYeWei/data

## 项目概述与目标

本项目旨在解决 Hadoop 集群日常运维痛点：默认 Web UI 分散、功能有限、缺乏统一审计与权限管控、作业提交流程繁琐。平台以 Spring Boot + Vue + MySQL 技术栈实现前后端完全分离，通过 RESTful API 提供服务。

**核心目标**:
- 实现对标准 Apache Hadoop 集群（HDFS + YARN + MapReduce）的统一可视化管理与监控。
- 提供安全、可审计的操作入口（支持代理用户、操作日志持久化）。
- 支持多集群管理、作业模板化提交、资源可视化。
- 确保与 Hadoop 官方客户完全兼容（推荐 Hadoop 3.3.x / 3.4.x）。

**非目标**（明确边界）:
- 不重新开发或 Fork HDFS NameNode/DataNode、YARN ResourceManager/NodeManager、MapReduce 框架。
- 不存储实际文件数据（仅元数据与操作审计）。
- 初期不包含完整的集群生命周期管理。

## Hadoop HDFS 核心客户端接口（基于官方仓库分析）

根据 Apache Hadoop 官方仓库 (https://github.com/apache/hadoop) 中 hadoop-hdfs-project/hadoop-hdfs-client 和 hadoop-common-project/hadoop-common 的源码和 API 文档，HDFS 主要客户端接口如下（推荐使用 Java 官方客户而非纯 WebHDFS，因为功能更完整、性能更优、安全可控）：

### 主要类和接口

1. **org.apache.hadoop.fs.FileSystem** (Abstract)
   - Hadoop 文件系统的统一抽象层。
   - 关键方法：
     - `static FileSystem get(Configuration conf)` / `get(URI uri, Configuration conf)`
     - `FSDataOutputStream create(Path f, boolean overwrite)`
     - `FSDataInputStream open(Path f)`
     - `boolean delete(Path f, boolean recursive)`
     - `boolean mkdirs(Path f)`
     - `boolean rename(Path src, Path dst)`
     - `FileStatus[] listStatus(Path f)`
     - `FileStatus getFileStatus(Path f)`
     - `void setPermission(Path p, FsPermission permission)`
     - `void setOwner(Path p, String username, String groupname)`
     - `void setReplication(Path p, short replication)`
     - `ContentSummary getContentSummary(Path path)`
     - `void setQuota(Path path, long namespaceQuota, long storagespaceQuota)`
     - `FSDataOutputStream append(Path f)`

2. **org.apache.hadoop.hdfs.DistributedFileSystem** (extends FileSystem)
   - HDFS 特定实现。
   - 额外方法：`concat(Path target, Path[] srcs)`、`setStoragePolicy(Path path, String policy)`、`modifyAclEntries` 等。

3. **org.apache.hadoop.fs.Path**
   - 路径表示类。`new Path("/user/hadoop/file.txt")` 或 `new Path("hdfs://namenode:8020/path")`

4. **org.apache.hadoop.fs.FsPermission**
   - 权限类。`FsPermission.createImmutable((short)0755)` 或 `new FsPermission("rwxr-xr-x")`

5. **org.apache.hadoop.fs.FileStatus**
   - 文件/目录元数据。包含 length, isDirectory, permission, owner, group, modificationTime, replication, blockSize 等。

6. **org.apache.hadoop.fs.FSDataInputStream** / **FSDataOutputStream**
   - 读写流。支持 seek, read, write 等。

### 配置与初始化
- `org.apache.hadoop.conf.Configuration`
  - 加载 `core-site.xml`, `hdfs-site.xml`
  - `conf.set("fs.defaultFS", "hdfs://namenode:8020")`
- 代理用户：`UserGroupInformation.doAs(...)` + `FileSystem.get(conf)`

### WebHDFS (REST 补充)
- `http://namenode:50070/webhdfs/v1/` 用于简单操作或直传。

**开发建议**：直接参考 Hadoop 官方源码 hadoop-hdfs-project/hadoop-hdfs-client/src/main/java/org/apache/hadoop/hdfs/DistributedFileSystem.java 和 FileSystem.java 。

## 技术架构

- **后端**：Spring Boot 3.x (Java 17+) + MyBatis-Plus + Spring Security + JWT + Hadoop Client 3.3.6
- **前端**：Vue 3 + TypeScript + Element Plus + ECharts + Vite
- **数据库**：MySQL (metadata, audit, templates, metrics)
- **Hadoop 集成**：hadoop-client (exclusions for Guava/Jackson conflicts)

## 项目结构推荐

```
data/
├── hermes-backend/          # Spring Boot 后端
│   ├── src/main/java/com/hermes/
│   │   ├── HermesApplication.java
│   │   ├── config/
│   │   │   ├── HadoopConfig.java
│   │   │   ├── SecurityConfig.java
│   │   ├── controller/hdfs/HdfsController.java
│   │   ├── service/hdfs/HdfsService.java
│   │   ├── entity/Cluster.java
│   │   ├── repository/ClusterRepository.java
│   │   └── ...
│   ├── pom.xml
│   └── src/main/resources/application.yml
├── hermes-frontend/         # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   ├── src/
│   │   ├── main.js
│   │   ├── App.vue
│   │   └── views/HdfsBrowser.vue
│   └── ...
├── docs/
│   ├── API_DESIGN.md
│   ├── ER_DIAGRAM.md
│   └── HDFS_INTERFACES.md
└── README.md
```

## 核心功能模块 (当前已实现骨架)

- [x] 多集群配置与动态 FileSystem 创建
- [x] HDFS 文件浏览器 (read-only: listStatus, getFileStatus)
- [x] REST API /api/v1/hdfs/**
- [ ] 用户登录 & RBAC (Spring Security + JWT)
- [ ] 操作审计日志
- [ ] YARN / MapReduce 模块
- [ ] 前端完整 UI

## 快速开始

### 1. 后端 (hermes-backend)

```bash
cd hermes-backend
mvn clean install
java -jar target/hermes-backend-0.0.1-SNAPSHOT.jar
```

需要配置 `application.yml` 中的 `hadoop.cluster.xxx` 和自己的 Hadoop 集群地址。

### 2. 前端

```bash
cd hermes-frontend
npm install
npm run dev
```

## 里程碑建议

1. 基础框架 + 用户登录 + 多集群配置 + HDFS 只读浏览器
2. HDFS 完整 CRUD + 审计日志
3. YARN 应用列表与提交
4. MapReduce 模板 + 历史作业
5. 监控图表 + 权限细化

## 风险与对策

- Hadoop 依赖冲突 → 严格 exclusions + 独立 classloader
- 大文件上传 → 流式处理 + 异步
- Kerberos → 先实现 Simple 模式

## 贡献

欢迎 PR 和 Issue！项目基于 Hadoop 3.3.6 客户端开发。

---

*Generated and initialized by Grok on 2026-06-15. Full implementation in progress following the detailed spec.*