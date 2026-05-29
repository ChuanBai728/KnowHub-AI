package ai.knowhub.parser;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.NativeDetector;

/**
 * 扩展参数名发现器
 *
 * 【作用】：在 Spring 默认的参数名发现器基础上，增加 LocalVariableTable 方式的参数名发现。
 *          用于在 SpEL 表达式中解析方法参数名（如 "#orderId"）。
 *
 * 【为什么需要这个扩展？】：
 * Java 编译时默认不保留方法参数名（字节码中参数名为 arg0、arg1...），
 * 只有加了 -parameters 编译选项才能保留。当没有加这个选项时，
 * 需要通过 ASM 读取 .class 文件的 LocalVariableTable 来获取参数名。
 *
 * 【关键概念】：
 * - DefaultParameterNameDiscoverer：Spring 默认的参数名发现器，按优先级尝试多种方式
 * - LocalVariableTableParameterNameDiscoverer：通过 ASM 读取字节码中的局部变量表来获取参数名
 * - NativeDetector：检测是否运行在 GraalVM Native Image 环境中
 *
 * 【设计模式】：装饰器模式（Decorator Pattern）—— 在默认发现器基础上增加额外的发现策略
 */
public class ExtParameterNameDiscoverer extends DefaultParameterNameDiscoverer {

    public ExtParameterNameDiscoverer() {
        super();
        // Native Image 环境下不支持 ASM 字节码读取，所以跳过
        if (!NativeDetector.inNativeImage()) {
            addDiscoverer(new LocalVariableTableParameterNameDiscoverer());
        }
    }
}
