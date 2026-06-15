package com.hermes.controller;

import com.hermes.entity.MetricSnapshot;
import com.hermes.mapper.MetricSnapshotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {

    @Autowired
    private MetricSnapshotMapper metricSnapshotMapper;

    @GetMapping("/history")
    public Object getHistory(@RequestParam(defaultValue = "cluster1") String clusterId,
                             @RequestParam(defaultValue = "hdfs") String module,
                             @RequestParam(defaultValue = "20") int limit) {
        // Simple query latest N records
        List<MetricSnapshot> list = metricSnapshotMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetricSnapshot>()
                .eq("module", module)
                .orderByDesc("create_time")
                .last("LIMIT " + limit)
        );
        return java.util.Map.of("code", 0, "data", list);
    }
}