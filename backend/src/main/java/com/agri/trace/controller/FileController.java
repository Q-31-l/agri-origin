package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.security.RequirePerm;
import com.agri.trace.service.FileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Tag(name = "文件上传与下载")
@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private FileService fileService;

    @Operation(summary = "上传文件（返回 SHA-256 摘要，供上链存证）")
    @RequirePerm("file:upload")
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(required = false) String bizType,
                                         @RequestParam(required = false) Long bizId) {
        return R.ok("上传成功", fileService.upload(file, bizType, bizId));
    }

    @Operation(summary = "文件预览/下载（公开，用于质检报告与现场照片展示）")
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        fileService.download(id, response);
    }

    @Operation(summary = "某条业务记录的实证材料列表（公开，供溯源页展示图片/视频）")
    @GetMapping("/evidence")
    public R<java.util.List<com.agri.trace.vo.EvidenceVO>> evidence(@RequestParam String bizType,
                                                                    @RequestParam Long bizId) {
        return R.ok(fileService.listEvidenceVisible(bizType, bizId));
    }
}
