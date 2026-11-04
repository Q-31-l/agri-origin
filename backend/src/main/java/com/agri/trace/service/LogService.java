package com.agri.trace.service;

import com.agri.trace.common.PageResult;
import com.agri.trace.entity.SysOperationLog;
import com.agri.trace.mapper.SysOperationLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 操作审计日志查询
 */
@Service
public class LogService {

    @Resource
    private SysOperationLogMapper logMapper;

    public PageResult<SysOperationLog> page(long page, long size, String username, String module) {
        LambdaQueryWrapper<SysOperationLog> wrapper = Wrappers.<SysOperationLog>lambdaQuery()
                .like(username != null && !username.isEmpty(), SysOperationLog::getUsername, username)
                .eq(module != null && !module.isEmpty(), SysOperationLog::getModule, module)
                .orderByDesc(SysOperationLog::getId);
        return PageResult.of(logMapper.selectPage(new Page<>(page, size), wrapper));
    }
}
