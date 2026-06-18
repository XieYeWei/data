package com.hermes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.entity.HealthScoreHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface HealthScoreHistoryMapper extends BaseMapper<HealthScoreHistory> {

    @Select("SELECT * FROM health_score_history WHERE score_date = #{date} LIMIT 1")
    HealthScoreHistory selectByDate(@Param("date") LocalDate date);

    @Select("SELECT * FROM health_score_history ORDER BY score_date DESC")
    java.util.List<HealthScoreHistory> selectAllOrderByDateDesc();
}
