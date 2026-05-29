package ai.knowhub.document.support;

/**
 * 文档 PgVector 常量（Document PgVector Constants）
 *
 * 【类的作用】
 * 定义文档向量存储相关的常量，主要用于 PostgreSQL 的 pgvector 扩展。
 * pgvector 是 PostgreSQL 的向量搜索扩展，用于存储和检索文档的向量嵌入。
 *
 * 【在架构中的角色】
 * 属于 RAG 流程中的向量存储层。文档切片经过 Embedding 模型转换为向量后，
 * 存储在 PostgreSQL 的 pgvector 表中。本类定义了该表的名称常量。
 *
 * 【设计模式】
 * 工具类模式（Utility Class）：私有构造器防止实例化。
 */
public final class DocumentPgVectorConstants {

    /**
     * 向量嵌入表名称
     * PostgreSQL 中存储文档向量嵌入的表的全限定名称（schema.table）
     * 格式为 "public.knowhub_document_embedding"
     */
    public static final String EMBEDDING_TABLE_NAME = "public.knowhub_document_embedding";

    /**
     * 私有构造器，防止实例化
     */
    private DocumentPgVectorConstants() {
    }
}
