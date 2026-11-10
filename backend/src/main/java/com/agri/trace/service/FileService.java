package com.agri.trace.service;

import cn.hutool.core.io.FileUtil;
import com.agri.trace.common.BizException;
import com.agri.trace.config.StorageProperties;
import com.agri.trace.entity.SysFile;
import com.agri.trace.mapper.SysFileMapper;
import com.agri.trace.security.UserContext;
import com.agri.trace.sdk.HashUtil;
import com.agri.trace.vo.EvidenceVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import cn.hutool.core.util.URLUtil;
import cn.hutool.core.util.CharsetUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传与下载：文件内容 SHA-256 作为摘要上链
 */
@Slf4j
@Service
public class FileService {

    @Resource
    private StorageProperties properties;
    @Resource
    private SysFileMapper fileMapper;

    public Map<String, Object> upload(MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的文件");
        }
        if (file.getSize() > properties.getMaxSize()) {
            throw BizException.badRequest("文件大小不能超过 " + (properties.getMaxSize() / 1024 / 1024) + "MB");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = FileUtil.extName(original);
        if (ext == null || !properties.getAllowedExt().toLowerCase().contains(ext.toLowerCase())) {
            throw BizException.badRequest("不支持的文件类型，仅允许：" + properties.getAllowedExt());
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext.toLowerCase();
        Path base = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Path dir = base.resolve(datePath);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String hash = HashUtil.sha256(Files.readAllBytes(target));
            SysFile record = new SysFile();
            record.setFileName(storedName);
            record.setOriginalName(original);
            record.setFilePath(target.toString());
            record.setFileHash(hash);
            record.setFileSize(file.getSize());
            record.setContentType(file.getContentType());
            record.setBizType(bizType);
            record.setBizId(bizId);
            record.setUploadBy(UserContext.get() == null ? null : UserContext.userId());
            fileMapper.insert(record);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", record.getId());
            result.put("fileName", original);
            result.put("filePath", properties.getPublicPrefix() + record.getId());
            result.put("fileHash", hash);
            result.put("fileSize", record.getFileSize());
            result.put("mediaType", mediaType(record));
            return result;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BizException(500, "文件上传失败：" + e.getMessage());
        }
    }

    public SysFile getById(Long id) {
        SysFile file = fileMapper.selectById(id);
        if (file == null) {
            throw BizException.notFound("文件不存在");
        }
        return file;
    }

    public SysFile findByHash(String hash) {
        return fileMapper.selectOne(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getFileHash, hash).last("limit 1"));
    }

    // ---------------- 实证材料（图片 / 视频） ----------------

    /**
     * 处置等动作提交时，把此前以 bizId=空 上传的临时文件绑定到业务记录上。
     * 只允许绑定"尚未归属"的文件，避免把别人已挂到其它记录的证据抢过来。
     *
     * @return 绑定成功的实证列表（顺序与入参一致）
     */
    public List<EvidenceVO> bindEvidence(List<Long> ids, String bizType, Long bizId) {
        List<EvidenceVO> result = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            SysFile f = fileMapper.selectById(id);
            if (f == null) {
                throw BizException.badRequest("实证文件不存在或已被删除（id=" + id + "）");
            }
            if (f.getBizId() != null && !f.getBizId().equals(bizId)) {
                throw BizException.badRequest("实证文件已归属其它记录，请重新上传");
            }
            f.setBizType(bizType);
            f.setBizId(bizId);
            fileMapper.updateById(f);
            result.add(toEvidence(f));
        }
        return result;
    }

    /** 按业务类型 + 记录 id 批量取实证，返回 key 为 "bizType#id" 的分组（已排除被管理员撤销的文件） */
    public Map<String, List<EvidenceVO>> evidenceMap(String bizType, Collection<Long> ids) {
        Map<String, List<EvidenceVO>> map = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<SysFile> files = fileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getBizType, bizType)
                .in(SysFile::getBizId, ids)
                .eq(SysFile::getRevoked, 0)
                .orderByAsc(SysFile::getId));
        for (SysFile f : files) {
            map.computeIfAbsent(bizType + "#" + f.getBizId(), k -> new ArrayList<>()).add(toEvidence(f));
        }
        return map;
    }

    /**
     * 全量实证（含已被撤销的文件）。
     *
     * <p>只给摘要重算用，不要拿去渲染页面——撤销掉的照片仍在链上存证的原始集合里，
     * 少算一张就会让历史存证判成"链下数据被篡改"。</p>
     */
    public List<EvidenceVO> listEvidence(String bizType, Long bizId) {
        List<SysFile> files = fileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getBizType, bizType)
                .eq(SysFile::getBizId, bizId)
                .orderByAsc(SysFile::getId));
        List<EvidenceVO> list = new ArrayList<>();
        for (SysFile f : files) {
            list.add(toEvidence(f));
        }
        return list;
    }

    /** 展示用实证列表：排除被管理员撤销的文件 */
    public List<EvidenceVO> listEvidenceVisible(String bizType, Long bizId) {
        List<SysFile> files = fileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getBizType, bizType)
                .eq(SysFile::getBizId, bizId)
                .eq(SysFile::getRevoked, 0)
                .orderByAsc(SysFile::getId));
        List<EvidenceVO> list = new ArrayList<>();
        for (SysFile f : files) {
            list.add(toEvidence(f));
        }
        return list;
    }

    /**
     * 管理员撤销单张实证。
     *
     * <p>不物理删文件：链上存的是文件 SHA-256，删了盘上的文件等于把"可复核"变成"不可复核"。
     * 撤销只做三件事——不再展示、禁止下载、记录撤销人与理由。</p>
     */
    public void revokeFile(Long fileId, String reason, String operator) {
        SysFile file = fileMapper.selectById(fileId);
        if (file == null) {
            throw BizException.notFound("文件不存在或已被撤销");
        }
        if (Integer.valueOf(1).equals(file.getRevoked())) {
            throw BizException.conflict("该文件已被撤销，请勿重复操作");
        }
        file.setRevoked(1);
        file.setRevokedBy(operator);
        file.setRevokedTime(java.time.LocalDateTime.now());
        file.setRevokeReason(reason);
        fileMapper.updateById(file);
        log.info("实证文件被撤销：id={}, name={}, 操作人={}, 理由={}",
                fileId, file.getOriginalName(), operator, reason);
    }

    /** 按业务记录批量撤销其实证，返回撤销数量 */
    public int revokeByBiz(String bizType, Long bizId, String reason, String operator) {
        List<SysFile> files = fileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getBizType, bizType)
                .eq(SysFile::getBizId, bizId)
                .eq(SysFile::getRevoked, 0));
        int n = 0;
        for (SysFile f : files) {
            f.setRevoked(1);
            f.setRevokedBy(operator);
            f.setRevokedTime(java.time.LocalDateTime.now());
            f.setRevokeReason(reason);
            fileMapper.updateById(f);
            n++;
        }
        return n;
    }

    /**
     * 实证联合摘要：把全部证据哈希排序后再次摘要，得到一枚"证据根哈希"。
     * 它随业务摘要一起上链——任何一张图、一段视频被替换，根哈希都会变。
     */
    public String evidenceHash(List<EvidenceVO> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return null;
        }
        List<String> hashes = new ArrayList<>();
        for (EvidenceVO e : evidences) {
            if (e.getHash() != null) {
                hashes.add(e.getHash());
            }
        }
        if (hashes.isEmpty()) {
            return null;
        }
        Collections.sort(hashes);
        return HashUtil.digestOf("EVIDENCE", String.join(",", hashes));
    }

    public EvidenceVO toEvidence(SysFile f) {
        EvidenceVO vo = new EvidenceVO();
        vo.setId(f.getId());
        vo.setName(f.getOriginalName() == null ? f.getFileName() : f.getOriginalName());
        vo.setUrl(properties.getPublicPrefix() + f.getId());
        vo.setMediaType(mediaType(f));
        vo.setContentType(f.getContentType());
        vo.setSize(f.getFileSize());
        vo.setHash(f.getFileHash());
        vo.setCreateTime(f.getCreateTime() == null ? null
                : f.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return vo;
    }

    private String mediaType(SysFile f) {
        String ct = f.getContentType() == null ? "" : f.getContentType().toLowerCase();
        if (ct.startsWith("video/")) {
            return EvidenceVO.VIDEO;
        }
        if (ct.startsWith("image/")) {
            return EvidenceVO.IMAGE;
        }
        String ext = FileUtil.extName(f.getFileName()).toLowerCase();
        if ("mp4".equals(ext) || "mov".equals(ext) || "m4v".equals(ext) || "webm".equals(ext)) {
            return EvidenceVO.VIDEO;
        }
        if ("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext)
                || "gif".equals(ext)) {
            return EvidenceVO.IMAGE;
        }
        return EvidenceVO.FILE;
    }

    public void download(Long id, HttpServletResponse response) {
        SysFile file = getById(id);
        // 撤销后连直链也不再放行：否则页面隐藏了图片，旧链接还能打开，等于没撤
        if (Integer.valueOf(1).equals(file.getRevoked())) {
            throw BizException.forbidden("该文件已被管理员撤销，不再提供查看");
        }
        File target = new File(file.getFilePath());
        if (!target.exists()) {
            throw BizException.notFound("文件已丢失，请重新上传");
        }
        response.setContentType(file.getContentType() == null
                ? "application/octet-stream" : file.getContentType());
        String downloadName = file.getOriginalName() == null ? file.getFileName() : file.getOriginalName();
        response.setHeader("Content-Disposition",
                "inline; filename*=UTF-8''" + URLUtil.encode(downloadName, CharsetUtil.CHARSET_UTF_8));
        try {
            Files.copy(target.toPath(), response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.warn("文件下载中断: {}", e.getMessage());
        }
    }
}
