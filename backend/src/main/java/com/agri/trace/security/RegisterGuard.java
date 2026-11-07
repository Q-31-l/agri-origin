package com.agri.trace.security;

import com.agri.trace.common.BizException;
import com.agri.trace.common.ResultCode;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册接口的轻量防刷。
 *
 * <p>注册是唯一一个"匿名就能写库"的入口，没有验证码，如果不加约束，
 * 一个脚本可以在几分钟内灌进几百个账号。这里按来源 IP 做两道滑动窗口：
 * <ul>
 *   <li><b>成功账号数</b>：每 IP 每小时最多 {@value #MAX_SUCCESS_PER_HOUR} 个。
 *       只统计真正建成功的账号——填错手机号、账号重名这类失败不该扣配额，
 *       否则用户输错几次就被锁一小时，体验比安全收益更亏；</li>
 *   <li><b>尝试次数</b>：每 IP 每小时最多 {@value #MAX_ATTEMPT_PER_HOUR} 次，
 *       用来挡住拿脚本狂刷接口的行为（这类请求不会建成账号，但会打满 CPU）。</li>
 * </ul>
 *
 * <p>用内存而不是 Redis：部署是单实例，注册量级很小，重启后计数归零也不影响
 * 安全边界——它挡的是脚本，不是精确配额。</p>
 */
@Component
public class RegisterGuard {

    /** 单 IP 每小时允许成功注册的账号数 */
    private static final int MAX_SUCCESS_PER_HOUR = 5;
    /** 单 IP 每小时允许的请求次数（含失败） */
    private static final int MAX_ATTEMPT_PER_HOUR = 30;
    /** 滑动窗口长度 */
    private static final long WINDOW_MS = 60 * 60 * 1000L;
    /** 超过这个键数量就做一次全量清理，避免长期运行内存只增不减 */
    private static final int CLEAN_THRESHOLD = 5000;

    private final Map<String, Deque<Long>> successes = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    /**
     * 请求进来先过一遍：返回来源 IP，供注册成功后调用 {@link #markSuccess(String)}。
     *
     * @throws BizException 429：同一 IP 成功注册数或请求次数超限
     */
    public String check(HttpServletRequest request) {
        String ip = clientIp(request);
        long now = System.currentTimeMillis();
        if (attempts.size() > CLEAN_THRESHOLD) {
            purgeAll(successes, now);
            purgeAll(attempts, now);
        }
        int done = pruneAndCount(successes, ip, now);
        if (done >= MAX_SUCCESS_PER_HOUR) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS,
                    "当前网络注册账号过多，请稍后再试或联系管理员开通");
        }
        int tried = pruneAndCount(attempts, ip, now);
        if (tried >= MAX_ATTEMPT_PER_HOUR) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS,
                    "操作过于频繁，请稍后再试");
        }
        record(attempts, ip, now);
        return ip;
    }

    /** 注册成功后才占用配额 */
    public void markSuccess(String ip) {
        record(successes, ip, System.currentTimeMillis());
    }

    private int pruneAndCount(Map<String, Deque<Long>> map, String ip, long now) {
        Deque<Long> queue = map.get(ip);
        if (queue == null) {
            return 0;
        }
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > WINDOW_MS) {
                queue.pollFirst();
            }
            return queue.size();
        }
    }

    private void record(Map<String, Deque<Long>> map, String ip, long now) {
        Deque<Long> queue = map.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(now);
        }
    }

    /**
     * 取真实来源 IP。
     *
     * <p>取值顺序有讲究：
     * <ol>
     *   <li>优先 X-Real-IP——nginx 用 {@code proxy_set_header X-Real-IP $remote_addr}
     *       覆盖写，客户端伪造不了；</li>
     *   <li>其次 X-Forwarded-For 的<b>最后</b>一段：nginx 的 $proxy_add_x_forwarded_for
     *       是"客户端自带值 + 真实 IP"，第一段反而可能是伪造的，取最后一段才可信；</li>
     *   <li>最后才用 remoteAddr（本机直连 8080 时就是它）。</li>
     * </ol>
     * 如果按最常见的"取 XFF 第一段"来写，攻击者每次换一个 XFF 就能绕开限流。</p>
     */
    private String clientIp(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.trim().isEmpty()) {
            return real.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            int comma = xff.lastIndexOf(',');
            return (comma >= 0 ? xff.substring(comma + 1) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    private void purgeAll(Map<String, Deque<Long>> map, long now) {
        Iterator<Map.Entry<String, Deque<Long>>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> q = it.next().getValue();
            synchronized (q) {
                if (q.isEmpty() || now - q.peekLast() > WINDOW_MS) {
                    it.remove();
                }
            }
        }
    }
}
