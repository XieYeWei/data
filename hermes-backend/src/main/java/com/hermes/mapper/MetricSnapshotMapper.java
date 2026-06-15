package com.hermes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.entity.MetricSnapshot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MetricSnapshotMapper extends BaseMapper<MetricSnapshot> {
}