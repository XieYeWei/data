package com.hermes.config;

import com.hermes.entity.Cluster;
import com.hermes.mapper.ClusterMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class HadoopConfig {

    private static final Logger log = LoggerFactory.getLogger(HadoopConfig.class);

    @Value("${hadoop.default-cluster-id:cluster1}")
    private String defaultClusterId;

    @Autowired(required = false)
    private ClusterMapper clusterMapper;

    private final Map<String, FileSystem> fsCache = new ConcurrentHashMap<>();
    private final Map<String, YarnClient> yarnCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("HadoopConfig initialized with dynamic cluster loading support");
    }

    /**
     * Get or load Cluster entity from DB (or fallback to properties)
     */
    private Cluster getClusterEntity(String clusterId) {
        if (clusterMapper != null) {
            Cluster cluster = clusterMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Cluster>()
                    .eq("name", clusterId).eq("enabled", true)
            );
            if (cluster != null) return cluster;
        }
        // Fallback to hardcoded demo
        Cluster demo = new Cluster();
        demo.setName(clusterId);
        demo.setNamenode("hdfs://your-namenode:8020");
        demo.setResourcemanager("your-rm-host:8032");
        demo.setAuthType("simple");
        return demo;
    }

    public FileSystem getFileSystem(String clusterId) throws IOException {
        return fsCache.computeIfAbsent(clusterId, id -> {
            try {
                Cluster cluster = getClusterEntity(id);
                Configuration conf = buildConf(cluster);
                FileSystem fs = FileSystem.get(URI.create(conf.get("fs.defaultFS")), conf);
                log.info("Created FileSystem for cluster: {} (auth={})", id, cluster.getAuthType());
                return fs;
            } catch (Exception e) {
                log.error("Failed to create FileSystem for {}", id, e);
                throw new RuntimeException(e);
            }
        });
    }

    public YarnClient getYarnClient(String clusterId) throws IOException {
        return yarnCache.computeIfAbsent(clusterId, id -> {
            try {
                Cluster cluster = getClusterEntity(id);
                Configuration conf = buildConf(cluster);
                YarnConfiguration yarnConf = new YarnConfiguration(conf);
                YarnClient client = YarnClient.createYarnClient();
                client.init(yarnConf);
                client.start();
                log.info("Created YarnClient for cluster: {} (auth={})", id, cluster.getAuthType());
                return client;
            } catch (Exception e) {
                log.error("Failed to create YarnClient for {}", id, e);
                throw new RuntimeException(e);
            }
        });
    }

    private Configuration buildConf(Cluster cluster) throws IOException {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", cluster.getNamenode());
        conf.set("yarn.resourcemanager.address", cluster.getResourcemanager());
        conf.set("hadoop.security.authentication", cluster.getAuthType());

        if ("kerberos".equalsIgnoreCase(cluster.getAuthType())) {
            UserGroupInformation.setConfiguration(conf);
            if (cluster.getKeytabPath() != null && cluster.getPrincipal() != null) {
                try {
                    UserGroupInformation.loginUserFromKeytab(cluster.getPrincipal(), cluster.getKeytabPath());
                    log.info("Kerberos login successful for principal: {}", cluster.getPrincipal());
                } catch (IOException e) {
                    log.error("Kerberos login failed", e);
                    throw e;
                }
            }
        }
        return conf;
    }

    public void closeAll() {
        fsCache.values().forEach(fs -> { try { fs.close(); } catch (Exception ignored) {} });
        yarnCache.values().forEach(yc -> { try { yc.close(); } catch (Exception ignored) {} });
        fsCache.clear();
        yarnCache.clear();
    }
}