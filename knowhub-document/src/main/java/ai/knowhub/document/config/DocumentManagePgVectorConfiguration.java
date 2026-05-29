package ai.knowhub.document.config;

import cn.hutool.core.util.StrUtil;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 文档管理模块的 PostgreSQL + pgvector 向量数据库配置类。
 *
 * pgvector 是 PostgreSQL 的向量检索扩展，支持存储和查询高维向量。
 * 在本系统中，pgvector 用于存储文档切块的语义向量（Embedding），
 * 配合 Elasticsearch 的关键词检索，实现「向量 + 关键词」的混合检索模式。
 *
 * 为什么用 pgvector 而不是专用向量数据库（如 Milvus）？
 * 
 *   减少运维成本：不需要额外部署向量数据库，复用已有的 PostgreSQL。
 *   事务一致性：可以与业务数据在同一个数据库中，利用 PostgreSQL 的事务机制。
 *   对于中小规模数据（百万级以内），pgvector 的性能完全够用。
 * 本类创建了独立的数据源（HikariDataSource）和 JdbcTemplate，
 * 使用 Qualifier 限定符与主数据源区分开来。
 *
 * 条件装配：仅在 app.manage.pgvector.enabled=true 时生效（默认为 true）。
 *
 * 设计模式：配置类模式 + 资源管理模式（DisposableBean 确保连接池关闭）。
 */
@Configuration
@EnableConfigurationProperties(DocumentManageProperties.class)
@ConditionalOnProperty(prefix = "app.manage.pgvector", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentManagePgVectorConfiguration {

    /**
     * 创建 pgvector 数据源的 JDBC 支持对象。
     *
     * 使用 HikariDataSource 作为连接池，HikariCP 是目前最快的 JDBC 连接池。
     * JDBC URL 中的 stringtype=unspecified 参数是 pgvector 扩展要求的，
     * 允许向量类型参数以未指定类型的方式传递。
     *
     * @param properties 文档管理配置属性（包含 PG 的 host、port、database 等）
     * @return JDBC 支持对象，封装了数据源和 JdbcTemplate
     */
    @Bean(name = "documentManagePgVectorJdbcSupport")
    public DocumentManagePgVectorJdbcSupport documentManagePgVectorJdbcSupport(DocumentManageProperties properties) {
        DocumentManageProperties.PgVector pg = properties.getPgVector();
        // 创建 HikariCP 连接池
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");  // PostgreSQL JDBC 驱动
        dataSource.setJdbcUrl(buildJdbcUrl(pg));                 // 构建 JDBC 连接 URL
        dataSource.setUsername(pg.getUsername());
        dataSource.setPassword(pg.getPassword());
        dataSource.setPoolName(pg.getPoolName());                // 连接池名称，便于监控和日志
        dataSource.setMaximumPoolSize(pg.getMaximumPoolSize());  // 最大连接数
        dataSource.setMinimumIdle(pg.getMinimumIdle());          // 最小空闲连接数
        return new DocumentManagePgVectorJdbcSupport(dataSource);
    }

    /**
     * 创建 pgvector 专用的 JdbcTemplate Bean。
     *
     * JdbcTemplate 是 Spring 对 JDBC 的封装，简化了数据库操作。
     * 这里通过 Qualifier 注入文档管理专用的数据源，避免与主数据库混淆。
     *
     * @param jdbcSupport JDBC 支持对象（包含数据源和 JdbcTemplate）
     * @return JdbcTemplate 实例
     */
    @Bean(name = "documentManagePgVectorJdbcTemplate")
    public JdbcTemplate documentManagePgVectorJdbcTemplate(
        @Qualifier("documentManagePgVectorJdbcSupport") DocumentManagePgVectorJdbcSupport jdbcSupport) {
        return jdbcSupport.getJdbcTemplate();
    }

    /**
     * 构建 PostgreSQL JDBC 连接 URL。
     *
     * URL 格式：jdbc:postgresql://host:port/database?stringtype=unspecified[&currentSchema=schema]
     *
     * @param pg pgvector 配置属性
     * @return JDBC URL 字符串
     */
    private String buildJdbcUrl(DocumentManageProperties.PgVector pg) {
        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
            .append(pg.getHost())
            .append(":")
            .append(pg.getPort())
            .append("/")
            .append(pg.getDatabase())
            .append("?stringtype=unspecified");
        // 如果配置了 schema，追加到 URL 中
        if (StrUtil.isNotBlank(pg.getSchema())) {
            jdbcUrl.append("&currentSchema=").append(pg.getSchema());
        }
        return jdbcUrl.toString();
    }

    /**
     * pgvector JDBC 支持内部类。
     *
     * 封装了 HikariDataSource 和 JdbcTemplate，
     * 并实现了 DisposableBean 接口，确保 Spring 容器关闭时
     * 数据源连接池被正确释放，防止连接泄漏。
     *
     * 为什么需要这个内部类？
     * 
     *   JdbcTemplate 本身不管理数据源的生命周期。
     *   需要一个持有数据源引用的对象来在销毁时关闭连接池。
     *   将数据源和 JdbcTemplate 打包在一起，方便统一管理。
     * 
     */
    public static class DocumentManagePgVectorJdbcSupport implements DisposableBean {

        /**
         * HikariCP 数据源，管理 PostgreSQL 连接池。
         */
        private final HikariDataSource dataSource;

        /**
         * Spring JdbcTemplate，封装了 JDBC 操作的模板工具。
         */
        private final JdbcTemplate jdbcTemplate;

        /**
         * 构造器，接收数据源并创建 JdbcTemplate。
         *
         * @param dataSource HikariCP 数据源实例
         */
        public DocumentManagePgVectorJdbcSupport(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            this.jdbcTemplate = new JdbcTemplate(dataSource);
        }

        /**
         * 获取 JdbcTemplate 实例。
         *
         * @return JdbcTemplate 实例
         */
        public JdbcTemplate getJdbcTemplate() {
            return jdbcTemplate;
        }

        /**
         * 销毁时关闭数据源连接池。
         *
         * 当 Spring 容器关闭时会自动调用此方法，
         * 释放所有数据库连接资源。
         */
        @Override
        public void destroy() {
            dataSource.close();
        }
    }
}
