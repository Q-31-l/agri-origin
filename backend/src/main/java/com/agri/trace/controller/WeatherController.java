package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当日天气。采收登记打开表单时自动带出，用户可改。
 * 只要登录即可访问（没有独立权限点，任何能登记采收的角色都用得上）。
 */
@Tag(name = "天气")
@RestController
@RequestMapping("/weather")
public class WeatherController {

    @Resource
    private WeatherService weatherService;

    @Operation(summary = "当日天气（按坐标取，缺坐标时用请求 IP 粗略定位兜底）")
    @GetMapping("/current")
    public R<Map<String, Object>> current(@RequestParam(required = false) Double lat,
                                          @RequestParam(required = false) Double lng,
                                          HttpServletRequest request) {
        String locateBy = "GPS";
        double la;
        double ln;
        String city = null;

        if (valid(lat, lng)) {
            la = lat;
            ln = lng;
        } else {
            // 浏览器定位被拒/超时：退到 IP 粗略定位，只用于取天气，不写进作业地点
            Map<String, Object> ip = weatherService.ipLocation(clientIp(request));
            if (ip == null) {
                // 取不到就返回空，前端保留下拉手选，不编造天气
                return R.ok(null);
            }
            la = ((Number) ip.get("lat")).doubleValue();
            ln = ((Number) ip.get("lng")).doubleValue();
            city = String.valueOf(ip.get("city"));
            locateBy = "IP";
        }

        Map<String, Object> weather = weatherService.current(la, ln);
        if (weather == null) {
            return R.ok(null);
        }
        Map<String, Object> data = new LinkedHashMap<>(weather);
        data.put("locateBy", locateBy);
        data.put("city", city);
        data.put("lat", la);
        data.put("lng", ln);
        return R.ok(data);
    }

    private boolean valid(Double lat, Double lng) {
        return lat != null && lng != null
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                && !(lat == 0 && lng == 0);
    }

    /** nginx 已经把真实来源写进 X-Real-IP；本地直连时退回 remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Forwarded-For");
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip == null ? "" : ip.trim();
    }
}
