package ai.knowhub.database.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 【分页响应 VO 基类】—— 统一的分页查询结果封装
 *
 * 核心作用：
 * 将分页查询的结果统一封装为此格式返回给前端。
 * 无论使用 MyBatis-Plus 分页还是 PageHelper 分页，最终都转换为 PageVo 格式，
 * 保证前端接收的数据结构一致。
 *
 * VO 是什么？
 * VO（Value Object，返回值对象）是用于返回给前端展示的数据对象。
 * 与 DTO（接收请求参数）不同，VO 用于封装响应数据。
 *
 * 返回给前端的 JSON 结构示例：
 *
 * {
 *   "pageNum": 1,
 *   "pageSize": 10,
 *   "totalSize": 100,
 *   "list": [
 *     { "id": 1, "name": "张三" },
 *     { "id": 2, "name": "李四" }
 *   ]
 * }
 * Lombok 注解说明：
 *
 *   @Data：自动生成 getter/setter/toString/equals/hashCode
 *   @NoArgsConstructor：生成无参构造方法（反序列化时需要）
 *   @AllArgsConstructor：生成包含所有字段的构造方法（方便 PageUtil 中使用）
 * Serializable 说明：
 * 实现序列化接口，使此对象可以被序列化为字节流，
 * 用于网络传输（如 RPC 调用）或缓存存储（如 Redis）。
 *
 * @param <T> 列表中元素的类型（泛型），例如 UserVo、OrderVo 等
 * @see PageUtil 分页工具类（负责构建和转换 PageVo）
 * @see BasePageDto 分页请求参数 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVo<T> implements Serializable {

    /**
     * 序列化版本号
     *
     * @Serial 是 Java 14 引入的注解，用于标记序列化相关的字段。
     * serialVersionUID 是 Java 序列化机制用来验证版本一致性的。
     * 如果类结构发生变化但版本号不变，反序列化时可能出问题。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     *
     * 表示当前返回的是第几页数据。从 1 开始计数。
     */
    private long pageNum;

    /**
     * 每页大小
     *
     * 表示每页包含多少条记录。与请求时传入的 pageSize 一致。
     */
    private long pageSize;

    /**
     * 总记录数
     *
     * 表示满足查询条件的总记录数（不是当前页的记录数）。
     * 前端通常用此值来计算总页数，实现分页导航。
     * 例如：totalSize=100, pageSize=10，则总页数为 10。
     */
    private long totalSize;

    /**
     * 数据列表
     *
     * 当前页的数据记录列表。泛型 T 可以是任意类型，如 UserVo、OrderVo 等。
     * 列表大小通常等于 pageSize（最后一页可能小于 pageSize）。
     */
    private List<T> list;
}
