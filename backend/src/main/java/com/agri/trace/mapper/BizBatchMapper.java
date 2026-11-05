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

    /**
     * 状态分布。三个参数是"数据范围"过滤条件，传 null 表示该维度不限制，
     * 由 StatsService 依据当前登录角色决定传入哪些值——保证图表与上方总量口径一致。
     */
    @Select("<script>SELECT status AS name, COUNT(*) AS value FROM biz_batch WHERE deleted = 0 " +
            "<if test='farmerId != null'>AND farmer_id = #{farmerId} </if>" +
            "<if test='villageId != null'>AND village_id = #{villageId} </if>" +
            "<if test='createBy != null'>AND create_by = #{createBy} </if>" +
            "GROUP BY status</script>")
    List<Map<String, Object>> countByStatus(@Param("farmerId") Long farmerId,
                                            @Param("villageId") Long villageId,
                                            @Param("createBy") Long createBy);

    @Select("<script>SELECT village_name AS name, COUNT(*) AS value FROM biz_batch " +
            "WHERE deleted = 0 AND village_name IS NOT NULL " +
            "<if test='farmerId != null'>AND farmer_id = #{farmerId} </if>" +
            "<if test='villageId != null'>AND village_id = #{villageId} </if>" +
            "<if test='createBy != null'>AND create_by = #{createBy} </if>" +
            "GROUP BY village_name</script>")
    List<Map<String, Object>> countByVillage(@Param("farmerId") Long farmerId,
                                             @Param("villageId") Long villageId,
                                             @Param("createBy") Long createBy);

    @Select("<script>SELECT DATE_FORMAT(create_time, '%m-%d') AS name, COUNT(*) AS value FROM biz_batch " +
            "WHERE deleted = 0 AND create_time &gt;= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "<if test='farmerId != null'>AND farmer_id = #{farmerId} </if>" +
            "<if test='villageId != null'>AND village_id = #{villageId} </if>" +
            "<if test='createBy != null'>AND create_by = #{createBy} </if>" +
            "GROUP BY DATE_FORMAT(create_time, '%m-%d') ORDER BY name</script>")
    List<Map<String, Object>> countByDay(@Param("farmerId") Long farmerId,
                                         @Param("villageId") Long villageId,
                                         @Param("createBy") Long createBy);

    @Select("SELECT COUNT(*) FROM biz_batch WHERE deleted = 0 AND status = #{status}")
    long countByStatusValue(@Param("status") String status);
}
