package ai.knowhub.lockinfo;

import ai.knowhub.parser.ExtParameterNameDiscoverer;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import ai.knowhub.core.SpringUtil;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static ai.knowhub.core.Constants.SEPARATOR;

;

/**
 * 锁信息处理抽象基类
 *
 * 【作用】：提供锁名称生成的通用逻辑，子类只需实现 getLockPrefixName() 返回各自的前缀即可。
 *
 * 【设计模式】：模板方法模式（Template Method Pattern）
 * - getLockName() 和 simpleGetLockName() 是模板方法，定义了锁名称的生成流程
 * - getLockPrefixName() 是抽象方法，由子类决定锁名称的前缀部分
 *
 * 【锁名称的组成规则】：
 * 完整锁名称 = 应用前缀 + "-" + 锁前缀 + ":" + 注解name + ":" + SpEL解析后的keys
 * 例如：myApp-REPEAT_EXECUTE_LIMIT:orderSubmit:order_123
 *
 * 【关键概念】：
 * - SpEL（Spring Expression Language）：Spring 表达式语言，可以在运行时动态解析表达式
 *   例如 "#orderId" 会被解析为方法参数中名为 orderId 的值
 * - AOP JoinPoint：连接点，代表被拦截的方法及其参数信息
 */
@Slf4j
public abstract class AbstractLockInfoHandle implements LockInfoHandle {

    /**
     * 锁名称中用于简单模式的固定前缀
     */
    private static final String LOCK_DISTRIBUTE_ID_NAME_PREFIX = "LOCK_DISTRIBUTE_ID";

    /**
     * 参数名发现器，用于在 SpEL 表达式中解析参数名
     * 【说明】：编译后的 Java 字节码默认不保留参数名，需要借助 ASM 等工具从 LocalVariableTable 中读取
     */
    private final ParameterNameDiscoverer nameDiscoverer = new ExtParameterNameDiscoverer();

    /**
     * SpEL 表达式解析器
     * 【说明】：Spring 内置的表达式引擎，支持在运行时动态求值
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 获取锁名称的前缀（抽象方法，由子类实现）
     *
     * @return 锁前缀字符串，例如 "SERVICE_LOCK" 或 "REPEAT_EXECUTE_LIMIT"
     */
    protected abstract String getLockPrefixName();

    /**
     * 生成完整的分布式锁名称（带 SpEL 表达式解析）
     *
     * 【流程】：
     * 1. 获取应用名称前缀（SpringUtil.getPrefixDistinctionName()）
     * 2. 拼接锁类型前缀（由子类提供）
     * 3. 拼接注解中的 name 值
     * 4. 解析 SpEL 表达式 keys，获取运行时的实际值并拼接
     *
     * @param joinPoint AOP 连接点，包含被拦截方法的信息
     * @param name      注解中配置的 name 值
     * @param keys      注解中配置的 SpEL 表达式数组
     * @return 完整的锁名称字符串
     */
    @Override
    public String getLockName(JoinPoint joinPoint,String name,String[] keys){
        return SpringUtil.getPrefixDistinctionName() + "-" + getLockPrefixName() + SEPARATOR + name + getRelKey(joinPoint, keys);
    }

    /**
     * 简单方式生成锁名称（不解析 SpEL，直接拼接 keys）
     *
     * 【适用场景】：在非 AOP 环境中（如手动调用 ServiceLockTool）生成锁名称，
     *              此时没有 JoinPoint，keys 直接传入原始字符串。
     *
     * @param name 注解中配置的 name 值
     * @param keys 锁标识 key 数组（原始字符串，不经过 SpEL 解析）
     * @return 完整的锁名称字符串
     */
    @Override
    public String simpleGetLockName(String name,String[] keys){
        List<String> definitionKeyList = new ArrayList<>();
        for (String key : keys) {
            if (StringUtil.isNotEmpty(key)) {
                definitionKeyList.add(key);
            }
        }
        return SpringUtil.getPrefixDistinctionName() + "-" +
                LOCK_DISTRIBUTE_ID_NAME_PREFIX + SEPARATOR + name + SEPARATOR + String.join(SEPARATOR, definitionKeyList);
    }

    /**
     * 获取与 SpEL 表达式解析相关的 key 部分
     *
     * @param joinPoint AOP 连接点
     * @param keys      SpEL 表达式数组
     * @return 解析后的 key 字符串（以分隔符开头）
     */
    private String getRelKey(JoinPoint joinPoint, String[] keys){
        Method method = getMethod(joinPoint);
        List<String> definitionKeys = getSpElKey(keys, method, joinPoint.getArgs());
        return SEPARATOR + String.join(SEPARATOR, definitionKeys);
    }

    /**
     * 从 JoinPoint 中获取被拦截的 Method 对象
     *
     * 【说明】：如果方法来自接口，需要通过反射获取实际实现类的方法对象。
     *
     * @param joinPoint AOP 连接点
     * @return 被拦截的方法对象
     */
    private Method getMethod(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        // 如果方法声明在接口上，需要获取实现类的方法
        if (method.getDeclaringClass().isInterface()) {
            try {
                method = joinPoint.getTarget().getClass().getDeclaredMethod(signature.getName(),
                        method.getParameterTypes());
            } catch (Exception e) {
                log.error("get method error ",e);
            }
        }
        return method;
    }

    /**
     * 解析 SpEL 表达式 keys，获取运行时的实际值
     *
     * 【示例】：
     * 注解配置 keys = {"#orderId"}
     * 方法签名：public void submit(String orderId)
     * 调用时传入：submit("12345")
     * 解析结果：["12345"]
     *
     * @param definitionKeys SpEL 表达式数组
     * @param method         被拦截的方法
     * @param parameterValues 方法调用时的实际参数值
     * @return 解析后的 key 值列表
     */
    private List<String> getSpElKey(String[] definitionKeys, Method method, Object[] parameterValues) {
        List<String> definitionKeyList = new ArrayList<>();
        for (String definitionKey : definitionKeys) {
            if (!ObjectUtils.isEmpty(definitionKey)) {
                // 创建 SpEL 求值上下文，绑定方法参数
                EvaluationContext context = new MethodBasedEvaluationContext(null, method, parameterValues, nameDiscoverer);
                // 解析表达式并获取值
                Object objKey = parser.parseExpression(definitionKey).getValue(context);
                definitionKeyList.add(ObjectUtils.nullSafeToString(objKey));
            }
        }
        return definitionKeyList;
    }

}
