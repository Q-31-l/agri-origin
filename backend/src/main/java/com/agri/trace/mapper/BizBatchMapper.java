package com.agri.trace.mapper;

import com.agri.trace.entity.BizBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface BizBatchMapper extends BaseMapper<BizBatch> {

    @Select("SELECT status AS name, COUNT(*) AS value FROM biz_batch WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> countByStatus();

    @Select("SELECT village_name AS name, COUNT(*) AS value FROM biz_batch " +
            "WHERE deleted = 0 AND village_name IS NOT NULL GROUP BY village_name")
    List<Map<String, Object>> countByVillage();

    @Select("SELECT DATE_FORMAT(create_time, '%m-%d') AS name, COUNT(*) AS value FROM biz_batch " +
            "WHERE deleted = 0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE_FORMAT(create_time, '%m-%d') ORDER BY name")
    List<Map<String, Object>> countByDay();

    @Select("SELECT COUNT(*) FROM biz_batch WHERE deleted = 0 AND status = #{status}")
    long countByStatusValue(@Param("status") String status);
}
