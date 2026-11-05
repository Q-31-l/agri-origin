package com.agri.trace.dto;

import lombok.Data;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class LogisticsDTO {

    @NotNull(message = "请选择批次")
    private Long batchId;

    @NotBlank(message = "请填写运单号")
    private String logisticsNo;

    private String fromOrg;
    private String toOrg;
    private String vehicleNo;
    private String driverName;
    private String driverPhone;

    @NotNull(message = "请选择发运时间")
    private LocalDateTime departTime;

    private LocalDateTime arriveTime;
    private String temperature;
    private String status = "DEPARTED";
    private String handoverFile;
    private String fileHash;
    private String remark;

    /** 现场实证：图片/视频文件 id 列表，提交后证据哈希随业务摘要一并上链 */
    private List<Long> evidenceIds;
}
