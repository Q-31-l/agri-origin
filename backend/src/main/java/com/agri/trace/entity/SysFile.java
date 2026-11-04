package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String originalName;
    private String filePath;
    /** SHA-256，文件内容摘要，用于上链比对 */
    private String fileHash;
    private Long fileSize;
    private String contentType;
    private String bizType;
    private Long bizId;
    private Long uploadBy;
    private LocalDateTime createTime;
}
