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
    private ClusterMapper clusterMapper;  // For future DB-driven clusters

    private final Map<String, FileSystem> fsCache = new ConcurrentHashMap<>();
    private final Map<String, YarnClient> yarnCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("HadoopConfig initialized. Default cluster: {}", defaultClusterId);
    }

    /**
     * Get FileSystem for cluster (supports Kerberos via keytab if configured)
     */
    public FileSystem getFileSystem(String clusterId) throws IOException {
        return fsCache.computeIfAbsent(clusterId, id -> {
            try {
                Configuration conf = buildConfForCluster(id);
                FileSystem fs = FileSystem.get(URI.create(conf.get("fs.defaultFS")), conf);
                log.info("Created FileSystem for cluster {}", id);
                return fs;
            } catch (Exception e) {
                log.error("Failed to init FileSystem for {}", id, e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Get YarnClient for cluster (with Kerberos support)
     */
    public YarnClient getYarnClient(String clusterId) throws IOException {
        return yarnCache.computeIfAbsent(clusterId, id -> {
            try {
                Configuration conf = buildConfForCluster(id);
                YarnConfiguration yarnConf = new YarnConfiguration(conf);
                YarnClient client = YarnClient.createYarnClient();
                client.init(yarnConf);
                client.start();
                log.info("Created YarnClient for cluster {}", id);
                return client;
            } catch (Exception e) {
                log.error("Failed to init YarnClient for {}", id, e);
                throw new RuntimeException(e);
            }
        });
    }

    private Configuration buildConfForCluster(String clusterId) throws IOException {
        Configuration conf = new Configuration();
        // TODO: In production, load from ClusterMapper.findByName(clusterId)
        // For demo, use yml or hardcoded
        String nn = "hdfs://your-namenode:8020"; // from yml or DB
        String rm = "your-rm-host:8032";
        String auth = "simple";

        conf.set("fs.defaultFS", nn);
        conf.set("yarn.resourcemanager.address", rm);
        conf.set("hadoop.security.authentication", auth);

        if ("kerberos".equalsIgnoreCase(auth)) {
            // Example Kerberos setup (uncomment and configure keytab/principal in Cluster entity)
            // UserGroupInformation.setConfiguration(conf);
            // UserGroupInformation.loginUserFromKeytab("hdfs@REALM", "/path/to/hdfs.keytab");
            log.info("Kerberos mode enabled for cluster {}", clusterId);
        }

        UserGroupInformation.setConfiguration(conf);
        return conf;
    }

    public void closeAll() {
        fsCache.values().forEach(fs -> { try { fs.close(); } catch (Exception ignored) {} });
        yarnCache.values().forEach(yc -> { try { yc.close(); } catch (Exception ignored) {} });
        fsCache.clear();
        yarnCache.clear();
    }
}