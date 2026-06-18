package com.hermes.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.entity.FileNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileNoteMapper extends BaseMapper<FileNote> {

    @Select("SELECT * FROM file_note WHERE cluster_id = #{clusterId} AND path = #{path} LIMIT 1")
    FileNote findByClusterIdAndPath(@Param("clusterId") String clusterId, @Param("path") String path);
}
