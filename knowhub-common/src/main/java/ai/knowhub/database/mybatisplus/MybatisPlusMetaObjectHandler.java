package ai.knowhub.database.mybatisplus;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import ai.knowhub.util.DateUtils;

import java.util.Date;

/**
 * 【MyBatis-Plus 自动填充处理器】—— 在数据库插入/更新时自动设置时间字段
 *
 * 核心作用：
 * 当执行数据库 INSERT（插入）或 UPDATE（更新）操作时，自动为实体对象的
 * 时间字段填充当前时间，无需手动调用 setter 设置。
 *
 * 工作原理：
 * 
 *   MyBatis-Plus 在执行 insert/update 操作前，会检查实体类中是否有
 *       标注了 @TableField(fill = FieldFill.INSERT) 的字段
 *   如果有，就调用本处理器的 #insertFill 或 #updateFill 方法
 *   处理器通过 strictInsertFill 将当前时间设置到对应字段
 * strictInsertFill 与 insertFill 的区别：
 * 
 *   strictInsertFill：严格模式，只有当字段值为 null 时才填充（推荐）
 *   insertFill：非严格模式，无论字段是否有值都会覆盖
 * Lombok 注解说明：
 * @Slf4j 自动生成一个名为 log 的日志对象，可以直接使用 log.info() 等方法输出日志。
 *
 * 关联说明：
 * 本处理器通过 MybatisPlusAutoConfiguration#metaObjectHandler() 注册为 Spring Bean，
 * 配合 BaseTableData 中的 @TableField(fill = ...) 注解一起工作。
 *
 * @see MybatisPlusAutoConfiguration  注册本处理器的配置类
 * @see BaseTableData                 定义了自动填充字段的基类
 */
@Slf4j
public class MybatisPlusMetaObjectHandler implements MetaObjectHandler {

    /**
     * INSERT 操作时的自动填充方法
     *
     * 当执行数据库插入操作时，MyBatis-Plus 会自动调用此方法。
     * 这里为 createTime 和 editTime 字段都设置为当前时间。
     *
     * 填充逻辑：
     * 
     *   createTime：使用 strictInsertFill，只有当字段值为 null 时才填充当前时间
     *   editTime：同上，插入时也设置编辑时间为当前时间
     * 关于 DateUtils::now：
     * 这是方法引用（Method Reference），等价于 () -> DateUtils.now()。
     * 使用 Supplier 函数式接口，只有在真正需要填充时才调用，实现懒加载。
     *
     * @param metaObject 元对象，包含实体对象的属性信息和原始值
     */
    @Override
    public void insertFill(MetaObject metaObject) {

        // 严格模式填充 createTime：仅当字段值为 null 时才设置
        this.strictInsertFill(metaObject, "createTime", DateUtils::now, Date.class);
        // 严格模式填充 editTime：仅当字段值为 null 时才设置
        this.strictInsertFill(metaObject, "editTime", DateUtils::now, Date.class);
    }

    /**
     * UPDATE 操作时的自动填充方法
     *
     * 当执行数据库更新操作时，MyBatis-Plus 会自动调用此方法。
     * 这里只为 editTime（编辑时间）字段设置当前时间。
     *
     * 注意：更新时不会修改 createTime（创建时间），
     * 因为创建时间应该在记录创建时确定，后续不应改变。
     *
     * @param metaObject 元对象，包含实体对象的属性信息和原始值
     */
    @Override
    public void updateFill(MetaObject metaObject) {

        // 严格模式填充 editTime：仅当字段值为 null 时才设置
        this.strictUpdateFill(metaObject, "editTime", DateUtils::now, Date.class);
    }
}
