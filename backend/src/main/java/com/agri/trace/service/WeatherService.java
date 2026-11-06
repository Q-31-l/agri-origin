package com.agri.trace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当日天气（采收登记自动填充用）。
 *
 * <p>为什么由后端代取，而不是前端直接调第三方：
 * <ul>
 *   <li>微信小程序必须把第三方域名加进后台 request 白名单，毕设环境配不了；</li>
 *   <li>浏览器直连境外接口容易被拦，还会把用户 IP 暴露给第三方。</li>
 * </ul>
 * 统一走同域 /api，三端行为一致，也便于加缓存。
 *
 * <p>数据源 <a href="https://open-meteo.com">open-meteo</a>（无需 Key）。
 * 取不到就返回 null——天气只是辅助信息，绝不能因为它卡住采收登记，
 * 前端会退化成手选。任何情况下都不编造天气。
 */
@Slf4j
@Service
public class WeatherService {

    /** 缓存 30 分钟：同一地块反复打开表单不该反复打外部接口 */
    private static final long WEATHER_TTL_MS = 30 * 60 * 1000L;
    /** IP 归属地变化慢，缓存 6 小时足够 */
    private static final long IP_TTL_MS = 6 * 60 * 60 * 1000L;

    private static final int CONNECT_TIMEOUT = 2500;
    private static final int READ_TIMEOUT = 3000;

    @Resource
    private ObjectMapper objectMapper;

    private final Map<String, Cache> weatherCache = new ConcurrentHashMap<>();
    private final Map<String, Cache> ipCache = new ConcurrentHashMap<>();

    /** 天气结果：text 是给用户看的中文，code 保留原始 WMO 码便于追溯 */
    public Map<String, Object> current(double lat, double lng) {
        // 坐标按 0.05° 取整（约 5km）再缓存，避免小数点抖动导致缓存永不命中
        String key = String.format("%.2f,%.2f", lat, lng);
        Cache hit = weatherCache.get(key);
        if (hit != null && !hit.expired(WEATHER_TTL_MS)) {
            return hit.payload;
        }

        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                + "&longitude=" + lng
                + "&current=temperature_2m,weather_code&timezone=Asia%2FShanghai";
        Map<String, Object> data = fetchWeather(url);
        if (data == null) {
            // 取不到不要缓存失败结果，否则接口抖一下就要等半小时才恢复
            return null;
        }
        weatherCache.put(key, new Cache(data));
        return data;
    }

    /**
     * 按客户端 IP 做粗略定位（市级），作为浏览器定位被拒时的兜底。
     * 只在拿不到精确定位时调用，用于取天气；不用于"作业地点"——IP 定位
     * 误差可能上百公里，不能冒充地块位置。
     */
    public Map<String, Object> ipLocation(String ip) {
        if (ip == null || ip.isEmpty() || isPrivate(ip)) {
            return null;
        }
        Cache hit = ipCache.get(ip);
        if (hit != null && !hit.expired(IP_TTL_MS)) {
            return hit.payload;
        }
        try {
            String body = get("https://ipinfo.io/" + ip + "/json");
            if (body == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(body);
            String loc = node.path("loc").asText("");
            if (loc.indexOf(',') < 0) {
                return null;
            }
            String[] parts = loc.split(",");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("city", node.path("city").asText(""));
            data.put("region", node.path("region").asText(""));
            data.put("lat", Double.parseDouble(parts[0].trim()));
            data.put("lng", Double.parseDouble(parts[1].trim()));
            ipCache.put(ip, new Cache(data));
            return data;
        } catch (Exception e) {
            log.debug("IP 定位失败 ip={}: {}", ip, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> fetchWeather(String url) {
        try {
            String body = get(url);
            if (body == null) {
                return null;
            }
            JsonNode current = objectMapper.readTree(body).path("current");
            if (current.isMissingNode() || current.path("weather_code").isMissingNode()) {
                return null;
            }
            int code = current.path("weather_code").asInt(-1);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", code);
            data.put("text", wmoText(code));
            data.put("temperature", current.path("temperature_2m").asDouble());
            data.put("observedAt", current.path("time").asText(""));
            data.put("source", "open-meteo");
            return data;
        } catch (Exception e) {
            log.debug("天气接口失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * WMO 4677 天气现象码 → 采收登记里能选的中文。
     * 值域与前端下拉保持一致，保证"自动取到的值"用户也能手工改回去。
     */
    static String wmoText(int code) {
        if (code == 0) return "晴";
        if (code == 1 || code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "小雨";
        if (code == 61 || code == 80) return "小雨";
        if (code == 63 || code == 81) return "中雨";
        if (code == 65 || code == 82) return "大雨";
        if (code == 66 || code == 67) return "冻雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code == 85 || code == 86) return "阵雪";
        if (code == 95) return "雷阵雨";
        if (code == 96 || code == 99) return "雷阵雨";
        return "阴";
    }

    private String get(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "agri-trace/1.0");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 内网/回环地址查归属地没有意义，直接跳过，别浪费一次外部请求 */
    private boolean isPrivate(String ip) {
        if (ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("::1") || ip.startsWith("0:") || "0.0.0.0".equals(ip)) {
            return true;
        }
        // 172.16.0.0 ~ 172.31.255.255，只按第二段判断，别把 172.200.x 这类公网地址误杀
        if (ip.startsWith("172.")) {
            int dot = ip.indexOf('.', 4);
            if (dot > 0) {
                try {
                    int second = Integer.parseInt(ip.substring(4, dot));
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignore) {
                    return false;
                }
            }
        }
        return false;
    }

    private static final class Cache {
        private final Map<String, Object> payload;
        private final long at = System.currentTimeMillis();

        private Cache(Map<String, Object> payload) {
            this.payload = payload;
        }

        private boolean expired(long ttl) {
            return System.currentTimeMillis() - at > ttl;
        }
    }
}
