package ai.knowhub.database.mybatisplus;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * 【MyBatis-Plus 自动配置类】—— 注册 MyBatis-Plus 的核心组件
 *
 * 核心作用：
 * 本类负责注册两个 MyBatis-Plus 的核心 Bean：
 * 
 *   MetaObjectHandler（元对象处理器）：实现字段自动填充功能，
 *       例如在插入数据时自动设置创建时间
 *   MybatisPlusInterceptor（拦截器）：注册分页插件，
 *       使 MyBatis-Plus 的分页查询能够正常工作
 * 什么是 MyBatis-Plus？
 * MyBatis-Plus 是 MyBatis 的增强工具，在 MyBatis 的基础上只做增强不做改变，
 * 为简化开发、提高效率而生。它提供了：
 * 
 *   通用 CRUD：内置通用 Mapper 和 Service，无需编写基础 SQL
 *   条件构造器：链式调用构建查询条件
 *   分页插件：内置物理分页插件
 *   自动填充：在插入/更新时自动填充字段值
 * 注意：
 * 本类没有标注 @Configuration，是通过 SPI 机制自动加载的。
 * 当项目依赖了 knowhub-common 模块时，这些配置会自动生效。
 *
 * @see MybatisPlusMetaObjectHandler 自动填充处理器的实现类
 * @see BaseTableData 自动填充的字段定义（createTime、editTime 等）
 */
public class MybatisPlusAutoConfiguration {

    /**
     * 注册元对象处理器（MetaObjectHandler）
     *
     * MetaObjectHandler 是 MyBatis-Plus 提供的字段自动填充机制。
     * 当实体类的字段上标注了 @TableField(fill = FieldFill.INSERT) 等注解时，
     * 在执行 INSERT 或 UPDATE 操作，MyBatis-Plus 会自动调用此处理器来填充字段值。
     *
     * 本项目中，MybatisPlusMetaObjectHandler 负责：
     * 
     *   INSERT 时：自动填充 createTime（创建时间）和 editTime（编辑时间）
     *   UPDATE 时：自动填充 editTime（编辑时间）
     * @return MetaObjectHandler 实例
     */
    @Bean
    public MetaObjectHandler metaObjectHandler(){
        return new MybatisPlusMetaObjectHandler();
    }

    /**
     * 注册 MyBatis-Plus 拦截器（分页插件）
     *
     * MybatisPlusInterceptor 是 MyBatis-Plus 的核心拦截器，可以添加多个内部拦截器。
     * 这里注册了 PaginationInnerInterceptor（分页拦截器），指定数据库类型为 MySQL。
     *
     * 分页插件的工作原理：
     * 当你调用 MyBatis-Plus 的 selectPage() 方法时，分页拦截器会自动拦截 SQL，
     * 将原始 SQL 改写为分页查询 SQL（添加 LIMIT 子句），并自动执行 COUNT 查询获取总记录数。
     *
     * 例如，原始 SQL：
     * SELECT * FROM user WHERE status = 1
     * 分页插件改写后：
     * SELECT * FROM user WHERE status = 1 LIMIT 10 OFFSET 0
     *
     * @return 配置好分页插件的 MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加 MySQL 分页拦截器
        // DbType.MYSQL 指定数据库类型，分页插件会根据不同数据库生成对应的分页 SQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
