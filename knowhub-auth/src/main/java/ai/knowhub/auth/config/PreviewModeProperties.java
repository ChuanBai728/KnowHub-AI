package ai.knowhub.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线上演示只读模式的配置属性类。
 *
 * 当系统用于线上演示（如产品展示、客户试用）时，可以通过配置开启"只读模式"。
 * 在此模式下，所有可能修改数据的接口（如发送消息、上传文档、修改知识库等）
 * 都会被 ai.knowhub.auth.support.PreviewModeInterceptor 拦截并拒绝。
 *
 * 配置示例（application.yml）
 * 
 * app:
 *   preview-mode:
 *     enabled: true
 *     message: 当前环境为只读展示模式，仅开放浏览与检索能力
 * 使用场景
 * 
 *   产品演示环境：让客户浏览功能但不能修改数据
 *   培训环境：学员可以查看但不能操作
 *   线上公测：开放只读体验，防止数据被随意修改
 * 
 */
@Data
@ConfigurationProperties(prefix = "app.preview-mode")
public class PreviewModeProperties {

    /**
     * 是否开启只读展示模式。
     * 默认关闭（false）。设为 true 时，写操作接口将被拦截。
     */
    private Boolean enabled = Boolean.FALSE;

    /**
     * 只读模式下的提示语。
     * 当用户的写操作被拦截时，会返回此消息作为错误提示。
     */
    private String message = "当前环境为只读展示模式，仅开放浏览与检索能力";
}
