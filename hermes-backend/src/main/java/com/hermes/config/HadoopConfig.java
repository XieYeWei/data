package com.hermes.config;

import com.hermes.entity.Cluster;
import com.hermes.mapper.ClusterMapper;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class HadoopConfig {

    @Autowired(required = false)
    private ClusterMapper clusterMapper;

    private final Map<String, org.apache.hadoop.conf.Configuration> hadoopConfs = new HashMap<>();
    private final Map<String, FileSystem> fileSystems = new HashMap<>();
    private final Map<String, YarnClient> yarnClients = new HashMap<>();

    @PostConstruct
    public void init() {
        // Initialize demo cluster if none exists
        if (clusterMapper != null && clusterMapper.selectCount(null) == 0) {
            Cluster demo = new Cluster();
            demo.setName("demo-cluster");
            demo.setNamenode("hdfs://localhost:9000");
            demo.setResourcemanager("localhost:8032");
            demo.setAuthType("simple");
            clusterMapper.insert(demo);
        }
    }

    public org.apache.hadoop.conf.Configuration getHadoopConf(String clusterId) {
        return hadoopConfs.computeIfAbsent(clusterId, id -> {
            Cluster cluster = getCluster(id);
            org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
            conf.set("fs.defaultFS", cluster.getNamenode());

            if ("kerberos".equalsIgnoreCase(cluster.getAuthType())) {
                conf.set("hadoop.security.authentication", "kerberos");
                // Add keytab and principal logic here if needed
            }
            return conf;
        });
    }

    public FileSystem getFileSystem(String clusterId) throws IOException {
        return fileSystems.computeIfAbsent(clusterId, id -> {
            try {
                Cluster cluster = getCluster(id);
                org.apache.hadoop.conf.Configuration conf = getHadoopConf(id);
                return FileSystem.get(URI.create(cluster.getNamenode()), conf);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public YarnClient getYarnClient(String clusterId) {
        return yarnClients.computeIfAbsent(clusterId, id -> {
            Cluster cluster = getCluster(id);
            YarnConfiguration yarnConf = new YarnConfiguration(getHadoopConf(id));
            yarnConf.set(YarnConfiguration.RM_ADDRESS, cluster.getResourcemanager());
            YarnClient client = YarnClient.createYarnClient();
            client.init(yarnConf);
            client.start();
            return client;
        });
    }

    private Cluster getCluster(String clusterId) {
        if (clusterMapper == null) {
            Cluster demo = new Cluster();
            demo.setName("demo");
            demo.setNamenode("hdfs://localhost:9000");
            demo.setResourcemanager("localhost:8032");
            demo.setAuthType("simple");
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