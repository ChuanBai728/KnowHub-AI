package ai.knowhub.prompt;

import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板服务。
 *
 * 该类负责加载、缓存和渲染 Prompt 模板。在 AI Agent 系统中，
 * Prompt（提示词）的质量直接影响 LLM 的回答质量。
 * 该服务使用 StringTemplate（ST4）模板引擎，支持变量替换和条件逻辑。
 *
 * 在架构中的角色
 * 属于"Prompt 管理层"，是 Prompt 工程化的核心基础设施。
 * 所有需要动态生成 Prompt 的业务代码都通过该服务加载模板并填充变量，
 * 而非在 Java 代码中硬编码 Prompt 文本。
 *
 * 设计模式
 * 
 *   <b>模板方法模式</b> - render() 方法定义了"加载模板 -> 填充变量 -> 返回结果"的标准流程
 *   <b>缓存模式</b> - 使用 ConcurrentHashMap 缓存已加载的模板，避免重复读取文件
 *   <b>策略模式</b> - 通过 StTemplateRenderer 策略进行模板渲染，可替换为其他渲染引擎
 * 模板文件约定
 * 
 *   模板文件存放在 classpath:prompt/ 目录下
 *   模板文件扩展名为 .st（StringTemplate）
 *   模板变量使用 <variableName> 语法（尖括号分隔符，非默认的 $ 符号）
 *   模板名称通过 PromptTemplateNames 常量类引用
 * Lombok 注解说明
 * 
 *   @Component - Spring 组件注解，将该类注册到 IoC 容器中，可通过依赖注入使用
 * 
 */
@Component
public class PromptTemplateService {

    /** Prompt 模板文件在 classpath 中的目录路径 */
    private static final String PROMPT_DIR = "prompt/";

    /** StringTemplate 模板文件的扩展名 */
    private static final String TEMPLATE_SUFFIX = ".st";

    /**
     * Spring 资源加载器，用于从 classpath 中读取模板文件。
     */
    private final ResourceLoader resourceLoader;

    /**
     * StringTemplate 模板渲染器。
     *
     * 使用 ST4（StringTemplate 4）引擎进行模板渲染。
     * 配置为使用尖括号 <> 作为变量分隔符（而非默认的 $），
     * 并设置验证模式为 THROW（变量缺失时抛出异常）。
     */
    private final StTemplateRenderer templateRenderer;

    /**
     * 模板内容缓存。
     *
     * 使用 ConcurrentHashMap 实现线程安全的模板缓存。
     * 模板文件内容在首次加载后会被缓存，后续请求直接从缓存读取，
     * 避免每次渲染都进行文件 I/O 操作。
     */
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 构造方法，初始化资源加载器和模板渲染器。
     *
     * @param resourceLoader Spring 资源加载器，由 Spring 自动注入
     */
    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        // 构建 StringTemplate 渲染器，使用尖括号作为变量分隔符
        this.templateRenderer = StTemplateRenderer.builder()
            .startDelimiterToken('<')    // 变量开始分隔符：<
            .endDelimiterToken('>')      // 变量结束分隔符：>
            .validationMode(ValidationMode.THROW)  // 变量缺失时抛出异常
            .build();
    }

    /**
     * 渲染 Prompt 模板。
     *
     * 根据模板名称加载模板文件（带缓存），并将变量填充到模板中，
     * 返回渲染后的完整 Prompt 文本。
     *
     * 使用示例：
     * {@code
     * String prompt = promptTemplateService.render(
     *     PromptTemplateNames.RAG_ANSWER_SYSTEM,
     *     Map.of("context", retrievedDocs, "question", userQuestion)
     * );
     * }
     *
     * @param templateName 模板名称（对应 PromptTemplateNames 中的常量），
     *                     支持多种格式：纯名称、带 "classpath:" 前缀、带目录路径等
     * @param variables    模板变量映射，key 为变量名，value 为变量值
     * @return 渲染后的 Prompt 文本（已去除首尾空白）
     * @throws IllegalArgumentException 如果模板文件不存在
     * @throws IllegalStateException    如果模板文件读取失败
     */
    public String render(String templateName, Map<String, ?> variables) {
        // 标准化模板路径（添加目录前缀和文件扩展名）
        String templatePath = normalizeTemplatePath(templateName);
        // 从缓存加载模板，如果缓存未命中则从文件系统加载
        String template = templateCache.computeIfAbsent(templatePath, this::loadTemplate);
        // 使用 ST4 引擎渲染模板，填充变量并去除首尾空白
        return templateRenderer.apply(template, normalizeVariables(variables)).trim();
    }

    /**
     * 标准化变量映射。
     *
     * 将变量值中的 null 替换为空字符串，避免模板渲染时出现 NullPointerException。
     * 使用 LinkedHashMap 保持变量的插入顺序。
     *
     * @param variables 原始变量映射
     * @return 标准化后的变量映射（null 值已被替换为空字符串）
     */
    private Map<String, Object> normalizeVariables(Map<String, ?> variables) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (variables == null || variables.isEmpty()) {
            return normalized;
        }
        variables.forEach((key, value) -> normalized.put(key, value == null ? "" : value));
        return normalized;
    }

    /**
     * 标准化模板路径。
     *
     * 对用户传入的模板名称进行规范化处理：
     * 
     *   去除 "classpath:" 前缀（如果有）
     *   去除开头的 "/" 字符
     *   添加 "prompt/" 目录前缀（如果没有）
     *   添加 ".st" 扩展名（如果没有）
     * 
     * 这样用户可以用多种形式引用模板，例如：
     * 
     *   "rag-answer-system" -> "prompt/rag-answer-system.st"
     *   "prompt/rag-answer-system.st" -> "prompt/rag-answer-system.st"
     *   "classpath:prompt/rag-answer-system.st" -> "prompt/rag-answer-system.st"
     * @param templateName 原始模板名称
     * @return 标准化后的模板路径
     */
    private String normalizeTemplatePath(String templateName) {
        String normalized = templateName == null ? "" : templateName.trim();
        if (normalized.startsWith("classpath:")) {
            normalized = normalized.substring("classpath:".length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith(PROMPT_DIR)) {
            normalized = PROMPT_DIR + normalized;
        }
        if (!normalized.endsWith(TEMPLATE_SUFFIX)) {
            normalized = normalized + TEMPLATE_SUFFIX;
        }
        return normalized;
    }

    /**
     * 从 classpath 加载模板文件内容。
     *
     * 使用 Spring 的 ResourceLoader 从 classpath 中读取模板文件，
     * 以 UTF-8 编码解析文件内容并返回。
     *
     * @param templatePath 模板文件的 classpath 路径（如 "prompt/rag-answer-system.st"）
     * @return 模板文件的文本内容
     * @throws IllegalArgumentException 如果模板文件不存在
     * @throws IllegalStateException    如果文件读取过程中发生异常
     */
    private String loadTemplate(String templatePath) {
        Resource resource = resourceLoader.getResource("classpath:" + templatePath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Prompt 模板不存在: classpath:" + templatePath);
        }
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
        catch (Exception exception) {
            throw new IllegalStateException("读取 Prompt 模板失败: classpath:" + templatePath, exception);
        }
    }
}
