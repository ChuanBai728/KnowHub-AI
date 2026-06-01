package ai.knowhub.config;

import cn.hutool.core.date.DateTime;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 【JSON 空值序列化修饰器】—— 统一处理各种类型字段为 null 时的 JSON 输出
 *
 * 核心作用：
 * 当 Java 对象的某个字段值为 null 时，Jackson 默认会在 JSON 中输出 "null"。
 * 但前端（特别是 JavaScript）在处理 null 时容易出问题。
 * 这个修饰器为不同类型的 null 字段设置了"空值默认输出"：
 * 
 *   String → 输出 ""（空字符串）
 *   Number → 输出 ""（空字符串，注意不是 0）
 *   Boolean → 输出 false
 *   Date / DateTime → 输出 ""（空字符串）
 *   数组 / List / Set → 输出 []（空数组）
 * 实现原理：
 * 通过继承 BeanSerializerModifier，在 Jackson 创建 Bean 序列化器的过程中，
 * 拦截每个属性的序列化器，为 null 值设置专门的"空值序列化器"（Null Serializer）。
 *
 * 工作流程：
 * 1. Jackson 准备序列化某个 Java Bean 时，先调用 #changeProperties 方法
 * 2. 遍历 Bean 的每个属性，调用 #judgeType 判断属性类型
 * 3. 根据类型返回对应的空值序列化器，设置到属性上
 * 4. 后续序列化时，如果该属性值为 null，就使用设置好的空值序列化器输出
 *
 * 使用场景：
 * 在 KnowHubCommonAutoConfig#createMvcObjectMapper 中通过
 * mvcObjectMapper.getSerializerFactory().withSerializerModifier(new JsonCustomSerializer())
 * 注册到 MVC 专用的 ObjectMapper 上。
 *
 * @see KnowHubCommonAutoConfig 自动配置类（负责注册本修饰器）
 */
public class JsonCustomSerializer extends BeanSerializerModifier {

	/**
	 * 拦截并修改 Bean 属性的序列化器列表
	 *
	 * 这是 BeanSerializerModifier 的核心方法。Jackson 在序列化一个 Java Bean 之前，
	 * 会调用此方法来获取最终的属性列表。我们可以在这里为每个属性添加"空值序列化器"。
	 *
	 * @param config         序列化配置信息
	 * @param beanDesc       Bean 的描述信息（包含类名、注解等元数据）
	 * @param beanProperties Bean 的属性列表（每个属性对应一个 BeanPropertyWriter）
	 * @return 修改后的属性列表（通常是原列表，但每个属性可能已附加了空值序列化器）
	 */
	@Override
	public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
			List<BeanPropertyWriter> beanProperties) {

		// 遍历 Bean 的每个属性
		for (BeanPropertyWriter writer : beanProperties) {
			// 根据属性类型判断是否需要设置空值序列化器
			JsonSerializer<Object> js = judgeType(writer);
			if (js != null) {
				// 为该属性设置空值序列化器：当属性值为 null 时，使用此序列化器输出
				writer.assignNullSerializer(js);
			}
		}
		return beanProperties;
	}

	/**
	 * 根据属性的 Java 类型，返回对应的空值序列化器
	 *
	 * 判断逻辑（按顺序）：
	 * 
	 *   String 类型 → 输出空字符串 ""
	 *   Number 类型（Integer、Long、Double 等）→ 输出空字符串 ""
	 *   Boolean 类型 → 输出 false
	 *   Date 类型 → 输出空字符串 ""
	 *   DateTime 类型（Hutool 的日期类型）→ 输出空字符串 ""
	 *   数组 / List / Set 类型 → 输出空数组 []
	 *   其他类型 → 返回 null（不做特殊处理，Jackson 会输出 JSON null）
	 * @param writer Bean 的属性写入器，包含属性的类型信息
	 * @return 对应类型的空值序列化器；如果不需要特殊处理则返回 null
	 */
	public JsonSerializer<Object> judgeType(BeanPropertyWriter writer) {
		// 获取属性的 Java 类型信息
		JavaType javaType = writer.getType();
		Class<?> clazz = javaType.getRawClass();

		// 判断是否为 String 类型（包括 String 的子类）
		if (String.class.isAssignableFrom(clazz)) {
			return new JsonSerializer<Object>() {
				@Override
				public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
						throws IOException {
					// String 为 null 时，输出空字符串 ""
					gen.writeString("");
				}
			};
		}

		// 判断是否为数值类型（Integer、Long、Double、BigDecimal 等都继承自 Number）
		if (Number.class.isAssignableFrom(clazz)) {
			return new JsonSerializer<Object>() {
				@Override
				public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
						throws IOException {
					// Number 为 null 时，输出空字符串 ""
					gen.writeString("");
				}
			};
		}

		// 判断是否为布尔类型
		if (Boolean.class.isAssignableFrom(clazz)) {
			return new JsonSerializer<Object>() {
				@Override
				public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
						throws IOException {
					// Boolean 为 null 时，输出 false（比 null 更安全，前端可直接用于条件判断）
					gen.writeBoolean(false);
				}
			};
		}

		// 判断是否为 java.util.Date 类型
		if (Date.class.isAssignableFrom(clazz)) {
			return new JsonSerializer<Object>() {
				@Override
				public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
						throws IOException {
					// Date 为 null 时，输出空字符串 ""
					gen.writeString("");
				}
			};
		}

		// 判断是否为 Hutool 的 DateTime 类型
		if (clazz.equals(DateTime.class)) {
			return new JsonSerializer<Object>() {
				@Override
				public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
						throws IOException {
					// DateTime 为 null 时，输出空字符串 ""
					gen.writeString("");
				}
			};
		}

		// 判断是否为数组、List 或 Set 类型
		if (clazz.isArray() || clazz.equals(List.class) || clazz.equals(Set.class)) {
			return new JsonSerializer<Object>() {
				@Override
				public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
						throws IOException {
					// 集合类型为 null 时，输出空数组 []
					gen.writeStartArray();  // 写入 [
					gen.writeEndArray();    // 写入 ]
				}
			};
		}

		// 其他类型不做特殊处理，返回 null
		return null;
	}
}
