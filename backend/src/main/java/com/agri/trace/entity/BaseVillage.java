package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("base_village")
public class BaseVillage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String villageCode;
    private String villageName;
    private String regionCode;
    private String regionName;
    private String address;
    private Long managerId;
    private String managerName;
    private String remark;
    private LocalDateTime createTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
