package ai.knowhub.lockinfo.factory;

import ai.knowhub.lockinfo.LockInfoHandle;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 锁信息处理工厂类
 *
 * 【作用】：根据锁信息类型标识（如 "repeat_execute_limit"、"service_lock"），
 *          从 Spring 容器中获取对应的 LockInfoHandle Bean 实例。
 *
 * 【设计模式】：工厂模式（Factory Pattern） + Spring IoC
 * - 通过 ApplicationContext 按名称查找 Bean，实现松耦合
 * - 新增锁类型时，只需注册一个新的 LockInfoHandle Bean（使用对应的名称即可），无需修改工厂代码
 *
 * 【关键注解】：
 * - ApplicationContextAware：Spring 回调接口，实现此接口后 Spring 会自动注入 ApplicationContext
 */
public class LockInfoHandleFactory implements ApplicationContextAware {

    /**
     * Spring 应用上下文，用于按名称获取 Bean
     */
    private ApplicationContext applicationContext;

    /**
     * 根据锁信息类型获取对应的 LockInfoHandle 实现
     *
     * @param lockInfoType 锁信息类型标识（即 Bean 名称），
     *                     例如 LockInfoType.REPEAT_EXECUTE_LIMIT ("repeat_execute_limit")
     * @return 对应的 LockInfoHandle 实现
     */
    public LockInfoHandle getLockInfoHandle(String lockInfoType){
        return applicationContext.getBean(lockInfoType,LockInfoHandle.class);
    }

    /**
     * Spring 回调方法，注入 ApplicationContext
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
