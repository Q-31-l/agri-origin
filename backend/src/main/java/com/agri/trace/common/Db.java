package com.agri.trace.common;

/**
 * 数据库写操作结果断言。
 *
 * <p>MyBatis-Plus 的乐观锁（{@code @Version}）在更新失败时不抛异常，只把影响行数返回 0。
 * 如果调用方不检查返回值，并发冲突会被静默吞掉——这比不加乐观锁更危险，
 * 因为它给了人"已经加锁"的错觉。所有带版本号的实体更新都必须经过这里。
 */
public final class Db {

    private Db() {
    }

    /**
     * 断言更新命中了一行。
     *
     * @param rows {@code mapper.updateById(entity)} 的返回值
     * @throws BizException 409 冲突，提示前端刷新后重试
     */
    public static void updated(int rows) {
        if (rows == 0) {
            throw BizException.stale();
        }
    }
}
