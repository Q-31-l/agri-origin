package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 实证材料（图片 / 视频 / 报告文件）。
 *
 * 设计要点：实证不是外挂附件——文件 SHA-256 会并入对应业务的上链摘要，
 * 消费者在溯源页看到的每一张图、每一段视频，都能与链上记录对应核验。
 */
@Data
public class EvidenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String IMAGE = "IMAGE";
    public static final String VIDEO = "VIDEO";
    public static final String FILE = "FILE";

    private Long id;
    /** 原始文件名 */
    private String name;
    /** 可访问地址（/api/file/download/{id}） */
    private String url;
    /** IMAGE / VIDEO / FILE */
    private String mediaType;
    private String contentType;
    private Long size;
    /** 文件内容 SHA-256，并入业务上链摘要 */
    private String hash;
    private String createTime;
}
