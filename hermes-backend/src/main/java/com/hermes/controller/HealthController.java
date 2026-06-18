package com.hermes.controller;

import com.hermes.config.HermesProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    @Autowired
    private HermesProperties props;

    @GetMapping("/health")
    public Map<String, Object> health() {
        List<Map<String, Object>> components = new ArrayList<>();
        int degradedCount = 0;
        int downCount = 0;

        // 1. HDFS HA — 通过 NN Web JMX 获取两个 NameNode 的状态
        Map<String, Object> hdfsHa = checkHdfsHa();
        components.add(hdfsHa);
        if ("degraded".equals(hdfsHa.get("status"))) degradedCount++;
        else if ("down".equals(hdfsHa.get("status"))) downCount++;

        // 2. YARN RM
        Map<String, Object> yarnRm = checkYarnRm();
        components.add(yarnRm);
        if ("degraded".equals(yarnRm.get("status"))) degradedCount++;
        else if ("down".equals(yarnRm.get("status"))) downCount++;

        // 3. NameNode nn1
        Map<String, Object> nn1 = checkNameNode("nn1", props.getHdfs().getNn1Web());
        components.add(nn1);
        if ("degraded".equals(nn1.get("status"))) degradedCount++;
        else if ("down".equals(nn1.get("status"))) downCount++;

        // 4. NameNode nn2
        Map<String, Object> nn2 = checkNameNode("nn2", props.getHdfs().getNn2Web());
        components.add(nn2);
        if ("degraded".equals(nn2.get("status"))) degradedCount++;
        else if ("down".equals(nn2.get("status"))) downCount++;

        // 5. DataNodes — 通过 NN JMX 查询
        Map<String, Object> dataNodes = checkDataNodes();
        components.add(dataNodes);
        if ("degraded".equals(dataNodes.get("status"))) degradedCount++;
        else if ("down".equals(dataNodes.get("status"))) downCount++;

        // 6. JournalNodes
        Map<String, Object> journalNodes = checkJournalNodes();
        components.add(journalNodes);
        if ("degraded".equals(journalNodes.get("status"))) degradedCount++;
        else if ("down".equals(journalNodes.get("status"))) downCount++;

        // 7. ZooKeeper (optional)
        Map<String, Object> zk = checkZooKeeper();
        components.add(zk);

        String overall;
        if (downCount > 0) {
            overall = "down";
        } else if (degradedCount > 0) {
            overall = "degraded";
        } else {
            overall = "healthy";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("components", components);
        result.put("overall", overall);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("msg", "success");
        response.put("data", result);
        return response;
    }

    /**
     * 通过 HTTP JMX 获取 NameNode 状态（纯服务级探测，不依赖 docker）
     */
    private String getNnState(String webAddress) {
        try {
            String url = "http://" + webAddress + "/jmx?qry=Hadoop:service=NameNode,name=NameNodeStatus";
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // 查找 "State" : "active" 或 "State" : "standby"
                if (line.contains("\"State\"")) {
                    if (line.contains("\"active\"")) return "active";
                    if (line.contains("\"standby\"")) return "standby";
                }
            }
        } catch (Exception e) {
            // 不可达
        }
        return null;
    }

    /**
     * HDFS HA 检查：两个 NN 各自通过 JMX 探测状态
     */
    private Map<String, Object> checkHdfsHa() {
        Map<String, Object> comp = new HashMap<>();
        comp.put("name", "HDFS HA");
        try {
            String nn1Web = props.getHdfs().getNn1Web();
            String nn2Web = props.getHdfs().getNn2Web();

            String nn1State = getNnState(nn1Web);
            String nn2State = getNnState(nn2Web);

            Map<String, Object> details = new HashMap<>();
            details.put("nn1", nn1State != null ? nn1State : "unreachable");
            details.put("nn2", nn2State != null ? nn2State : "unreachable");

            if (nn1State == null && nn2State == null) {
                comp.put("status", "down");
                details.put("message", "两个 NameNode 均不可达");
            } else if ("active".equals(nn1State) || "active".equals(nn2State)) {
                comp.put("status", "healthy");
                details.put("message", "HA active/standby 工作正常");
            } else if (nn1State == null || nn2State == null) {
                comp.put("status", "degraded");
                details.put("message", "一个 NameNode 不可达");
            } else {
                comp.put("status", "degraded");
                details.put("message", "两个 NN 均为 standby，无 active 节点");
            }
            comp.put("details", details);
        } catch (Exception e) {
            comp.put("status", "down");
            Map<String, Object> details = new HashMap<>();
            details.put("message", "检查失败: " + e.getMessage());
            comp.put("details", details);
        }
        return comp;
    }

    /**
     * 单个 NameNode 检查
     */
    private Map<String, Object> checkNameNode(String name, String webAddress) {
        Map<String, Object> comp = new HashMap<>();
        comp.put("name", "NameNode " + name);
        try {
            String state = getNnState(webAddress);
            if (state != null) {
                comp.put("status", "healthy");
                Map<String, Object> details = new HashMap<>();
                details.put("state", state);
                details.put("host", webAddress.split(":")[0]);
                details.put("webAddress", webAddress);
                comp.put("details", details);
            } else {
                comp.put("status", "down");
                Map<String, Object> details = new HashMap<>();
                details.put("message", webAddress + " JMX 不可达");
                comp.put("details", details);
            }
        } catch (Exception e) {
            comp.put("status", "down");
            Map<String, Object> details = new HashMap<>();
            details.put("message", e.getMessage());
            comp.put("details", details);
        }
        return comp;
    }

    /**
     * YARN ResourceManager 检查
     */
    private Map<String, Object> checkYarnRm() {
        Map<String, Object> comp = new HashMap<>();
        comp.put("name", "YARN ResourceManager");
        try {
            String host = props.getCluster().getResourcemanager().split(":")[0];
            String url = "http://" + host + ":8088/ws/v1/cluster/info";

            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();

            if (code == 200) {
                comp.put("status", "healthy");
                Map<String, Object> details = new HashMap<>();
                details.put("host", host);
                details.put("httpStatus", code);
                comp.put("details", details);
            } else {
                comp.put("status", "degraded");
                Map<String, Object> details = new HashMap<>();
                details.put("host", host);
                details.put("httpStatus", code);
                details.put("message", "RM 返回状态码 " + code);
                comp.put("details", details);
            }
        } catch (Exception e) {
            comp.put("status", "down");
            Map<String, Object> details = new HashMap<>();
            details.put("message", "RM 不可达: " + e.getMessage());
            comp.put("details", details);
        }
        return comp;
    }

    /**
     * DataNodes 检查 — 通过 NameNode JMX 查询存活 DataNode 数量
     */
    private Map<String, Object> checkDataNodes() {
        Map<String, Object> comp = new HashMap<>();
        comp.put("name", "DataNodes");
        try {
            String nnWeb = props.getHdfs().getNn1Web();
            String url = "http://" + nnWeb + "/jmx?qry=Hadoop:service=NameNode,name=FSNamesystem";

            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();

            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                Integer liveDns = null;
                Integer deadDns = null;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("\"NumLiveDataNodes\"")) {
                        String val = line.replaceAll(".*?([0-9]+).*", "$1");
                        liveDns = Integer.parseInt(val);
                    }
                    if (line.contains("\"NumDeadDataNodes\"")) {
                        String val = line.replaceAll(".*?([0-9]+).*", "$1");
                        deadDns = Integer.parseInt(val);
                    }
                }

                Map<String, Object> details = new HashMap<>();
                if (liveDns != null && liveDns > 0) {
                    comp.put("status", "healthy");
                    details.put("liveNodes", liveDns);
                    details.put("deadNodes", deadDns != null ? deadDns : 0);
                    details.put("message", liveDns + " 个 DataNode 在线");
                } else {
                    comp.put("status", "degraded");
                    details.put("liveNodes", liveDns != null ? liveDns : 0);
                    details.put("message", "无存活 DataNode");
                }
                comp.put("details", details);
            } else {
                comp.put("status", "degraded");
                Map<String, Object> details = new HashMap<>();
                details.put("message", "无法通过 NN JMX 查询 DataNode 状态");
                comp.put("details", details);
            }
        } catch (Exception e) {
            comp.put("status", "down");
            Map<String, Object> details = new HashMap<>();
            details.put("message", "DataNode 检查失败: " + e.getMessage());
            comp.put("details", details);
        }
        return comp;
    }

    /**
     * JournalNodes 检查
     */
    private Map<String, Object> checkJournalNodes() {
        Map<String, Object> comp = new HashMap<>();
        comp.put("name", "JournalNodes");
        try {
            List<String> jnHosts = props.getJournalNode().getHosts();
            int reachable = 0;
            List<Map<String, String>> nodeDetails = new ArrayList<>();
            for (String host : jnHosts) {
                Map<String, String> nd = new HashMap<>();
                nd.put("host", host);
                try {
                    String url = "http://" + host + ":8480/jmx";
                    HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    if (conn.getResponseCode() == 200) {
                        reachable++;
                        nd.put("status", "reachable");
                    } else {
                        nd.put("status", "unreachable");
                    }
                } catch (Exception e) {
                    nd.put("status", "unreachable");
                }
                nodeDetails.add(nd);
            }

            if (reachable == jnHosts.size()) {
                comp.put("status", "healthy");
            } else if (reachable > 0) {
                comp.put("status", "degraded");
            } else {
                comp.put("status", "down");
            }
            Map<String, Object> details = new HashMap<>();
            details.put("nodes", nodeDetails);
            details.put("total", jnHosts.size());
            details.put("reachable", reachable);
            comp.put("details", details);
        } catch (Exception e) {
            comp.put("status", "down");
            Map<String, Object> details = new HashMap<>();
            details.put("message", e.getMessage());
            comp.put("details", details);
        }
        return comp;
    }

    /**
     * ZooKeeper 检查（可选）
     */
    private Map<String, Object> checkZooKeeper() {
        Map<String, Object> comp = new HashMap<>();
        comp.put("name", "ZooKeeper");
        comp.put("status", "healthy");
        Map<String, Object> details = new HashMap<>();
        details.put("message", "ZK 检查跳过（可选）");
        comp.put("details", details);
        return comp;
    }
}
