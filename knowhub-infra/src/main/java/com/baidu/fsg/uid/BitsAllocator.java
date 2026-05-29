package com.baidu.fsg.uid;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.springframework.util.Assert;

/**
 * 位分配器 - UID（唯一ID）生成的核心组件
 *
 * 【类的作用】
 * 这个类负责将一个64位的long型数字拆分成不同的部分，每一部分存储不同的信息。
 * 就像一个蛋糕被切成几块，每块的大小不同，用途也不同。
 *
 * 【64位ID的结构】（从高位到低位）
 * ┌─────────┬──────────────────┬──────────────┬──────────────┐
 * │ 符号位  │   时间戳部分      │  工作机器ID  │   序列号     │
 * │ (1位)   │  (timestampBits) │ (workerIdBits)│ (sequenceBits)│
 * └─────────┴──────────────────┴──────────────┴──────────────┘
 *
 * 【设计模式】
 * 使用了"值对象"模式，一旦构造完成，各字段值不可变（final），保证线程安全。
 *
 * 【关键概念 - 位运算】
 * - 左移(<<)：将数字的二进制表示向左移动指定位数，右边补0
 * - 右移(>>)：将数字的二进制表示向右移动指定位数
 * - 按位或(|)：两个位只要有一个为1，结果就为1
 * - 按位与(&)：两个位都为1时，结果才为1
 * - 取反(~)：0变1，1变0
 */
public class BitsAllocator {

    /**
     * 总位数 = 64位，这是Java中long类型的固定长度
     * 1 << 6 = 1 * 2^6 = 64，使用位移运算比直接写64更能体现"位"的概念
     */
    public static final int TOTAL_BITS = 1 << 6;

    /**
     * 符号位：占1位，固定为0表示正数
     * Java中long类型最高位是符号位，0表示正数，1表示负数
     * 我们生成的ID始终是正数，所以符号位固定为1位且值为0
     */
    private int signBits = 1;

    /**
     * 时间戳部分占用的位数
     * 位数越多，能表示的时间范围越大
     * 例如：28位可以表示约8.5年（2^28秒）
     */
    private final int timestampBits;

    /**
     * 工作机器ID占用的位数
     * 用于标识不同的服务器/节点，确保不同机器生成的ID不会重复
     * 例如：22位可以表示约419万个不同的工作节点
     */
    private final int workerIdBits;

    /**
     * 序列号占用的位数
     * 在同一秒内，每生成一个ID序列号就加1
     * 当序列号用完时，需要等待下一秒再生成
     * 例如：13位表示同一秒内最多生成8192个ID（2^13）
     */
    private final int sequenceBits;

    /**
     * 时间戳部分能表示的最大值
     * 计算方式：~(-1L << timestampBits)
     * 例如 timestampBits=28 时，maxDeltaSeconds = 2^28 - 1 = 268435455
     *
     * 【位运算原理】
     * -1L 的二进制是 1111...1111（64个1）
     * -1L << 28 = 1111...1111 000...000（右边28个0）
     * ~(-1L << 28) = 0000...0000 111...111（右边28个1）= 2^28 - 1
     */
    private final long maxDeltaSeconds;

    /**
     * 工作机器ID的最大值
     * 例如 workerIdBits=22 时，maxWorkerId = 2^22 - 1 = 4194303
     */
    private final long maxWorkerId;

    /**
     * 序列号的最大值
     * 例如 sequenceBits=13 时，maxSequence = 2^13 - 1 = 8191
     * 序列号从0开始，所以每秒最多生成 maxSequence+1 个ID
     */
    private final long maxSequence;

    /**
     * 时间戳部分需要左移的位数
     * 等于 workerIdBits + sequenceBits
     * 因为时间戳在ID的高位部分，需要左移让出低位给workerId和sequence
     */
    private final int timestampShift;

    /**
     * 工作机器ID需要左移的位数
     * 等于 sequenceBits
     * 因为workerId在时间戳和序列号之间
     */
    private final int workerIdShift;

    /**
     * 构造函数 - 初始化位分配器
     *
     * @param timestampBits 时间戳部分的位数
     * @param workerIdBits  工作机器ID的位数
     * @param sequenceBits  序列号的位数
     *
     * 【重要校验】
     * 所有部分的位数之和必须等于64位，否则无法组成一个完整的long型ID
     */
    public BitsAllocator(int timestampBits, int workerIdBits, int sequenceBits) {

        // 计算所有部分的位数总和
        int allocateTotalBits = signBits + timestampBits + workerIdBits + sequenceBits;
        // 校验：位数总和必须等于64位
        Assert.isTrue(allocateTotalBits == TOTAL_BITS, "allocate not enough 64 bits");

        this.timestampBits = timestampBits;
        this.workerIdBits = workerIdBits;
        this.sequenceBits = sequenceBits;

        // 计算各部分能表示的最大值
        // 原理：n位能表示的最大值是 2^n - 1
        // 位运算技巧：~(-1L << n) 得到低n位全为1的数字，即 2^n - 1
        this.maxDeltaSeconds = ~(-1L << timestampBits);
        this.maxWorkerId = ~(-1L << workerIdBits);
        this.maxSequence = ~(-1L << sequenceBits);

        // 计算各部分的左移位数
        // 时间戳在最高位（符号位之后），所以要左移到最左边
        // workerId在中间，sequence在最低位
        this.timestampShift = workerIdBits + sequenceBits;
        this.workerIdShift = sequenceBits;
    }

    /**
     * 将三个部分组合成一个完整的UID
     *
     * 【组合原理】（以默认配置为例：28+22+13=63位 + 1位符号位=64位）
     *
     * 最终ID的二进制结构：
     * [0][时间戳28位][workerId22位][序列号13位]
     *
     * @param deltaSeconds 距离基准时间的秒数差（时间戳部分）
     * @param workerId     工作机器ID
     * @param sequence     序列号
     * @return 组合后的唯一ID
     *
     * 【位运算步骤】
     * 1. 将deltaSeconds左移到高位
     * 2. 将workerId左移到中位
     * 3. 序列号本身就在低位，不需要移动
     * 4. 用按位或(|)将三部分合并
     */
    public long allocate(long deltaSeconds, long workerId, long sequence) {
        return (deltaSeconds << timestampShift) | (workerId << workerIdShift) | sequence;
    }

    /**
     * 获取符号位的位数（固定为1）
     */
    public int getSignBits() {
        return signBits;
    }

    /**
     * 获取时间戳部分的位数
     */
    public int getTimestampBits() {
        return timestampBits;
    }

    /**
     * 获取工作机器ID的位数
     */
    public int getWorkerIdBits() {
        return workerIdBits;
    }

    /**
     * 获取序列号的位数
     */
    public int getSequenceBits() {
        return sequenceBits;
    }

    /**
     * 获取时间戳部分能表示的最大值（即最大秒数差）
     */
    public long getMaxDeltaSeconds() {
        return maxDeltaSeconds;
    }

    /**
     * 获取工作机器ID的最大值
     */
    public long getMaxWorkerId() {
        return maxWorkerId;
    }

    /**
     * 获取序列号的最大值
     */
    public long getMaxSequence() {
        return maxSequence;
    }

    /**
     * 获取时间戳的左移位数
     */
    public int getTimestampShift() {
        return timestampShift;
    }

    /**
     * 获取工作机器ID的左移位数
     */
    public int getWorkerIdShift() {
        return workerIdShift;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
    }

}
