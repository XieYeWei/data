package com.hermes.controller;

import com.hermes.entity.Cluster;
import com.hermes.mapper.ClusterMapper;
import com.hermes.util.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/clusters")
public class ClusterController {

    @Autowired
    private ClusterMapper clusterMapper;

    @GetMapping
    public R listAll() {
        List<Cluster> clusters = clusterMapper.selectList(null);
        return R.success(clusters);
    }

    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        Cluster cluster = clusterMapper.selectById(id);
        if (cluster == null) {
            return R.error(404, "Cluster not found: " + id);
        }
        return R.success(cluster);
    }

    @PostMapping
    public R create(@RequestBody Cluster cluster) {
        clusterMapper.insert(cluster);
        log.info("Created cluster: id={}, name={}", cluster.getId(), cluster.getName());
        return R.success(cluster);
    }

    @PutMapping("/{id}")
    public R update(@PathVariable Long id, @RequestBody Cluster cluster) {
        Cluster existing = clusterMapper.selectById(id);
        if (existing == null) {
            return R.error(404, "Cluster not found: " + id);
        }
        cluster.setId(id);
        clusterMapper.updateById(cluster);
        log.info("Updated cluster: id={}", id);
        return R.success(clusterMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public R delete(@PathVariable Long id) {
        Cluster existing = clusterMapper.selectById(id);
        if (existing == null) {
            return R.error(404, "Cluster not found: " + id);
        }
        clusterMapper.deleteById(id);
        log.info("Deleted cluster: id={}", id);
        return R.success();
    }
}
