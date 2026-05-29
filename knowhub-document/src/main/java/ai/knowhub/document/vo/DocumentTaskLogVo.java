package ai.knowhub.document.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 文档任务日志返回值对象（Document Task Log Vo）
 *
 * 【类的作用】
 * 用于展示文档处理任务中的单条日志记录，包括日志的阶段、事件类型、
 * 级别、内容和详细信息。
 *
 * 【在架构中的角色】
 * 属于文档管理模块的返回值层，是 DocumentTaskLogQueryVo 的组成部分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTaskLogVo {

    /**
     * 日志ID（ID）
     * 日志记录的唯一标识
     */
    private Long id;

    /**
     * 阶段类型编码（Stage Type）
     * 日志所属的处理阶段编码
     */
    private Integer stageType;

    /**
     * 阶段类型名称（Stage Type Name）
     * 处理阶段的可读名称
     */
    private String stageTypeName;

    /**
     * 事件类型编码（Event Type）
     * 日志事件的类型编码
     */
    private Integer eventType;

    /**
     * 事件类型名称（Event Type Name）
     * 事件类型的可读名称
     */
    private String eventTypeName;

    /**
     * 日志级别编码（Log Level）
     * 日志的级别编码（如 INFO、WARN、ERROR）
     */
    private Integer logLevel;

    /**
     * 日志级别名称（Log Level Name）
     * 日志级别的可读名称
     */
    private String logLevelName;

    /**
     * 日志内容（Content）
     * 日志的主要描述信息
     */
    private String content;

    /**
     * 详细信息 JSON（Detail JSON）
     * 日志的详细结构化数据，以 JSON 格式存储
     */
    private String detailJson;

    /**
     * 创建时间（Create Time）
     * 日志记录的创建时间
     */
    private Date createTime;
}
