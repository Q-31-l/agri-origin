package com.agri.trace.security;

/**
 * 当前请求用户上下文
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new com.agri.trace.common.BizException(
                    com.agri.trace.common.ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return user;
    }

    public static Long userId() {
        return require().getUserId();
    }

    public static String roleCode() {
        return require().getRoleCode();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
