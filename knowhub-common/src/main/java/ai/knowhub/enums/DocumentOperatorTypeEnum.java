package ai.knowhub.enums;

/**
 * 【文档操作者类型枚举】
 *
 * 作用：标识执行文档操作的角色类型，用于审计日志和权限控制。
 *
 * 业务背景：
 *   文档的创建、修改、删除等操作可能由不同角色执行：
 *   - 系统自动执行（如定时任务触发的文档更新）
 *   - 普通用户手动操作（如上传文档）
 *   - 管理员执行的操作（如强制重建索引）
 *
 * 使用示例：
 *   DocumentOperatorTypeEnum type = DocumentOperatorTypeEnum.getRc(2);  // 返回 USER
 */
public enum DocumentOperatorTypeEnum {

    /** 系统自动执行的操作（如定时任务、自动同步等） */
    SYSTEM(1, "系统"),

    /** 普通用户执行的操作（如上传文档、发起检索等） */
    USER(2, "用户"),

    /** 管理员执行的操作（如强制重建索引、批量删除等） */
    ADMIN(3, "管理员");

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
    DocumentOperatorTypeEnum(Integer code, String msg) {
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
    public static DocumentOperatorTypeEnum getRc(Integer code) {
        for (DocumentOperatorTypeEnum item : DocumentOperatorTypeEnum.values()) {
            if (item.code.intValue() == code.intValue()) {
                return item;
            }
        }
        return null;
    }
}
