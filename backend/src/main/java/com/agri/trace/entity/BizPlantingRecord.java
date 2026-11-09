package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 种植 / 农事记录
 */
@Data
@TableName("biz_planting_record")
public class BizPlantingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String batchNo;
    /** 手写作物名：农户录农事时未指派批次，直接写"草莓/玉米/高粱"等，审核通过后据此建档批次 */
    private String productName;
    /** SOIL/SOWING/FERTILIZE/IRRIGATE/PEST/WEED/OTHER */
    private String recordType;
    private LocalDate operationDate;
    private String content;
    private String materialName;
    private String dosage;

    private Long operatorId;
    private String operatorName;
    private String images;
    private String gps;
    /** 作业地点文本：农场档案地址 + 自动定位坐标，解决"只存坐标没人看得懂" */
    private String location;

    private String auditStatus;
    private String auditRemark;
    /** 审核人姓名：让农户知道"是谁审的" */
    private String auditBy;
    /** 审核时间：让农户能看清"什么时候被审核通过/驳回" */
    private LocalDateTime auditTime;

    private String dataHash;
    private String chainStatus;
    private String chainTxHash;
    private Long blockNumber;
    private LocalDateTime createTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
