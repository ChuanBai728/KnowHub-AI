package ai.knowhub.config;

import ai.knowhub.util.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 【日期反序列化器】—— 将 JSON 中的日期字符串转为 Java 的 Date 对象
 *
 * 作用说明：
 * 当前端（或接口调用方）传来的 JSON 数据中包含日期字段时，
 * Spring 默认可能无法识别各种格式的日期字符串。
 * 这个自定义反序列化器负责把多种常见日期格式的字符串统一解析为 Date 对象。
 *
 * 支持的输入格式：
 * 
 *   纯数字时间戳（毫秒级），例如：1700000000000
 *   yyyy-MM，例如：2024-01
 *   yyyy-MM-dd，例如：2024-01-15
 *   yyyy-MM-dd HH:mm，例如：2024-01-15 10:30
 *   yyyy-MM-dd HH:mm:ss，例如：2024-01-15 10:30:45
 *   yyyy/MM/dd HH:mm:ss，例如：2024/01/15 10:30:45
 * 使用场景：
 * 通常在 JacksonCustom 中通过 builder.deserializerByType(Date.class, new DateJsonDeserializer())
 * 注册到 Jackson 的 ObjectMapper 中，使全局生效。
 *
 * @see JacksonCustom  Jackson 全局自定义配置类
 * @see InstantJsonDeserializer  Instant 类型的反序列化器（内部复用了本类的 parseDate 方法）
 */
public class DateJsonDeserializer extends JsonDeserializer<Date> {

	/**
	 * 正则表达式：用于判断字符串是否为纯数字（即时间戳格式）
	 * ^[0-9]* 表示从头到尾都是数字
	 */
	private static final Pattern P = Pattern.compile("^[0-9]*");

	/**
	 * 支持的日期格式列表（静态常量）
	 * 按照索引顺序：0=年月, 1=年月日, 2=年月日时分, 3=年月日时分秒, 4=斜杠格式时分秒
	 */
	private static final List<String> FORMAT = new ArrayList<>(4);

	// 静态代码块：在类加载时初始化支持的日期格式
	static {
		FORMAT.add("yyyy-MM");              // 索引0：只到月份
		FORMAT.add("yyyy-MM-dd");           // 索引1：精确到天
		FORMAT.add("yyyy-MM-dd HH:mm");     // 索引2：精确到分钟
		FORMAT.add("yyyy-MM-dd HH:mm:ss"); // 索引3：精确到秒（最常用）
		FORMAT.add("yyyy/MM/dd HH:mm:ss"); // 索引4：用斜杠分隔的格式
	}

	/**
	 * Jackson 反序列化入口方法
	 *
	 * 当 Jackson 在反序列化过程中遇到 Date 类型的字段时，会自动调用此方法。
	 * 它从 JSON 解析器中读取原始字符串，然后交给 #parseDate(String) 进行解析。
	 *
	 * @param p    JSON 解析器，用于获取原始的 JSON 文本值
	 * @param ctxt 反序列化上下文（包含一些配置信息，此处未使用）
	 * @return 解析后的 Date 对象，如果输入为空则返回 null
	 * @throws IOException            读取 JSON 流时可能抛出的 IO 异常
	 * @throws JsonProcessingException JSON 格式错误时抛出
	 */
	@Override
	public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		return parseDate(p.getText());
	}

	/**
	 * 核心解析方法：将日期字符串解析为 Date 对象
	 *
	 * 解析策略（按优先级）：
	 * 
	 *   如果字符串为空或 null，直接返回 null
	 *   如果字符串是纯数字，视为时间戳（毫秒），调用 DateUtils.parse(Long) 解析
	 *   否则，依次尝试多种日期格式的正则匹配，匹配成功则用对应格式解析
	 *   如果所有格式都不匹配，抛出 IllegalArgumentException 异常
	 * @param str 待解析的日期字符串
	 * @return 解析后的 Date 对象；输入为空时返回 null
	 * @throws IllegalArgumentException 当字符串不匹配任何已知格式时抛出
	 */
	public static Date parseDate(String str) {
		Date convertDate = null;

		// 空值校验：null 或空字符串直接返回 null
		if (str == null || "".equals(str)) {
			return null;
		}

		// 分支1：纯数字 → 当作时间戳（毫秒）处理
		if (isNum(str)) {
			convertDate = DateUtils.parse(Long.valueOf(str));
		}
		else {
			// 分支2：按正则逐一匹配日期格式
			if (str.matches("^\\d{4}-\\d{1,2}$")) {
				// 匹配 "yyyy-MM" 格式，如 "2024-01"
				return DateUtils.parse(str, FORMAT.get(0));
			}
			else if (str.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$")) {
				// 匹配 "yyyy-MM-dd" 格式，如 "2024-01-15"
				return DateUtils.parse(str, FORMAT.get(1));
			}
			else if (str.matches("^\\d{4}-\\d{1,2}-\\d{1,2} {1}\\d{1,2}:\\d{1,2}$")) {
				// 匹配 "yyyy-MM-dd HH:mm" 格式，如 "2024-01-15 10:30"
				return DateUtils.parse(str, FORMAT.get(2));
			}
			else if (str.matches("^\\d{4}-\\d{1,2}-\\d{1,2} {1}\\d{1,2}:\\d{1,2}:\\d{1,2}$")) {
				// 匹配 "yyyy-MM-dd HH:mm:ss" 格式，如 "2024-01-15 10:30:45"
				return DateUtils.parse(str, FORMAT.get(3));
			}
			else if (str.matches("^\\d{4}/\\d{1,2}/\\d{1,2} {1}\\d{1,2}:\\d{1,2}:\\d{1,2}$")) {
				// 匹配 "yyyy/MM/dd HH:mm:ss" 格式，如 "2024/01/15 10:30:45"
				return DateUtils.parse(str, FORMAT.get(4));
			}
			else {
				// 所有格式都不匹配，抛出异常
				throw new IllegalArgumentException("Invalid boolean value '" + str + "'");
			}
		}

		return convertDate;
	}

	/**
	 * 判断字符串是否为纯数字
	 *
	 * 用于区分时间戳格式和日期字符串格式。
	 * 例如："1700000000000" 是纯数字，返回 true；
	 * "2024-01-15" 不是纯数字，返回 false。
	 *
	 * @param number 待检测的字符串
	 * @return 如果字符串全部由数字组成则返回 true，否则返回 false
	 */
	public static boolean isNum(String number) {
		Matcher m = P.matcher(number);
		return m.matches();
	}
}
