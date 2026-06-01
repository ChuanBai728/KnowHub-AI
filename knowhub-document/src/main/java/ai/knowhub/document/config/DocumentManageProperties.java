package ai.knowhub.document.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档管理模块的统一配置属性类。
 *
 * 本类使用 Spring Boot 的 ConfigurationProperties 机制，
 * 将 application.yml 中 app.manage 前缀下的所有配置项映射到 Java 对象中。
 *
 * 使用 ConfigurationProperties 的好处：
 * 
 *   类型安全：配置值自动转换为 Java 类型，避免手动解析字符串。
 *   IDE 支持：配合 spring-boot-configuration-processor 可以在 IDE 中获得自动补全和校验。
 *   集中管理：所有相关配置集中在一个类中，便于查阅和维护。
 *   支持默认值：字段初始化值即为默认值，减少配置文件的编写量。
 * 本类采用「分组嵌套」的设计模式，将配置按功能模块划分为多个内部静态类：
 * 
 *   Minio：MinIO 对象存储配置
 *   Kafka：Kafka 消息队列配置
 *   Chunk：文档切块策略配置
 *   StructureParsing：文档结构解析配置
 *   PgVector：PostgreSQL 向量数据库配置
 *   Elasticsearch：Elasticsearch 全文检索配置
 * 设计模式：配置对象模式 —— 将分散的配置项封装为结构化的 Java 对象。
 *
 * Lombok 注解说明：
 * 
 *   Data：自动生成 getter/setter/toString/equals/hashCode 方法。
 * 
 */
@Data
@ConfigurationProperties(prefix = "app.manage")
public class DocumentManageProperties {

    /**
     * MinIO 对象存储配置，用于存储原始文档文件和解析后的文本。
     */
    private Minio minio = new Minio();

    /**
     * Kafka 消息队列配置，用于文档解析和索引构建的异步任务调度。
     */
    private Kafka kafka = new Kafka();

    /**
     * 文档切块（Chunking）策略配置，控制如何将长文档拆分为适合检索的小片段。
     */
    private Chunk chunk = new Chunk();

    /**
     * 文档结构解析配置，控制如何从文档中提取标题层级和段落结构。
     */
    private StructureParsing structureParsing = new StructureParsing();

    /**
     * Milvus 向量数据库配置，保留为可选向量存储方案。
     */
    private Milvus milvus = new Milvus();

    /**
     * PostgreSQL + pgvector 向量数据库配置，用于存储文档切块的语义向量。
     */
    private PgVector pgVector = new PgVector();

    /**
     * Elasticsearch 全文检索配置，用于文档切块的关键词检索和全文搜索。
     */
    private Elasticsearch elasticsearch = new Elasticsearch();

    /**
     * Neo4j graph storage configuration.
     */
    private Neo4j neo4j = new Neo4j();
    /**
     * MinIO 对象存储配置。
     *
     * MinIO 兼容 Amazon S3 API，是一个高性能的分布式对象存储系统。
     * 在本系统中用于存储用户上传的文档原始文件和解析后的纯文本。
     */
    @Data
    public static class Minio {
        /** MinIO 服务器地址，默认本地开发环境 */
        private String endpoint = "http://127.0.0.1:9000";
        /** 访问密钥（Access Key），类似于用户名 */
        private String accessKey = "minioadmin";
        /** 秘密密钥（Secret Key），类似于密码 */
        private String secretKey = "minioadmin";
        /** 存储桶名称，所有文档文件都存储在此桶中 */
        private String bucketName = "knowhub-document";
        /** 原始文档文件的对象前缀（路径），用于在桶内组织文件 */
        private String objectPrefix = "rag/document";
        /** 解析后纯文本文件的对象前缀 */
        private String parsedTextPrefix = "rag/parsed-text";
    }

    /**
     * Kafka 消息队列配置。
     *
     * Kafka 在本系统中承担异步任务调度的角色：
     * 
     *   文档上传后，发送消息到解析 Topic，由消费者异步执行解析。
     *   解析完成后，发送消息到索引构建 Topic，由消费者异步构建检索索引。
     * 
     */
    @Data
    public static class Kafka {
        /** 文档解析任务的 Topic 名称 */
        private String parseTopic = "knowhub-document-parse-route";
        /** 索引构建任务的 Topic 名称 */
        private String indexTopic = "knowhub-document-index-build";
        /** 消费者组 ID，同一组内的消费者共同消费消息（实现负载均衡） */
        private String groupId = "knowhub-document-manage";
        /** 是否自动创建 Topic（开发环境默认开启，生产环境建议关闭并手动创建） */
        private Boolean autoCreateTopics = Boolean.TRUE;
    }

    /**
     * 文档切块（Chunking）策略配置。
     *
     * 切块是 RAG（检索增强生成）的核心步骤之一。将长文档拆分为小片段后，
     * 才能进行向量化和语义检索。不同的切块策略适用于不同类型的文档。
     *
     * 本系统支持三种切块策略：
     * 
     *   <b>递归切块</b>（Recursive）：按分隔符（段落、句子）逐级递归拆分，通用性最强。
     *   <b>语义切块</b>（Semantic）：基于文本语义相似度动态确定切分点，效果最好但较慢。
     *   <b>LLM 切块</b>（LLM）：使用大语言模型判断最佳切分点，效果最好但成本最高。
     * 
     */
    @Data
    public static class Chunk {
        /** 递归切块：每个切块的最大字符数 */
        private Integer recursiveMaxChars = 800;
        /** 递归切块：相邻切块之间的重叠字符数（保证上下文连贯性） */
        private Integer recursiveOverlapChars = 120;
        /** 语义切块：每个切块的最大字符数 */
        private Integer semanticMaxChars = 700;
        /** 语义切块：每个切块的最小字符数（避免产生过小的切块） */
        private Integer semanticMinChars = 240;
        /** 语义切块：语义相似度阈值，低于此值认为是切分点 */
        private Double semanticSimilarityThreshold = 0.18D;
        /** 是否启用 LLM 切块策略（默认关闭，因为成本较高） */
        private Boolean llmEnabled = Boolean.FALSE;
        /** LLM 切块：每次发送给 LLM 的最大字符数 */
        private Integer llmMaxChars = 3500;
        /** 当切块质量较低时，是否推荐使用 LLM 切块策略 */
        private Boolean recommendLlmWhenLowQuality = Boolean.TRUE;
    }

    /**
     * 文档结构解析配置。
     *
     * 结构解析是从文档中提取标题层级、段落归属等结构信息的过程。
     * 例如，识别「第一章」「1.1」「1.1.1」等标题编号，构建文档的树形结构。
     *
     * LLM 消歧功能：当标题格式不规范（如纯数字编号、特殊符号）时，
     * 使用 LLM 辅助判断标题的层级关系。
     */
    @Data
    public static class StructureParsing {

        /** 是否启用 LLM 消歧功能（默认开启） */
        private Boolean llmDisambiguationEnabled = Boolean.TRUE;

        /** 每次 LLM 调用最多处理的歧义信号数量（控制单次调用的输入长度） */
        private Integer maxAmbiguousSignalsPerCall = 8;

        /** LLM 消歧时，上下文窗口的行数（前后各取几行作为判断依据） */
        private Integer contextWindowLines = 2;

        /** 纯文本标题的最大字符数（超过此长度可能是正文而非标题） */
        private Integer maxPlainHeadingChars = 32;

        /** 歧义置信度下限：低于此值认为无法判断，需要人工介入 */
        private Double ambiguityConfidenceFloor = 0.45D;

        /** 歧义置信度上限：高于此值认为判断可靠，无需 LLM 介入 */
        private Double ambiguityConfidenceCeil = 0.80D;
    }

    /**
     * PostgreSQL + pgvector 向量数据库配置。
     *
     * pgvector 是 PostgreSQL 的向量检索扩展。文档切块经过 Embedding 模型
     * 转换为高维向量后，存储在 pgvector 中，支持余弦相似度、L2 距离等向量检索。
     */
    @Data
    public static class PgVector {

        /** 是否启用 pgvector 功能 */
        private Boolean enabled = Boolean.TRUE;

        /** PostgreSQL 服务器主机地址 */
        private String host = "127.0.0.1";

        /** PostgreSQL 服务器端口号（默认 5432） */
        private Integer port = 5432;

        /** 数据库名称 */
        private String database = "knowhub_pgvector";

        /** 数据库 schema 名称（默认 public） */
        private String schema = "public";

        /** 数据库用户名 */
        private String username = "postgres";

        /** 数据库密码 */
        private String password = "postgres";

        /** HikariCP 连接池名称（用于监控和日志标识） */
        private String poolName = "knowhub-manage-pgvector-hikari";

        /** 连接池最大连接数 */
        private Integer maximumPoolSize = 5;

        /** 连接池最小空闲连接数 */
        private Integer minimumIdle = 1;
    }

    @Data
    public static class Milvus {
        private Boolean enabled = Boolean.FALSE;
        private String host = "127.0.0.1";
        private Integer port = 19530;
        private String database = "default";
        private String collectionName = "knowhub_document_embedding";
        private Integer dimension = 1024;
        private String indexType = "HNSW";
        private String metricType = "COSINE";
    }

    /**
     * Elasticsearch 全文检索配置。
     *
     * Elasticsearch 在本系统中用于文档切块的全文检索和关键词搜索。
     * 配合 pgvector 的向量检索，实现「混合检索」模式，提高召回率。
     *
     * 分词器说明：
     * 
     *   <b>ik_max_word</b>：IK 分词器的「最大切分」模式，索引时使用，尽可能多地切分出词语。
     *   <b>ik_smart</b>：IK 分词器的「智能切分」模式，搜索时使用，切分出更有意义的词语。
     *   <b>standard</b>：标准分词器，按空格和标点拆分，对中文效果较差，作为回退方案。
     * 
     */
    @Data
    public static class Elasticsearch {

        /** 是否启用 Elasticsearch 功能 */
        private Boolean enabled = Boolean.TRUE;

        /** Elasticsearch 集群地址列表（支持多节点） */
        private List<String> uris = new ArrayList<>(List.of("http://127.0.0.1:9200"));

        /** Elasticsearch 用户名（开启 X-Pack 安全特性时需要） */
        private String username = "elastic";

        /** Elasticsearch 密码 */
        private String password = "elastic";

        /** 文档切块关键词索引的名称 */
        private String indexName = "knowhub_document_keyword";

        /** 索引时使用的分词器（ik_max_word 最大切分，提高召回率） */
        private String analyzer = "ik_max_word";

        /** 搜索时使用的分词器（ik_smart 智能切分，提高精确率） */
        private String searchAnalyzer = "ik_smart";

        /** 文档导航结构索引的名称（用于存储文档的标题层级结构） */
        private String navigationIndexName = "knowhub_document_navigation";

        /** 知识路由索引的名称（用于存储知识范围和主题的路由信息） */
        private String routeIndexName = "knowhub_knowledge_route";

        /** 连接超时时间（毫秒） */
        private Integer connectTimeoutMillis = 3000;

        /** Socket 读取超时时间（毫秒） */
        private Integer socketTimeoutMillis = 5000;
    }

    @Data
    public static class Neo4j {
        private Boolean enabled = Boolean.FALSE;
        private String uri = "bolt://127.0.0.1:7687";
        private String username = "neo4j";
        private String password = "neo4j";
        private String database = "neo4j";
        private Integer queryTimeoutSeconds = 5;
    }

}
