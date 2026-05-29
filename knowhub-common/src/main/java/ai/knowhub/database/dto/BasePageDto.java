package ai.knowhub.database.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 【分页查询基础 DTO 类】—— 所有分页查询请求的参数基类
 *
 * 核心作用：
 * 定义了分页查询必须的两个参数：页码（pageNumber）和每页大小（pageSize）。
 * 所有需要分页的查询接口的请求参数 DTO 都应该继承此类。
 *
 * DTO 是什么？
 * DTO（Data Transfer Object，数据传输对象）是用于在客户端和服务器之间传输数据的对象。
 * 它通常用于接收前端传来的请求参数，与数据库实体类区分开来。
 *
 * 使用示例：
 * 
 * // 查询用户的 DTO，继承 BasePageDto 自动获得分页参数
 * public class UserQueryDto extends BasePageDto {
 *     private String username;  // 额外的查询条件
 * }
 *
 * // Controller 中使用
 * public ApiResponse listUsers(UserQueryDto dto) {
 *     IPage&lt;User&gt; page = PageUtil.getPageParams(dto);  // 获取分页参数
 *     // ... 执行分页查询
 * }
 * 校验说明：
 * 使用 Jakarta Validation（JSR 380）的 @NotNull 注解进行参数校验。
 * 当前端传来的 pageNumber 或 pageSize 为 null 时，Spring 会自动返回 400 错误。
 *
 * Swagger 注解说明：
 * @Schema 注解用于在 Swagger/Knife4j API 文档中描述接口参数，
 * 让前端开发者清楚地知道每个参数的含义、类型和是否必填。
 *
 * @see PageUtil 分页工具类（将 DTO 转换为 MyBatis-Plus 的分页对象）
 * @see PageVo 分页响应 VO 类（封装分页查询结果）
 */
@Data
public class BasePageDto {

    /**
     * 页码（从 1 开始）
     *
     * 表示要查询第几页的数据。例如 pageNumber=1 表示第一页，pageNumber=2 表示第二页。
     *
     * 注解说明：
     * 
     *   @Schema：Swagger 文档注解，描述参数信息
     *     
     *       name：参数名称
     *       type：参数类型（注意：这里写的是 Long，但实际字段是 Integer，可能是文档笔误）
     *       description：参数描述（"页码"）
     *       requiredMode：是否必填（REQUIRED 表示必填）
     *   @NotNull：Jakarta Validation 校验注解，确保参数不为 null
     * 
     */
    @Schema(name ="pageNumber", type ="Long", description ="页码",requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Integer pageNumber;

    /**
     * 每页大小（每页显示多少条数据）
     *
     * 表示每页返回多少条记录。例如 pageSize=10 表示每页 10 条，
     * pageSize=20 表示每页 20 条。通常建议设置合理的上限（如 100），
     * 防止一次查询过多数据导致性能问题。
     *
     * 注解说明同 pageNumber。
     */
    @Schema(name ="pageSize", type ="Long", description ="页大小",requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Integer pageSize;
}
