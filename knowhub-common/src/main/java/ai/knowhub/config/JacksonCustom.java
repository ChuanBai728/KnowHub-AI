package ai.knowhub.config;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.core.Ordered;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

/**
 * 【Jackson 全局自定义配置类】—— 统一配置 JSON 序列化/反序列化规则
 *
 * 核心作用：
 * 这是整个项目的 JSON 格式化"总开关"。它通过实现 Jackson2ObjectMapperBuilderCustomizer 接口，
 * 对 Spring Boot 自动配置的 Jackson ObjectMapper 进行自定义，使得所有 HTTP 接口的
 * JSON 输入/输出都遵循统一的日期格式、序列化策略等规则。
 *
 * 主要配置内容：
 * 
 *   序列化包含策略：Include.ALWAYS —— 所有字段都输出（包括 null 值）
 *   JSON 读取宽松模式：允许单引号、未转义控制字符等
 *   日期格式统一化：Date、LocalDateTime、Instant、LocalDate、LocalTime 全部统一格式
 *   忽略未知属性：反序列化时 JSON 中有 Java 类没有的字段不报错
 * 执行优先级：
 * 通过 #getOrder() 返回 1，确保本配置优先级较高（数值越小优先级越高）。
 *
 * 关于 Jackson：
 * Jackson 是 Java 生态中最流行的 JSON 处理库，Spring Boot 默认用它来做 JSON 序列化/反序列化。
 * "序列化"是把 Java 对象转成 JSON 字符串（用于响应），
 * "反序列化"是把 JSON 字符串转成 Java 对象（用于请求参数）。
 *
 * @see DateJsonDeserializer   Date 类型的自定义反序列化器
 * @see InstantJsonDeserializer Instant 类型的自定义反序列化器
 * @see KnowHubCommonAutoConfig 自动配置类（负责注册本 Bean）
 */
public class JacksonCustom implements Jackson2ObjectMapperBuilderCustomizer, Ordered {

    /**
     * 日期时间格式模板
     * "yyyy-MM-dd HH:mm:ss" 是最常见的中文环境日期时间格式
     * 例如：2024-01-15 10:30:45
     */
    private final String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

    /**
     * 自定义配置入口方法
     *
     * Spring Boot 在创建 ObjectMapper 时会自动调用此方法，
     * 将本类中定义的所有自定义规则应用到 ObjectMapper 上。
     *
     * @param builder Jackson 的 ObjectMapper 构建器，用于注册各种自定义序列化/反序列化规则
     */
    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {

        // ========== 1. 序列化包含策略 ==========
        // Include.ALWAYS：即使字段值为 null，也输出到 JSON 中（输出 null）
        // 其他选项：NON_NULL（忽略null）、NON_EMPTY（忽略null和空字符串）等
        builder.serializationInclusion(Include.ALWAYS);

        // ========== 2. JSON 读取特性（宽松模式） ==========
        // 允许使用单引号包裹字符串，例如 {'name': '张三'} 也是合法的
        builder.featuresToEnable(Feature.ALLOW_SINGLE_QUOTES);
        // 允许字段名不加引号，例如 {name: '张三'} 也是合法的
        builder.featuresToEnable(Feature.ALLOW_UNQUOTED_FIELD_NAMES);

        // ========== 3. Date 类型序列化/反序列化配置 ==========
        // 序列化：Date → JSON 字符串，格式为 "yyyy-MM-dd HH:mm:ss"
        builder.serializerByType(Date.class, new JsonSerializer<Date>() {

            @Override
            public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                SimpleDateFormat sdf = new SimpleDateFormat(dateTimeFormat);
                String newValue = sdf.format(value);
                gen.writeString(newValue);
            }

        });
        // 反序列化：JSON 字符串 → Date，使用自定义的 DateJsonDeserializer（支持多种格式）
        builder.deserializerByType(Date.class, new DateJsonDeserializer());

        // ========== 4. LocalDateTime 类型序列化/反序列化配置 ==========
        // LocalDateTime 是 Java 8 新增的日期时间类型，比 Date 更推荐使用
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormat);
        // 序列化：LocalDateTime → "yyyy-MM-dd HH:mm:ss"
        builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        // 反序列化："yyyy-MM-dd HH:mm:ss" → LocalDateTime
        builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));

        // ========== 5. Instant 类型序列化/反序列化配置 ==========
        // Instant 表示时间线上的一个瞬时点（类似于时间戳）
        // 序列化：Instant → "yyyy-MM-dd HH:mm:ss"（先转为系统时区的 LocalDateTime 再格式化）
        builder.serializerByType(Instant.class, new JsonSerializer<Instant>() {

            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                // 将 Instant 转为系统默认时区的 LocalDateTime，再格式化为字符串
                String newValue = LocalDateTime.ofInstant(value, ZoneId.systemDefault()).format(dateTimeFormatter);
                gen.writeString(newValue);
            }

        });
        // 反序列化：字符串 → Instant，使用自定义的 InstantJsonDeserializer
        builder.deserializerByType(Instant.class, new InstantJsonDeserializer());

        // ========== 6. LocalDate 类型序列化/反序列化配置 ==========
        // LocalDate 只包含日期（年月日），不包含时间
        String dateFormat = "yyyy-MM-dd";
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(dateFormat);
        builder.serializerByType(LocalDate.class, new LocalDateSerializer(dateFormatter));
        builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(dateFormatter));

        // ========== 7. LocalTime 类型序列化/反序列化配置 ==========
        // LocalTime 只包含时间（时分秒），不包含日期
        String timeFormat = "HH:mm:ss";
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(timeFormat);
        builder.serializerByType(LocalTime.class, new LocalTimeSerializer(timeFormatter));
        builder.deserializerByType(LocalTime.class, new LocalTimeDeserializer(timeFormatter));

        // ========== 8. 时区设置 ==========
        // 使用系统默认时区，确保日期时间的序列化结果与本地环境一致
        builder.timeZone(TimeZone.getDefault());

        // ========== 9. 反序列化容错配置 ==========
        // 当 JSON 中包含 Java 类没有的字段时，不抛出异常，而是静默忽略
        // 这在接口版本迭代时非常有用：前端新增字段不会导致后端报错
        builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // ========== 10. 允许未转义的控制字符 ==========
        // 例如 JSON 字符串中包含换行符 \n 等控制字符时不报错
        builder.featuresToEnable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature());
    }

    /**
     * 配置执行顺序（优先级）
     *
     * 返回值越小，优先级越高。这里返回 1，表示优先级较高。
     * 当存在多个 Jackson2ObjectMapperBuilderCustomizer 时，
     * Spring Boot 会按照 getOrder() 的返回值从小到大依次执行。
     *
     * @return 优先级序号，值为 1
     */
    @Override
    public int getOrder() {
        return 1;
    }
}
