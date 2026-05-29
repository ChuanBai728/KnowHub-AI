package ai.knowhub.lockinfo;

import org.aspectj.lang.JoinPoint;

/**
 * 锁信息处理接口
 *
 * 【作用】：定义锁名称生成的统一接口，不同的锁场景（如分布式锁、防重复提交）可以有不同的实现。
 *
 * 【设计模式】：策略模式（Strategy Pattern）
 * - 不同的锁信息处理实现类代表不同的锁名称生成策略
 * - 通过 LockInfoHandleFactory 可以在运行时切换策略
 *
 * 【实现类】：
 * - ServiceLockInfoHandle：分布式服务锁的名称生成
 * - RepeatExecuteLimitLockInfoHandle：防重复提交的名称生成
 */
public interface LockInfoHandle {

    /**
     * 通过 AOP JoinPoint 生成锁名称（支持 SpEL 表达式解析）
     *
     * @param joinPoint AOP 连接点，包含被拦截方法的信息和参数
     * @param name      注解中配置的 name 值
     * @param keys      SpEL 表达式数组，用于动态生成锁标识
     * @return 完整的锁名称
     */
    String getLockName(JoinPoint joinPoint, String name, String[] keys);

    /**
     * 简单方式生成锁名称（不经过 SpEL 解析）
     *
     * 【适用场景】：在非 AOP 环境中（如编程式调用）生成锁名称
     *
     * @param name 注解中配置的 name 值
     * @param keys 锁标识 key 数组（原始字符串）
     * @return 完整的锁名称
     */
    String simpleGetLockName(String name,String[] keys);
}
