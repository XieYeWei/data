package com.hermes.config;

import com.hermes.entity.Cluster;
import com.hermes.mapper.ClusterMapper;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class HadoopConfig {

    @Autowired(required = false)
    private ClusterMapper clusterMapper;

    @Autowired
    private HermesProperties props;

    private final Map<String, org.apache.hadoop.conf.Configuration> hadoopConfs = new HashMap<>();
    private final Map<String, FileSystem> fileSystems = new HashMap<>();
    private final Map<String, YarnClient> yarnClients = new HashMap<>();

    @PostConstruct
    public void init() {
        // 如果数据库中没有集群记录，自动创建一条默认集群
        if (clusterMapper != null && clusterMapper.selectCount(null) == 0) {
            Cluster demo = new Cluster();
            demo.setName(props.getCluster().getName());
            demo.setNamenode(props.getCluster().getNamenode());
            demo.setResourcemanager(props.getCluster().getResourcemanager());
            demo.setAuthType(props.getCluster().getAuthType());
            clusterMapper.insert(demo);
        }
    }

    public org.apache.hadoop.conf.Configuration getHadoopConf(String clusterId) {
        return hadoopConfs.computeIfAbsent(clusterId, id -> {
            Cluster cluster = getCluster(id);
            org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
            conf.set("fs.defaultFS", cluster.getNamenode());

            // IPC 连接超时配置
            conf.setInt("ipc.client.connect.max.retries", props.getIpc().getConnectMaxRetries());
            conf.setInt("ipc.client.connect.retry.interval", props.getIpc().getConnectRetryInterval());
            conf.setInt("ipc.client.connect.timeout", props.getIpc().getConnectTimeout());
            conf.setInt("dfs.client.socket-timeout", props.getIpc().getSocketTimeout());

            // HDFS HA 配置（nameservice 方式连接）
            HermesProperties.Hdfs hdfs = props.getHdfs();
            conf.set("dfs.nameservices", hdfs.getNameservices());
            conf.set("dfs.ha.namenodes." + hdfs.getNameservices(), hdfs.getHaNamenodes());
            conf.set("dfs.namenode.rpc-address." + hdfs.getNameservices() + ".nn1", hdfs.getNn1Rpc());
            conf.set("dfs.namenode.rpc-address." + hdfs.getNameservices() + ".nn2", hdfs.getNn2Rpc());
            conf.set("dfs.client.failover.proxy.provider." + hdfs.getNameservices(),
                "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");

            if ("kerberos".equalsIgnoreCase(cluster.getAuthType())) {
                conf.set("hadoop.security.authentication", "kerberos");
            }
            return conf;
        });
    }

    public FileSystem getFileSystem(String clusterId) throws IOException {
        return fileSystems.computeIfAbsent(clusterId, id -> {
            try {
                Cluster cluster = getCluster(id);
                org.apache.hadoop.conf.Configuration conf = getHadoopConf(id);
                // 以 hadoop 用户身份访问 HDFS（Docker 容器内 HDFS 默认用户）
                UserGroupInformation ugi = UserGroupInformation.createRemoteUser("hadoop");
                return ugi.doAs((PrivilegedExceptionAction<FileSystem>) () ->
                    FileSystem.get(URI.create(cluster.getNamenode()), conf)
                );
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public YarnClient getYarnClient(String clusterId) {
        return yarnClients.computeIfAbsent(clusterId, id -> {
            Cluster cluster = getCluster(id);
            YarnConfiguration yarnConf = new YarnConfiguration(getHadoopConf(id));
            yarnConf.set(YarnConfiguration.RM_ADDRESS, cluster.getResourcemanager());

            // YARN 客户端连接超时配置
            HermesProperties.Yarn yarnProps = props.getYarn();
            yarnConf.setLong("yarn.resourcemanager.connect.max-wait.waitms", yarnProps.getConnectMaxWaitMs());
            yarnConf.setLong("yarn.resourcemanager.connect.retry-interval.ms", yarnProps.getConnectRetryIntervalMs());
            yarnConf.setInt("ipc.client.connect.max.retries", props.getIpc().getConnectMaxRetries());
            yarnConf.setInt("ipc.client.connect.retry.interval", props.getIpc().getConnectRetryInterval());
            yarnConf.setInt("ipc.client.connect.timeout", props.getIpc().getConnectTimeout());

            YarnClient client = YarnClient.createYarnClient();
            client.init(yarnConf);
            client.start();
            return client;
        });
    }

    private Cluster getCluster(String clusterId) {
        if (clusterMapper == null) {
            Cluster demo = new Cluster();
            demo.setName(props.getCluster().getName());
            demo.setNamenode(props.getCluster().getNamenode());
            demo.setResourcemanager(props.getCluster().getResourcemanager());
            demo.setAuthType(props.getCluster().getAuthType());
            return demo;
        }
        Cluster cluster = clusterMapper.selectById(clusterId.replace("cluster", ""));
        if (cluster == null) {
            throw new RuntimeException("Cluster not found: " + clusterId);
        }
        return cluster;
    }

    @Bean
    public Map<String, FileSystem> fileSystemMap() {
        return fileSystems;
    }
}
