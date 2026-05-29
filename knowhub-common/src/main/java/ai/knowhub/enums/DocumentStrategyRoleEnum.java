package ai.knowhub.enums;

/**
 * 【文档策略角色枚举】
 *
 * 作用：定义切块策略在处理方案中的角色定位，用于决定策略的优先级和执行顺序。
 *
 * 业务背景：
 *   一个文档处理方案可能包含多种策略，每种策略承担不同角色：
 *   - 主策略：首选的切块方式，通常根据文档特征自动选择。
 *   - 优化策略：在主策略基础上进一步优化（如合并碎片块）。
 *   - 兜底策略：当主策略失败时的备选方案。
 *   - 增强策略：对切块结果做额外增强（如添加元数据、上下文补充）。
 *
 * 使用示例：
 *   DocumentStrategyRoleEnum role = DocumentStrategyRoleEnum.getRc(1);  // 返回 PRIMARY
 */
public enum DocumentStrategyRoleEnum {

    /** 主策略：首选的切块方式 */
    PRIMARY(1, "主策略"),

    /** 优化策略：对主策略结果进行优化 */
    OPTIMIZE(2, "优化策略"),

    /** 兜底策略：主策略失败时的备选方案 */
    FALLBACK(3, "兜底策略"),

    /** 增强策略：对切块结果做额外增强处理 */
    ENHANCE(4, "增强策略");

    /**
     * 数字编码
     */
    private final Integer code;

    /**
     * 中文描述
     */
    private final String msg;

    /**
     * 枚举构造方法
     *
     * @param code 数字编码
     * @param msg  中文描述
     */
    DocumentStrategyRoleEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 获取数字编码
     * @return 编码值
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 获取中文描述
     * @return 描述文本，如果 msg 为 null 则返回空字符串
     */
    public String getMsg() {
        return msg == null ? "" : msg;
    }

    /**
     * 根据数字编码查找对应的枚举实例
     *
     * @param code 数字编码
     * @return 对应的枚举实例，未找到则返回 null
     */
    public static DocumentStrategyRoleEnum getRc(Integer code) {
        for (DocumentStrategyRoleEnum item : DocumentStrategyRoleEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
