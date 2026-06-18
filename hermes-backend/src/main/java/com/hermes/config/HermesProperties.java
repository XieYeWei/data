package com.hermes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hermes 平台集群配置，从 application.yml 读取
 */
@Component
@ConfigurationProperties(prefix = "hermes")
public class HermesProperties {

    /** Hadoop 集群连接配置 */
    private Cluster cluster = new Cluster();

    /** HDFS 高可用相关 */
    private Hdfs hdfs = new Hdfs();

    /** JournalNode 配置 */
    private JournalNode journalNode = new JournalNode();

    /** YARN 资源管理 */
    private Yarn yarn = new Yarn();

    /** IPC 连接超时 */
    private Ipc ipc = new Ipc();

    public static class Cluster {
        /** 默认集群 ID（对应数据库 cluster 表的 id） */
        private String defaultId = "cluster1";
        /** 默认集群名称 */
        private String name = "demo-cluster";
        /** HDFS NameNode RPC 地址，格式 hdfs://host:port（如 hdfs://172.21.0.8:8020） */
        private String namenode = "hdfs://localhost:9000";
        /** YARN ResourceManager RPC 地址，格式 host:port（如 172.21.0.2:8032） */
        private String resourcemanager = "localhost:8032";
        /** 认证方式：simple / kerberos */
        private String authType = "simple";

        public String getDefaultId() { return defaultId; }
        public void setDefaultId(String defaultId) { this.defaultId = defaultId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getNamenode() { return namenode; }
        public void setNamenode(String namenode) { this.namenode = namenode; }
        public String getResourcemanager() { return resourcemanager; }
        public void setResourcemanager(String resourcemanager) { this.resourcemanager = resourcemanager; }
        public String getAuthType() { return authType; }
        public void setAuthType(String authType) { this.authType = authType; }
    }

    public static class Hdfs {
        /** NameNode HA nameservice 名称（如 mycluster） */
        private String nameservices = "mycluster";
        /** HA 模式下两个 NameNode 的 ID，逗号分隔（如 nn1,nn2） */
        private String haNamenodes = "nn1,nn2";
        /** NameNode1 的 RPC 地址，格式 host:port（如 172.21.0.8:8020） */
        private String nn1Rpc = "172.21.0.8:8020";
        /** NameNode2 的 RPC 地址，格式 host:port（如 172.21.0.7:8020） */
        private String nn2Rpc = "172.21.0.7:8020";
        /** NameNode1 的 Web UI 地址，格式 ip:port（如 172.21.0.8:9870） */
        private String nn1Web = "172.21.0.8:9870";
        /** NameNode2 的 Web UI 地址，格式 ip:port（如 172.21.0.7:9870） */
        private String nn2Web = "172.21.0.7:9870";
        /** WebHDFS 端口 */
        private int webPort = 9870;

        public String getNameservices() { return nameservices; }
        public void setNameservices(String nameservices) { this.nameservices = nameservices; }
        public String getHaNamenodes() { return haNamenodes; }
        public void setHaNamenodes(String haNamenodes) { this.haNamenodes = haNamenodes; }
        public String getNn1Rpc() { return nn1Rpc; }
        public void setNn1Rpc(String nn1Rpc) { this.nn1Rpc = nn1Rpc; }
        public String getNn2Rpc() { return nn2Rpc; }
        public void setNn2Rpc(String nn2Rpc) { this.nn2Rpc = nn2Rpc; }
        public String getNn1Web() { return nn1Web; }
        public void setNn1Web(String nn1Web) { this.nn1Web = nn1Web; }
        public String getNn2Web() { return nn2Web; }
        public void setNn2Web(String nn2Web) { this.nn2Web = nn2Web; }
        public int getWebPort() { return webPort; }
        public void setWebPort(int webPort) { this.webPort = webPort; }
    }

    public static class JournalNode {
        /** JournalNode 主机列表，逗号分隔（如 172.21.0.6,172.21.0.5,172.21.0.3） */
        private List<String> hosts = new ArrayList<>(List.of("172.21.0.6", "172.21.0.5", "172.21.0.3"));
        /** JournalNode Web UI 端口（默认 8480） */
        private int webPort = 8480;

        public List<String> getHosts() { return hosts; }
        public void setHosts(List<String> hosts) { this.hosts = hosts; }
        public int getWebPort() { return webPort; }
        public void setWebPort(int webPort) { this.webPort = webPort; }
    }

    public static class Yarn {
        /** YARN ResourceManager 地址，格式 host:port（如 localhost:8032） */
        private String rmAddress = "localhost:8032";
        /** RM 连接最大等待时间（毫秒） */
        private long connectMaxWaitMs = 2000;
        /** RM 连接重试间隔（毫秒） */
        private long connectRetryIntervalMs = 500;

        public String getRmAddress() { return rmAddress; }
        public void setRmAddress(String rmAddress) { this.rmAddress = rmAddress; }
        public long getConnectMaxWaitMs() { return connectMaxWaitMs; }
        public void setConnectMaxWaitMs(long connectMaxWaitMs) { this.connectMaxWaitMs = connectMaxWaitMs; }
        public long getConnectRetryIntervalMs() { return connectRetryIntervalMs; }
        public void setConnectRetryIntervalMs(long connectRetryIntervalMs) { this.connectRetryIntervalMs = connectRetryIntervalMs; }
    }

    public static class Ipc {
        /** Hadoop IPC 连接最大重试次数 */
        private int connectMaxRetries = 1;
        /** Hadoop IPC 连接重试间隔（毫秒） */
        private int connectRetryInterval = 500;
        /** Hadoop IPC 连接超时（毫秒） */
        private int connectTimeout = 2000;
        /** HDFS 客户端 socket 超时（毫秒） */
        private int socketTimeout = 3000;

        public int getConnectMaxRetries() { return connectMaxRetries; }
        public void setConnectMaxRetries(int connectMaxRetries) { this.connectMaxRetries = connectMaxRetries; }
        public int getConnectRetryInterval() { return connectRetryInterval; }
        public void setConnectRetryInterval(int connectRetryInterval) { this.connectRetryInterval = connectRetryInterval; }
        public int getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
        public int getSocketTimeout() { return socketTimeout; }
        public void setSocketTimeout(int socketTimeout) { this.socketTimeout = socketTimeout; }
    }

    public Cluster getCluster() { return cluster; }
    public void setCluster(Cluster cluster) { this.cluster = cluster; }
    public Hdfs getHdfs() { return hdfs; }
    public void setHdfs(Hdfs hdfs) { this.hdfs = hdfs; }
    public JournalNode getJournalNode() { return journalNode; }
    public void setJournalNode(JournalNode journalNode) { this.journalNode = journalNode; }
    public Yarn getYarn() { return yarn; }
    public void setYarn(Yarn yarn) { this.yarn = yarn; }
    /** Trash / Recycle Bin configuration */
    private Trash trash = new Trash();

    public static class Trash {
        /** Number of days before trash items are auto-cleaned (default 30) */
        private int retentionDays = 30;

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    public Trash getTrash() { return trash; }
    public void setTrash(Trash trash) { this.trash = trash; }

    /** Grafana dashboard configuration */
    private Grafana grafana = new Grafana();

    public static class Grafana {
        /** Grafana dashboard URL for embedding */
        private String url = "";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public Grafana getGrafana() { return grafana; }
    public void setGrafana(Grafana grafana) { this.grafana = grafana; }

    public Ipc getIpc() { return ipc; }
    public void setIpc(Ipc ipc) { this.ipc = ipc; }
}
