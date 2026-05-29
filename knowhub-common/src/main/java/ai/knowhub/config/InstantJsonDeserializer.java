package ai.knowhub.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

/**
 * 【Instant 时间戳反序列化器】—— 将 JSON 中的日期字符串转为 Java 8 的 Instant 对象
 *
 * 背景知识：
 * Java 8 引入了新的时间 API（java.time 包），其中 Instant 表示
 * 时间线上的一个瞬时点（类似时间戳）。它比传统的 Date 更推荐使用。
 *
 * 实现原理：
 * 这个反序列化器内部复用了 DateJsonDeserializer#parseDate(String) 方法，
 * 先将字符串解析为 Date 对象，再通过 Date.toInstant() 转换为 Instant。
 * 这样避免了重复编写日期格式匹配逻辑。
 *
 * 使用场景：
 * 当实体类中的日期字段使用 Instant 类型时（而非 Date 类型），
 * Jackson 会自动调用此反序列化器来解析前端传来的日期字符串。
 * 在 JacksonCustom 中通过
 * builder.deserializerByType(Instant.class, new InstantJsonDeserializer()) 注册。
 *
 * @see DateJsonDeserializer  Date 类型的反序列化器（本类依赖其解析逻辑）
 * @see JacksonCustom  Jackson 全局自定义配置类
 */
public class InstantJsonDeserializer extends JsonDeserializer<Instant> {

    /**
     * 反序列化入口方法
     *
     * 将 JSON 中的日期字符串转为 Instant 对象。处理步骤：
     * 
     *   从 JSON 解析器中获取原始字符串
     *   调用 DateJsonDeserializer.parseDate() 先转为 Date 对象
     *   通过 Date.toInstant() 转为 Instant 对象
     *   如果解析结果为 null（输入为空），则返回 null
     * @param p    JSON 解析器，提供原始的 JSON 文本值
     * @param ctxt 反序列化上下文（此处未使用）
     * @return 解析后的 Instant 对象；输入为空字符串或 null 时返回 null
     * @throws IOException            读取 JSON 流时可能抛出的 IO 异常
     * @throws JsonProcessingException JSON 格式错误时抛出
     */
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        // 先复用 DateJsonDeserializer 将字符串解析为 Date，再转为 Instant
        Date convertDate = DateJsonDeserializer.parseDate(p.getText());
        return convertDate != null ? convertDate.toInstant() : null;
    }
}
