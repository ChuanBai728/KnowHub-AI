package ai.knowhub.chat.rag.service;

import ai.knowhub.document.model.graph.GraphSection;

/**
 * 【章节匹配分数 — 本地章节候选的打分结果】
 *
 * 这个类是一个简单的值对象，用于在 {@link DocumentQuestionRouter} 的
 * 本地结构匹配中，记录每个章节节点与用户问题短语之间的匹配分数。
 *
 * 使用场景：
 * 当用户问题中包含章节标题（如"部署流程"），路由器会遍历文档的所有章节，
 * 计算每个章节与问题短语的匹配分数，选择分数最高的章节作为定位目标。
 *
 * 打分规则（在 DocumentQuestionRouter.scoreSection 方法中）：
 * - 路径匹配：100 + 短语长度（最高优先级）
 * - 标题匹配：90 + 短语长度
 * - 锚文本匹配：80 + 短语长度
 * - 内容匹配：45 + min(短语长度, 20)（最低优先级）
 *
 * 设计模式：值对象模式（Value Object），只有 getter 方法，无 setter。
 *
 * 在 RAG 流水线中的位置：
 * 用户问题 -> 提取短语 -> 遍历章节打分 -> 【本类：分数记录】-> 选择最高分章节
 */
final class SectionScore {

    /**
     * 当前参与打分的图章节节点。
     * 包含章节的标题、路径、锚文本、内容等信息。
     */
    private final GraphSection section;

    /**
     * 当前章节和用户问题短语之间的匹配分数。
     * 分数越高，说明章节与问题越相关。
     */
    private final double score;

    /**
     * 构造函数。
     *
     * @param section 图章节节点
     * @param score   匹配分数
     */
    SectionScore(GraphSection section, double score) {
        this.section = section;
        this.score = score;
    }

    /** 获取图章节节点 */
    GraphSection section() {
        return section;
    }

    /** 获取匹配分数 */
    double score() {
        return score;
    }
}
