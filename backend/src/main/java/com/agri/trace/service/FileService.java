package com.agri.trace.service;

import cn.hutool.core.io.FileUtil;
import com.agri.trace.common.BizException;
import com.agri.trace.config.StorageProperties;
import com.agri.trace.entity.SysFile;
import com.agri.trace.mapper.SysFileMapper;
import com.agri.trace.security.UserContext;
import com.agri.trace.sdk.HashUtil;
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
import java.util.LinkedHashMap;
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

    public void download(Long id, HttpServletResponse response) {
        SysFile file = getById(id);
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
