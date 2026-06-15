package com.hermes.config;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hadoop configuration and FileSystem factory for multi-cluster support.
 * Loads from application.yml or properties. Supports dynamic creation per cluster.
 * Uses ThreadLocal or cache for efficiency. For production, integrate with UserGroupInformation.doAs for proxy users.
 */
@org.springframework.context.annotation.Configuration
public class HadoopConfig {

    private static final Logger log = LoggerFactory.getLogger(HadoopConfig.class);

    @Value("${hadoop.default-cluster-id:cluster1}")
    private String defaultClusterId;

    // Example: support multiple clusters via properties or DB in future
    // For demo: hardcode or load from yml map if needed. Here we use a simple default.
    private final Map<String, FileSystem> fsCache = new ConcurrentHashMap<>();

    /**
     * Get or create FileSystem for a specific cluster.
     * In production: load cluster info (NN address, auth type, keytab) from DB (Cluster entity).
     */
    public FileSystem getFileSystem(String clusterId) throws IOException {
        return fsCache.computeIfAbsent(clusterId, id -> {
            try {
                Configuration conf = new Configuration();
                // TODO: In real impl, load per-cluster: fs.defaultFS, dfs.replication, auth (kerberos/simple)
                // Example for cluster1 (replace with your NN address)
                if ("cluster1".equals(id) || defaultClusterId.equals(id)) {
                    conf.set("fs.defaultFS", "hdfs://your-namenode-host:8020");  // <-- CHANGE TO YOUR HDFS NN
                    conf.set("dfs.replication", "3");
                    // For Kerberos: conf.set("hadoop.security.authentication", "kerberos");
                    // UserGroupInformation.setConfiguration(conf);
                    // Login with keytab if needed
                } else {
                    // Add more clusters dynamically from DB later
                    conf.set("fs.defaultFS", "hdfs://your-namenode-host:8020");
                }
                // Use FileSystem.get which returns DistributedFileSystem for hdfs://
                FileSystem fs = FileSystem.get(URI.create(conf.get("fs.defaultFS")), conf);
                log.info("Initialized FileSystem for cluster: {} , defaultFS={}", id, conf.get("fs.defaultFS"));
                return fs;
            } catch (IOException e) {
                log.error("Failed to create FileSystem for cluster {}", id, e);
                throw new RuntimeException(e);
            }
        });
    }

    @Bean
    public FileSystem defaultFileSystem() throws IOException {
        return getFileSystem(defaultClusterId);
    }

    /**
     * Close all cached FileSystems on shutdown (optional @PreDestroy)
     */
    public void closeAll() {
        fsCache.values().forEach(fs -> {
            try {
                fs.close();
            } catch (IOException e) {
                log.warn("Error closing FileSystem", e);
            }
        });
        fsCache.clear();
    }
}