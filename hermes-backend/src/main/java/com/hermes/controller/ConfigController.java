package com.hermes.controller;

import com.hermes.config.HermesProperties;
import com.hermes.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/v1/system")
public class ConfigController {

    @Autowired
    private HermesProperties hermesProperties;

    @GetMapping("/config")
    public R getConfig() {
        List<Map<String, Object>> items = new ArrayList<>();

        // Cluster
        HermesProperties.Cluster cluster = hermesProperties.getCluster();
        Map<String, Object> clusterGroup = new LinkedHashMap<>();
        clusterGroup.put("group", "Cluster");
        clusterGroup.put("items", List.of(
            Map.of("key", "hermes.cluster.defaultId", "label", "默认集群 ID", "value", cluster.getDefaultId()),
            Map.of("key", "hermes.cluster.name", "label", "集群名称", "value", cluster.getName()),
            Map.of("key", "hermes.cluster.namenode", "label", "NameNode RPC", "value", cluster.getNamenode()),
            Map.of("key", "hermes.cluster.resourcemanager", "label", "ResourceManager RPC", "value", cluster.getResourcemanager()),
            Map.of("key", "hermes.cluster.authType", "label", "认证方式", "value", cluster.getAuthType())
        ));
        items.add(clusterGroup);

        // HDFS
        HermesProperties.Hdfs hdfs = hermesProperties.getHdfs();
        Map<String, Object> hdfsGroup = new LinkedHashMap<>();
        hdfsGroup.put("group", "HDFS");
        hdfsGroup.put("items", List.of(
            Map.of("key", "hermes.hdfs.nameservices", "label", "Nameservice", "value", hdfs.getNameservices()),
            Map.of("key", "hermes.hdfs.haNamenodes", "label", "HA NameNodes", "value", hdfs.getHaNamenodes()),
            Map.of("key", "hermes.hdfs.nn1Rpc", "label", "NN1 RPC 地址", "value", hdfs.getNn1Rpc()),
            Map.of("key", "hermes.hdfs.nn2Rpc", "label", "NN2 RPC 地址", "value", hdfs.getNn2Rpc()),
            Map.of("key", "hermes.hdfs.nn1Web", "label", "NN1 Web UI", "value", hdfs.getNn1Web()),
            Map.of("key", "hermes.hdfs.nn2Web", "label", "NN2 Web UI", "value", hdfs.getNn2Web()),
            Map.of("key", "hermes.hdfs.webPort", "label", "WebHDFS 端口", "value", String.valueOf(hdfs.getWebPort()))
        ));
        items.add(hdfsGroup);

        // YARN
        HermesProperties.Yarn yarn = hermesProperties.getYarn();
        Map<String, Object> yarnGroup = new LinkedHashMap<>();
        yarnGroup.put("group", "YARN");
        yarnGroup.put("items", List.of(
            Map.of("key", "hermes.yarn.rmAddress", "label", "RM 地址", "value", yarn.getRmAddress()),
            Map.of("key", "hermes.yarn.connectMaxWaitMs", "label", "连接最大等待 (ms)", "value", String.valueOf(yarn.getConnectMaxWaitMs())),
            Map.of("key", "hermes.yarn.connectRetryIntervalMs", "label", "连接重试间隔 (ms)", "value", String.valueOf(yarn.getConnectRetryIntervalMs()))
        ));
        items.add(yarnGroup);

        // JournalNode
        HermesProperties.JournalNode jn = hermesProperties.getJournalNode();
        Map<String, Object> jnGroup = new LinkedHashMap<>();
        jnGroup.put("group", "JournalNode");
        jnGroup.put("items", List.of(
            Map.of("key", "hermes.journalNode.hosts", "label", "JN 主机列表", "value", String.join(", ", jn.getHosts())),
            Map.of("key", "hermes.journalNode.webPort", "label", "JN Web 端口", "value", String.valueOf(jn.getWebPort()))
        ));
        items.add(jnGroup);

        // IPC
        HermesProperties.Ipc ipc = hermesProperties.getIpc();
        Map<String, Object> ipcGroup = new LinkedHashMap<>();
        ipcGroup.put("group", "IPC");
        ipcGroup.put("items", List.of(
            Map.of("key", "hermes.ipc.connectMaxRetries", "label", "最大重试次数", "value", String.valueOf(ipc.getConnectMaxRetries())),
            Map.of("key", "hermes.ipc.connectRetryInterval", "label", "重试间隔 (ms)", "value", String.valueOf(ipc.getConnectRetryInterval())),
            Map.of("key", "hermes.ipc.connectTimeout", "label", "连接超时 (ms)", "value", String.valueOf(ipc.getConnectTimeout())),
            Map.of("key", "hermes.ipc.socketTimeout", "label", "Socket 超时 (ms)", "value", String.valueOf(ipc.getSocketTimeout()))
        ));
        items.add(ipcGroup);

        // Grafana
        HermesProperties.Grafana grafana = hermesProperties.getGrafana();
        Map<String, Object> grafanaGroup = new LinkedHashMap<>();
        grafanaGroup.put("group", "Grafana");
        grafanaGroup.put("items", List.of(
            Map.of("key", "hermes.grafana.url", "label", "Grafana URL", "value", grafana.getUrl() != null ? grafana.getUrl() : "")
        ));
        items.add(grafanaGroup);

        return R.success(items);
    }
}
