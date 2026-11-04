package com.agri.trace.config;

import com.agri.trace.common.BizException;
import com.agri.trace.entity.SysOperationLog;
import com.agri.trace.mapper.SysOperationLogMapper;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作审计：记录所有写操作（POST/PUT/DELETE）
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Resource
    private SysOperationLogMapper logMapper;
    @Resource
    private ObjectMapper objectMapper;

    @Around("execution(* com.agri.trace.controller..*.*(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return point.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return point.proceed();
        }

        long start = System.currentTimeMillis();
        SysOperationLog entity = new SysOperationLog();
        entity.setCreateTime(LocalDateTime.now());
        entity.setRequestUri(request.getRequestURI());
        entity.setMethod(method);
        entity.setIp(clientIp(request));
        entity.setParams(serializeArgs(point.getArgs()));
        entity.setModule(resolveModule(request.getRequestURI()));
        entity.setAction(((MethodSignature) point.getSignature()).getMethod().getName());

        LoginUser user = UserContext.get();
        if (user != null) {
            entity.setUserId(user.getUserId());
            entity.setUsername(user.getUsername());
            entity.setRoleCode(user.getRoleCode());
        }

        try {
            Object result = point.proceed();
            entity.setStatus(1);
            entity.setCostMs(System.currentTimeMillis() - start);
            save(entity);
            return result;
        } catch (Throwable e) {
            entity.setStatus(0);
            entity.setErrorMsg(e instanceof BizException ? e.getMessage() : e.getClass().getSimpleName());
            entity.setCostMs(System.currentTimeMillis() - start);
            save(entity);
            throw e;
        }
    }

    private void save(SysOperationLog entity) {
        try {
            logMapper.insert(entity);
        } catch (Exception e) {
            log.warn("操作日志写入失败: {}", e.getMessage());
        }
    }

    private String serializeArgs(Object[] args) {
        try {
            String json = objectMapper.writeValueAsString(Arrays.asList(args));
            json = json.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
            return json.length() > 1800 ? json.substring(0, 1800) : json;
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveModule(String uri) {
        String[] parts = uri.split("/");
        for (String part : parts) {
            if (part.isEmpty() || "api".equals(part)) {
                continue;
            }
            return part;
        }
        return "system";
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
}
