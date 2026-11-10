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

    /**
     * 管理员撤销标记：1 表示该实证不再展示、不再参与可信度计分。
     *
     * <p>不做物理删除，是为了让 {@code evidenceHash} 仍能按原始文件集合重算——
     * 链上记录的是"提交当时有什么"，撤销只改变"现在是否采信"。
     * 若真删一行，一致性校验会把整条存证判成"链下数据被篡改"，
     * 反而掩盖了撤销这一合规动作。</p>
     */
    private Integer revoked;
    private String revokedBy;
    private LocalDateTime revokedTime;
    private String revokeReason;
}
