package ai.knowhub.database.data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

/**
 * 【数据库表基础字段实体类】—— 所有数据库表实体的公共父类
 *
 * 核心作用：
 * 定义了所有数据库表都需要的公共字段（创建时间、编辑时间、状态），
 * 并通过 MyBatis-Plus 的自动填充功能，在数据插入/更新时自动设置这些字段的值。
 *
 * 设计模式：模板方法模式
 * 所有业务实体类都应该继承此基类，这样就自动拥有了创建时间、编辑时间、状态字段，
 * 以及自动填充能力，无需在每个实体类中重复定义。
 *
 * 使用示例：
 * 
 * // 实体类继承 BaseTableData，自动获得 createTime、editTime、status 字段
 * public class User extends BaseTableData {
 *     private Long id;
 *     private String username;
 * }
 * 自动填充原理：
 * 字段上的 @TableField(fill = ...) 注解配合 MybatisPlusMetaObjectHandler，
 * 在执行 INSERT 或 UPDATE SQL 时，MyBatis-Plus 会自动为这些字段填充当前时间。
 *
 * Lombok 注解说明：
 * @Data 自动生成 getter、setter、toString、equals、hashCode 方法，
 * 这样就不用手写这些样板代码了。
 *
 * @see MybatisPlusMetaObjectHandler 自动填充处理器（负责在插入/更新时填充时间字段）
 * @see BasePageDto 分页查询基础 DTO
 */
@Data
public class BaseTableData {

    /**
     * 创建时间
     *
     * @TableField(fill = FieldFill.INSERT) 表示：
     * 仅在 INSERT（插入数据）时自动填充此字段。
     *
     * 自动填充时机：当执行 MyBatis-Plus 的 insert() 方法时，
     * MybatisPlusMetaObjectHandler#insertFill 会自动将当前时间设置到此字段。
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 最后编辑时间
     *
     * @TableField(fill = FieldFill.INSERT_UPDATE) 表示：
     * 在 INSERT（插入）和 UPDATE（更新）时都会自动填充此字段。
     *
     * 也就是说：新建记录时设置一次，每次修改记录时也会更新为当前时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date editTime;

    /**
     * 数据状态
     *
     * 通常用于逻辑删除或数据状态管理。常见取值示例：
     * 
     *   0 - 禁用/已删除
     *   1 - 启用/正常
     * 注意：此字段没有标注自动填充注解，需要在业务代码中手动设置。
     */
    private Integer status;
}
