package ai.knowhub.constant;

/**
 * 锁信息类型常量类
 *
 * 【作用】：定义框架中所有锁信息处理类型的标识常量。
 *          这些常量同时作为 Spring Bean 的名称，用于在 LockInfoHandleFactory 中查找对应的实现。
 *
 * 【使用场景】：
 * - "repeat_execute_limit"：重复执行限制（防重复提交）场景的锁信息处理
 * - "service_lock"：通用分布式服务锁场景的锁信息处理
 *
 * 【设计模式】：常量类模式 —— 集中管理魔法字符串，避免硬编码散落在各处。
 */
public class LockInfoType {

    /**
     * 重复执行限制的锁信息类型标识
     * 【对应 Bean】：RepeatExecuteLimitLockInfoHandle（通过 @Bean("repeat_execute_limit") 注册）
     */
    public static final String REPEAT_EXECUTE_LIMIT = "repeat_execute_limit";

    /**
     * 服务分布式锁的锁信息类型标识
     * 【对应 Bean】：ServiceLockInfoHandle（通过 @Bean("service_lock") 注册）
     */
    public static final String SERVICE_LOCK = "service_lock";

}
