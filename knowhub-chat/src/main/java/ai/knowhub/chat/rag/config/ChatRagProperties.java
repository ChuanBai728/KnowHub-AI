package ai.knowhub.chat.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG（检索增强生成）流水线的配置属性类。
 *
 * 本类通过 @ConfigurationProperties(prefix = "app.chat.rag") 绑定 application.yml 中
 * 以 app.chat.rag 开头的配置项，让整个 RAG 流水线的行为可以通过配置文件灵活调整，无需改代码。
 *
 * RAG 流水线关键阶段与对应配置
 * 
 *   <b>查询改写（Rewrite）</b>：将用户口语化、省略上下文的问题改写成更完整的检索查询。
 *       对应配置：rewriteEnabled、rewriteHistoryTurns、rewriteOptions
 *   <b>子问题拆分</b>：复杂问题拆成多个子问题分别检索。
 *       对应配置：maxSubQuestions
 *   <b>多通道检索</b>：向量检索（语义相似）+ 关键词检索（精确匹配）并行执行。
 *       对应配置：vectorTopK、keywordTopK、keywordChannelEnabled
 *   <b>结果融合与过滤</b>：RRF（Reciprocal Rank Fusion）融合两个通道的结果，过滤低分文档。
 *       对应配置：candidateTopK、minVectorSimilarity、keywordRelativeScoreFloor
 *   <b>Rerank 重排序</b>：用交叉编码器模型对候选文档重新打分排序。
 *       对应配置：rerankEnabled、rerank.*
 *   <b>Prompt 预算组装</b>：将证据文本压缩进 Prompt 预算，防止上下文超限。
 *       对应配置：totalEvidenceMaxChars、perSubQuestionEvidenceMaxChars、parentEvidenceMaxChars
 *   <b>历史上下文管理</b>：会话历史的摘要压缩和最近对话保留。
 *       对应配置：historySummary.*
 * @see ChatRagExecutorConfiguration 线程池配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.chat.rag")
public class ChatRagProperties {

    /**
     * RAG 流水线总开关。
     * 设为 false 时整个 RAG 检索链路不生效，直接走普通对话。
     */
    private boolean enabled = true;

    /**
     * 查询改写开关。
     * 开启后，用户原始问题会先经过大模型改写，补充省略的上下文信息，
     * 让检索更准确。例如 "它的价格呢" 会被改写成 "XXX产品的价格是多少"。
     */
    private boolean rewriteEnabled = true;

    /**
     * 查询改写时参考的历史对话轮数。
     * 改写模型需要看到最近 N 轮对话才能理解"它"、"那个"等指代词指的是什么。
     * 默认 4 轮，即看最近 4 轮用户和助手的对话。
     */
    private int rewriteHistoryTurns = 4;

    /**
     * 查询改写的模型调用参数（温度、topP、是否开启思考模式等）。
     */
    private RewriteOptionsProperties rewriteOptions = new RewriteOptionsProperties();

    /**
     * 单次检索允许的最大子问题数量。
     * 复杂问题会被拆成多个子问题分别检索，此值限制子问题上限，防止检索次数过多导致延迟过高。
     * 默认最多 4 个子问题。
     */
    private int maxSubQuestions = 4;

    /**
     * 向量检索返回的最大文档数（Top-K）。
     * 每个子问题在向量通道检索时，最多取语义最相似的 K 个文档片段。
     * 默认返回 8 个。
     */
    private int vectorTopK = 8;

    /**
     * 关键词检索返回的最大文档数（Top-K）。
     * 每个子问题在关键词通道检索时，最多取关键词匹配得分最高的 K 个文档片段。
     * 默认返回 8 个。
     */
    private int keywordTopK = 8;

    /**
     * RRF 融合后的候选文档数。
     * 向量和关键词两个通道的结果经过 RRF（Reciprocal Rank Fusion）融合后，
     * 只保留得分最高的前 K 个候选文档进入后续处理。
     * 默认保留 10 个候选。
     */
    private int candidateTopK = 10;

    /**
     * 最终送入大模型的文档数。
     * 经过父块提升、rerank 等处理后，最终只保留最相关的 K 个文档片段放入 Prompt。
     * 默认保留 5 个。
     */
    private int finalTopK = 5;

    /**
     * 向量检索的最低相似度阈值。
     * 向量检索返回的文档必须与查询的余弦相似度 >= 此值才会被采纳，
     * 低于此值的文档被认为是"不够相关"而被过滤掉。
     * 默认 0.45。
     */
    private double minVectorSimilarity = 0.45D;

    /**
     * 关键词检索的相对得分下限。
     * 关键词检索结果中，得分低于最高分 * 此比例的文档会被过滤。
     * 例如最高分 100，此值为 0.35，则得分 < 35 的文档会被丢弃。
     * 默认 0.35。
     */
    private double keywordRelativeScoreFloor = 0.35D;

    /**
     * 父块证据的最大字符数。
     * 当检索命中的是子块（chunk）时，系统会尝试提升到其父块以提供更完整的上下文，
     * 此值限制父块文本的最大长度，防止过长。
     * 默认 2200 字符。
     */
    private int parentEvidenceMaxChars = 2200;

    /**
     * 规划历史上下文的最大字符数。
     * 在组装 Prompt 时，历史对话规划信息（对话目标、已知事实等）的最大字符预算。
     * 默认 1600 字符。
     */
    private int planningHistoryMaxChars = 1600;

    /**
     * 回答历史上下文的最大字符数。
     * 在组装 Prompt 时，之前回答的历史信息的最大字符预算。
     * 默认 1000 字符。
     */
    private int answerHistoryMaxChars = 1000;

    /**
     * 所有证据的总字符预算。
     * 所有子问题的证据文本加起来不能超过此值，超出部分会被截断或省略。
     * 这是为了防止 Prompt 中证据部分占用过多 token，留给模型推理的空间不够。
     * 默认 5200 字符。
     */
    private int totalEvidenceMaxChars = 5200;

    /**
     * 单个子问题的证据字符预算。
     * 每个子问题分配到的证据文本最大字符数，防止某个子问题占用过多预算。
     * 默认 2200 字符。
     */
    private int perSubQuestionEvidenceMaxChars = 2200;

    /**
     * 单个检索通道的超时时间（毫秒）。
     * 向量检索或关键词检索单次调用超过此时间会被中断，避免一个通道卡住拖慢整个流程。
     * 默认 5000 毫秒（5 秒）。
     */
    private long channelTimeoutMs = 5000L;

    /**
     * 单个子问题的总超时时间（毫秒）。
     * 一个子问题从开始检索到拿到结果的总时间上限，包含所有通道的检索时间。
     * 默认 12000 毫秒（12 秒）。
     */
    private long subQuestionTimeoutMs = 12000L;

    /**
     * 关键词检索通道开关。
     * 设为 false 时只使用向量检索，不走关键词检索通道。
     * 对于某些场景（如纯语义搜索），关闭关键词通道可以减少延迟。
     * 默认开启。
     */
    private boolean keywordChannelEnabled = true;

    /**
     * Rerank 重排序开关。
     * 开启后，融合后的候选文档会经过交叉编码器模型重新打分排序，
     * 通常能显著提升最终选出文档的质量。
     * 需要配置 rerank.* 相关参数。
     * 默认关闭。
     */
    private boolean rerankEnabled = true;

    /**
     * 无证据时的兜底回复话术。
     * 当检索引擎没有找到足够相关的证据时，直接返回此话术，
     * 避免大模型在没有依据的情况下"编造"答案（幻觉）。
     */
    private String noEvidenceReply = "当前没有从已接入文档中检索到足够证据，暂时不能给出可靠结论。";

    /**
     * RAG 回答的系统提示词。
     * 可以在此配置全局的系统提示词，告诉模型如何基于证据回答问题。
     * 为空时使用默认提示词。
     */
    private String answerSystemPrompt = "";

    /**
     * 会话历史摘要压缩配置。
     * 控制如何将长对话历史压缩成摘要，释放 Prompt 上下文空间。
     */
    private HistorySummaryProperties historySummary = new HistorySummaryProperties();

    /**
     * Rerank 重排序服务配置。
     * 配置外部 rerank API 的地址、密钥、模型名等参数。
     */
    private RerankProperties rerank = new RerankProperties();

    /**
     * 会话历史摘要压缩的配置属性。
     *
     * 当对话轮数较多时，不可能把所有历史都塞进 Prompt。
     * 本配置控制如何保留最近几轮原文 + 将更早的历史压缩成摘要。
     */
    @Data
    public static class HistorySummaryProperties {

        /**
         * 历史摘要功能开关。
         * 设为 false 时不做历史压缩，直接截断。
         */
        private boolean enabled = true;

        /**
         * 保留最近 N 轮对话的原文。
         * 最近的对话通常与当前问题最相关，保留原文比摘要更有用。
         * 默认保留最近 4 轮。
         */
        private int keepRecentTurns = 4;

        /**
         * 每次压缩的对话轮数。
         * 每积累这么多轮对话就触发一次摘要压缩。
         * 默认每 6 轮压缩一次。
         */
        private int compressionBatchTurns = 6;

        /**
         * 最近对话原文的最大字符数。
         * 即使只保留最近几轮，原文也不能太长，超过此值会被截断。
         * 默认 2200 字符。
         */
        private int recentTranscriptMaxChars = 2200;

        /**
         * 历史摘要的最大字符数。
         * 压缩后的摘要文本长度上限。
         * 默认 1400 字符。
         */
        private int summaryMaxChars = 1400;
    }

    /**
     * 查询改写的模型调用参数配置。
     *
     * 查询改写需要模型输出稳定、确定性强的结果，所以通常使用低温度（temperature）、
     * 低 topP 参数，并关闭思考模式以加快响应速度。
     */
    @Data
    public static class RewriteOptionsProperties {

        /**
         * 改写参数子开关。
         * 设为 false 时不使用这些参数，走默认值。
         */
        private boolean enabled = true;

        /**
         * 模型温度参数（0.0 ~ 2.0）。
         * 值越低输出越确定、越稳定。查询改写需要确定性，所以用 0.1 的低温度。
         */
        private Double temperature = 0.1D;

        /**
         * 核采样参数 topP（0.0 ~ 1.0）。
         * 控制模型从概率最高的 token 中采样的范围。0.3 表示只从概率最高的 30% token 中选择。
         */
        private Double topP = 0.3D;

        /**
         * 是否开启模型的思考模式（Chain of Thought）。
         * 查询改写不需要复杂推理，关闭思考模式可以加快响应。
         */
        private Boolean thinking = Boolean.FALSE;

        /**
         * 改写使用的模型名称。
         * 为空时使用全局默认模型。可配置为更轻量的模型以加快改写速度，
         * 例如 "qwen-turbo"（走 DashScope 百炼 API，与 embedding/rerank 同一 API Key）。
         */
        private String model;
    }

    /**
     * Rerank 重排序服务的配置属性。
     *
     * Rerank 是一种"精排"技术：先用向量检索或关键词检索"粗排"拿到候选文档，
     * 再用专门的交叉编码器模型对每个文档与查询的相关性重新打分，选出最相关的文档。
     * 这比单纯靠向量相似度排序更准确，因为交叉编码器会同时看查询和文档的完整内容。
     *
     * 本项目使用 SiliconFlow 提供的 rerank API，默认模型为 BAAI/bge-reranker-v2-m3。
     */
    @Data
    public static class RerankProperties {

        /**
         * Rerank 功能子开关。
         * 设为 false 时不调用 rerank API，直接用融合后的排序。
         */
        private boolean enabled = false;

        /**
         * Rerank API 的请求地址。
         * 默认使用 SiliconFlow 的 rerank 端点。
         */
        private String url = "https://api.siliconflow.cn/v1/rerank";

        /**
         * Rerank API 的访问密钥。
         * 需要在配置文件或环境变量中设置。
         */
        private String apiKey;

        /**
         * Rerank 使用的模型名称。
         * BAAI/bge-reranker-v2-m3 是一个多语言 rerank 模型，支持中英文。
         */
        private String model = "BAAI/bge-reranker-v2-m3";

        /**
         * Rerank 后保留的文档数（Top-N）。
         * rerank 模型会对所有候选文档重新打分，最终只取得分最高的前 N 个。
         * 默认保留 5 个。
         */
        private int topN = 5;

        /**
         * 连接超时时间（毫秒）。
         * 建立与 rerank API 服务器的连接超过此时间会报超时。
         * 默认 3000 毫秒（3 秒）。
         */
        private int connectTimeoutMs = 3000;

        /**
         * 读取超时时间（毫秒）。
         * 从 rerank API 读取响应超过此时间会报超时。
         * 默认 6000 毫秒（6 秒）。
         */
        private int readTimeoutMs = 6000;
    }
}
