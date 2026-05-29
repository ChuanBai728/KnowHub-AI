package com.baidu.fsg.uid.exception;

/**
 * UID生成异常 - ID生成过程中的专用异常类
 *
 * 【类的作用】
 * 这是UID生成器的专用运行时异常，用于表示ID生成过程中发生的各种错误。
 * 继承自RuntimeException，属于非受检异常（unchecked exception），调用方可以选择捕获或不捕获。
 *
 * 【使用场景】
 * 1. 时钟回拨：服务器时间突然变小，可能导致生成重复ID
 * 2. Worker ID超限：分配的Worker ID超过了最大允许值
 * 3. 时间戳用尽：时间戳部分的位数已经不够用了
 * 4. 缓冲区异常：环形缓冲区操作失败
 *
 * 【设计模式】
 * 使用了"异常层次"模式：
 * - 定义专用异常类，便于调用方精确捕获和处理
 * - 继承RuntimeException，避免强制异常处理的样板代码
 *
 * 【关键概念 - 序列化】
 * serialVersionUID用于版本控制：
 * - 序列化时会将此ID写入字节流
 * - 反序列化时会校验此ID是否匹配
 * - 如果不匹配，会抛出InvalidClassException
 */
public class UidGenerateException extends RuntimeException {

    /**
     * 序列化版本号
     * 用于类的序列化和反序列化时的版本校验
     */
    private static final long serialVersionUID = -27048199131316992L;

    /**
     * 无参构造函数
     */
    public UidGenerateException() {
        super();
    }

    /**
     * 带消息和原因的构造函数
     *
     * @param message 异常描述信息
     * @param cause   导致此异常的根本原因
     */
    public UidGenerateException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 带消息的构造函数
     *
     * @param message 异常描述信息
     */
    public UidGenerateException(String message) {
        super(message);
    }

    /**
     * 带格式化消息的构造函数
     *
     * @param msgFormat 消息格式模板（使用String.format格式）
     * @param args      格式化参数
     *
     * 【使用示例】
     * new UidGenerateException("Clock moved backwards. Refusing for %d seconds", 5)
     * 等价于：
     * new UidGenerateException(String.format("Clock moved backwards. Refusing for %d seconds", 5))
     */
    public UidGenerateException(String msgFormat, Object... args) {
        super(String.format(msgFormat, args));
    }

    /**
     * 带原因的构造函数
     *
     * @param cause 导致此异常的根本原因
     */
    public UidGenerateException(Throwable cause) {
        super(cause);
    }

}
