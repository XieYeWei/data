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
                             @RequestParam(defaultValue = "queue") String module,
                             @RequestParam(required = false) String startTime,
                             @RequestParam(required = false) String endTime,
                             @RequestParam(defaultValue = "50") int limit) {

        var query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MetricSnapshot>()
                .eq("module", module)
                .orderByDesc("create_time")
                .last("LIMIT " + limit);

        if (startTime != null && !startTime.isEmpty()) {
            query.ge("create_time", startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            query.le("create_time", endTime);
        }

        List<MetricSnapshot> list = metricSnapshotMapper.selectList(query);
        return java.util.Map.of("code", 0, "data", list);
    }
}