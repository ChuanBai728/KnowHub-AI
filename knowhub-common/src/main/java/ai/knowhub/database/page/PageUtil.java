package ai.knowhub.database.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.pagehelper.PageInfo;
import ai.knowhub.database.dto.BasePageDto;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 【分页工具类】—— 封装分页参数构建和分页结果转换
 *
 * 核心作用：
 * 提供一组静态工具方法，用于：
 * 
 *   从分页 DTO 构建 MyBatis-Plus 的分页对象（IPage）
 *   将分页查询结果转换为统一的响应格式（PageVo）
 *   支持对分页结果进行类型转换（如 Entity → VO/DTO）
 * 两种分页方案的支持：
 * 
 *   MyBatis-Plus 分页：通过 IPage / Page 实现，
 *       配合 MybatisPlusInterceptor 分页拦截器自动改写 SQL
 *   PageHelper 分页：通过 PageInfo 实现，
 *       是另一个流行的 MyBatis 分页插件
 * 使用示例：
 * 
 * // 1. 获取分页参数
 * IPage&lt;User&gt; page = PageUtil.getPageParams(dto);
 *
 * // 2. 执行查询（MyBatis-Plus 会自动分页）
 * IPage&lt;User&gt; result = userMapper.selectPage(page, queryWrapper);
 *
 * // 3. 转换为统一响应格式（Entity → VO）
 * PageVo&lt;UserVo&gt; pageVo = PageUtil.convertPage(result, user -&gt; {
 *     UserVo vo = new UserVo();
 *     BeanUtils.copyProperties(user, vo);
 *     return vo;
 * });
 * @see BasePageDto 分页查询请求参数基类
 * @see PageVo     分页查询响应 VO 类
 */
public class PageUtil {

    /**
     * 从分页 DTO 构建 MyBatis-Plus 分页对象
     *
     * 将前端传来的分页参数（BasePageDto）转换为 MyBatis-Plus 能识别的 IPage 对象。
     * 这是分页查询的第一步。
     *
     * @param <T>         实体类型（泛型）
     * @param basePageDto 分页请求 DTO，包含 pageNumber（页码）和 pageSize（每页大小）
     * @return MyBatis-Plus 的 IPage 分页对象，可直接传入 Mapper 的 selectPage 方法
     */
    public static <T> IPage<T> getPageParams(BasePageDto basePageDto) {
        return getPageParams(basePageDto.getPageNumber(), basePageDto.getPageSize());
    }

    /**
     * 根据页码和每页大小构建 MyBatis-Plus 分页对象
     *
     * 直接传入页码和每页大小，返回 IPage 对象。适用于不方便使用 BasePageDto 的场景。
     *
     * @param <T>        实体类型（泛型）
     * @param pageNumber 页码（从 1 开始）
     * @param pageSize   每页大小
     * @return MyBatis-Plus 的 Page 分页对象
     */
    public static <T> IPage<T> getPageParams(int pageNumber, int pageSize) {
        return new Page<>(pageNumber, pageSize);
    }

    /**
     * 将 PageHelper 的分页结果转换为统一的 PageVo 响应格式
     *
     * 当使用 PageHelper 插件进行分页时，查询结果封装在 PageInfo 中。
     * 此方法将 PageInfo 转换为项目统一的 PageVo 格式，并支持对结果进行类型转换。
     *
     * 关于函数式参数 function：
     * 这是一个类型转换函数，通常用于将数据库实体（Entity）转换为前端展示对象（VO/DTO）。
     * 例如：User → UserVo，可以在这里做字段裁剪、脱敏等操作。
     *
     * @param     原始数据类型（通常是数据库实体类）
     * @param <NEW>    目标数据类型（通常是 VO 或 DTO）
     * @param pageInfo PageHelper 的分页查询结果
     * @param function 类型转换函数（例如 user -&gt; convertToVo(user)）
     * @return 统一格式的分页响应对象 PageVo
     */
    public static <OLD,NEW> PageVo<NEW> convertPage(PageInfo<OLD> pageInfo, Function<? super OLD, ? extends NEW> function){
        return new PageVo<>(pageInfo.getPageNum(),
                pageInfo.getPageSize(),
                pageInfo.getTotal(),
                pageInfo.getList().stream().map(function).collect(Collectors.toList()));
    }

    /**
     * 将 MyBatis-Plus 的分页结果转换为统一的 PageVo 响应格式
     *
     * 当使用 MyBatis-Plus 的 selectPage 方法进行分页时，查询结果封装在 IPage 中。
     * 此方法将 IPage 转换为项目统一的 PageVo 格式，并支持对结果进行类型转换。
     *
     * Stream API 说明：
     * iPage.getRecords().stream().map(function).collect(Collectors.toList())
     * 的含义是：将查询结果列表中的每个元素，通过 function 函数转换为新类型，最后收集为新列表。
     *
     * @param    原始数据类型（通常是数据库实体类）
     * @param <NEW>   目标数据类型（通常是 VO 或 DTO）
     * @param iPage   MyBatis-Plus 的分页查询结果
     * @param function 类型转换函数
     * @return 统一格式的分页响应对象 PageVo
     */
    public static <OLD,NEW> PageVo<NEW> convertPage(IPage<OLD> iPage, Function<? super OLD, ? extends NEW> function){
        return new PageVo<>(iPage.getCurrent(),
                iPage.getSize(),
                iPage.getTotal(),
                iPage.getRecords().stream().map(function).collect(Collectors.toList()));
    }
}
