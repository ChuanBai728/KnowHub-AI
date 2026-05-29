package ai.knowhub.parser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 LocalVariableTable 的参数名发现器
 *
 * 【作用】：通过 ASM 字节码框架读取 .class 文件中的 LocalVariableTable（局部变量表），
 *          从而获取方法的参数名。这是在编译时未指定 -parameters 选项时的备选方案。
 *
 * 【工作原理】：
 * 1. 获取类的 .class 文件输入流
 * 2. 使用 ASM ClassReader 解析字节码
 * 3. 通过自定义的 ClassVisitor 遍历每个方法
 * 4. 在方法的 LocalVariableTable 中找到参数对应的变量名
 * 5. 将结果缓存在 ConcurrentHashMap 中，避免重复解析
 *
 * 【关键概念】：
 * - ASM：Java 字节码操作框架，可以直接读取和修改 .class 文件
 * - LocalVariableTable：.class 文件中存储局部变量信息的表，包含变量名、类型、作用域等
 * - BridgeMethod：Java 泛型擦除后编译器生成的桥接方法
 * - ClassVisitor / MethodVisitor：ASM 的访问者模式，用于遍历字节码结构
 *
 * 【设计模式】：访问者模式（Visitor Pattern）—— 通过 ASM 的 ClassVisitor/MethodVisitor 遍历字节码
 *
 * 【注意】：此类是从 Spring Framework 源码中提取的，用于兼容旧版 Spring Boot。
 *          在新版 Spring 中可能已被废弃。
 */
public class LocalVariableTableParameterNameDiscoverer implements ParameterNameDiscoverer {

	private static final Log logger = LogFactory.getLog(LocalVariableTableParameterNameDiscoverer.class);

	/**
	 * 空映射常量，用于标记某个类没有调试信息（无法获取参数名）
	 */
	private static final Map<Executable, String[]> NO_DEBUG_INFO_MAP = Collections.emptyMap();

	/**
	 * 参数名缓存：key 为 Class，value 为 Map<方法/构造器, 参数名数组>
	 * 使用 ConcurrentHashMap 保证线程安全
	 */
	private final Map<Class<?>, Map<Executable, String[]>> parameterNamesCache = new ConcurrentHashMap<>(32);

	/**
	 * 获取方法的参数名数组
	 *
	 * @param method 方法对象
	 * @return 参数名数组，如果无法获取则返回 null
	 */
	@Override
	@Nullable
	public String[] getParameterNames(Method method) {
		// 先解析桥接方法，获取原始方法
		Method originalMethod = BridgeMethodResolver.findBridgedMethod(method);
		return doGetParameterNames(originalMethod);
	}

	/**
	 * 获取构造器的参数名数组
	 *
	 * @param ctor 构造器对象
	 * @return 参数名数组，如果无法获取则返回 null
	 */
	@Override
	@Nullable
	public String[] getParameterNames(Constructor<?> ctor) {
		return doGetParameterNames(ctor);
	}

	/**
	 * 获取可执行对象（方法或构造器）的参数名
	 *
	 * @param executable 方法或构造器
	 * @return 参数名数组，如果无法获取则返回 null
	 */
	@Nullable
	private String[] doGetParameterNames(Executable executable) {
		Class<?> declaringClass = executable.getDeclaringClass();
		// 使用 computeIfAbsent 保证每个类只解析一次
		Map<Executable, String[]> map = this.parameterNamesCache.computeIfAbsent(declaringClass, this::inspectClass);
		return (map != NO_DEBUG_INFO_MAP ? map.get(executable) : null);
	}

	/**
	 * 解析类的所有方法和构造器的参数名
	 *
	 * @param clazz 要解析的类
	 * @return 方法/构造器 -> 参数名数组 的映射，如果无法解析返回 NO_DEBUG_INFO_MAP
	 */
	private Map<Executable, String[]> inspectClass(Class<?> clazz) {
		// 获取 .class 文件的输入流
		InputStream is = clazz.getResourceAsStream(ClassUtils.getClassFileName(clazz));
		if (is == null) {
			// 找不到 .class 文件
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot find '.class' file for class [" + clazz +
						"] - unable to determine constructor/method parameter names");
			}
			return NO_DEBUG_INFO_MAP;
		}

		try {
			// 使用 ASM 读取字节码
			ClassReader classReader = new ClassReader(is);
			Map<Executable, String[]> map = new ConcurrentHashMap<>(32);
			// 使用自定义的 ClassVisitor 遍历类结构
			classReader.accept(new ParameterNameDiscoveringVisitor(clazz, map), 0);
			if (logger.isWarnEnabled()) {
				logger.warn("Using deprecated '-debug' fallback for parameter name resolution. Compile the " +
						"affected code with '-parameters' instead or avoid its introspection: " + clazz.getName());
			}
			return map;
		}
		catch (IOException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Exception thrown while reading '.class' file for class [" + clazz +
						"] - unable to determine constructor/method parameter names", ex);
			}
		}
		catch (IllegalArgumentException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("ASM ClassReader failed to parse class file [" + clazz +
						"], probably due to a new Java class file version that isn't supported yet " +
						"- unable to determine constructor/method parameter names", ex);
			}
		}
		finally {
			try {
				is.close();
			}
			catch (IOException ex) {
				// 忽略关闭异常
			}
		}
		return NO_DEBUG_INFO_MAP;
	}

	/**
	 * 类访问者：遍历类中的所有方法，为每个方法创建 LocalVariableTableVisitor
	 *
	 * 【设计模式】：访问者模式（Visitor Pattern）
	 */
	private static class ParameterNameDiscoveringVisitor extends ClassVisitor {

		/** 静态初始化块的方法名（<clinit>），不需要参数名 */
		private static final String STATIC_CLASS_INIT = "<clinit>";

		/** 当前正在访问的类 */
		private final Class<?> clazz;

		/** 存储方法 -> 参数名 的映射 */
		private final Map<Executable, String[]> executableMap;

		public ParameterNameDiscoveringVisitor(Class<?> clazz, Map<Executable, String[]> executableMap) {
			super(SpringAsmInfo.ASM_VERSION);
			this.clazz = clazz;
			this.executableMap = executableMap;
		}

		/**
		 * 访问类中的每个方法
		 *
		 * @param access     方法访问标志
		 * @param name       方法名
		 * @param desc       方法描述符（参数类型和返回类型）
		 * @param signature  泛型签名
		 * @param exceptions 异常类型
		 * @return MethodVisitor 用于进一步访问方法内部
		 */
		@Override
		@Nullable
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			// 跳过合成方法、桥接方法和静态初始化块
			if (!isSyntheticOrBridged(access) && !STATIC_CLASS_INIT.equals(name)) {
				return new LocalVariableTableVisitor(this.clazz, this.executableMap, name, desc, isStatic(access));
			}
			return null;
		}

		/**
		 * 判断方法是否是合成方法或桥接方法
		 * 【说明】：合成方法是编译器自动生成的（如内部类访问外部类的私有字段），
		 *          桥接方法是泛型擦除后生成的。
		 */
		private static boolean isSyntheticOrBridged(int access) {
			return (((access & Opcodes.ACC_SYNTHETIC) | (access & Opcodes.ACC_BRIDGE)) > 0);
		}

		/**
		 * 判断方法是否是静态方法
		 */
		private static boolean isStatic(int access) {
			return ((access & Opcodes.ACC_STATIC) > 0);
		}
	}

	/**
	 * 方法访问者：从方法的 LocalVariableTable 中提取参数名
	 *
	 * 【LocalVariableTable 说明】：
	 * Java 字节码中每个方法都有一个局部变量表，记录了所有局部变量的信息。
	 * 方法参数也是局部变量，它们在表中的顺序和索引可以通过方法描述符计算出来。
	 */
	private static class LocalVariableTableVisitor extends MethodVisitor {

		/** 构造器方法名（<init>） */
		private static final String CONSTRUCTOR = "<init>";

		/** 当前正在访问的类 */
		private final Class<?> clazz;

		/** 存储方法 -> 参数名 的映射 */
		private final Map<Executable, String[]> executableMap;

		/** 方法名 */
		private final String name;

		/** 方法参数类型数组（从方法描述符解析） */
		private final Type[] args;

		/** 参数名数组（结果） */
		private final String[] parameterNames;

		/** 是否是静态方法 */
		private final boolean isStatic;

		/** 是否找到了 LocalVariableTable 信息 */
		private boolean hasLvtInfo = false;

		/**
		 * 局部变量表中的槽位索引数组
		 * 【说明】：long 和 double 占 2 个槽位，其他类型占 1 个槽位。
		 *          实例方法的 0 号槽位是 this 引用。
		 */
		private final int[] lvtSlotIndex;

		public LocalVariableTableVisitor(Class<?> clazz, Map<Executable, String[]> map, String name, String desc, boolean isStatic) {
			super(SpringAsmInfo.ASM_VERSION);
			this.clazz = clazz;
			this.executableMap = map;
			this.name = name;
			this.args = Type.getArgumentTypes(desc);
			this.parameterNames = new String[this.args.length];
			this.isStatic = isStatic;
			this.lvtSlotIndex = computeLvtSlotIndices(isStatic, this.args);
		}

		/**
		 * 访问局部变量表中的每个变量
		 *
		 * @param name        变量名
		 * @param description 变量类型描述
		 * @param signature   泛型签名
		 * @param start       变量作用域起始标签
		 * @param end         变量作用域结束标签
		 * @param index       变量在局部变量表中的槽位索引
		 */
		@Override
		public void visitLocalVariable(String name, String description, String signature, Label start, Label end, int index) {
			this.hasLvtInfo = true;
			// 遍历参数的槽位索引，找到匹配的变量名
			for (int i = 0; i < this.lvtSlotIndex.length; i++) {
				if (this.lvtSlotIndex[i] == index) {
					this.parameterNames[i] = name;
				}
			}
		}

		/**
		 * 方法访问结束时，将解析结果存入映射
		 */
		@Override
		public void visitEnd() {
			if (this.hasLvtInfo || result()) {
				this.executableMap.put(resolveExecutable(), this.parameterNames);
			}
		}

		/**
		 * 判断是否是无参数的静态方法（这种情况下没有参数需要匹配）
		 */
		public boolean result(){
			return this.isStatic && this.parameterNames.length == 0;
		}

		/**
		 * 将当前访问的方法解析为 Java 反射的 Method 或 Constructor 对象
		 */
		private Executable resolveExecutable() {
			ClassLoader loader = this.clazz.getClassLoader();
			Class<?>[] argTypes = new Class<?>[this.args.length];
			for (int i = 0; i < this.args.length; i++) {
				argTypes[i] = ClassUtils.resolveClassName(this.args[i].getClassName(), loader);
			}
			try {
				if (CONSTRUCTOR.equals(this.name)) {
					return this.clazz.getDeclaredConstructor(argTypes);
				}
				return this.clazz.getDeclaredMethod(this.name, argTypes);
			}
			catch (NoSuchMethodException ex) {
				throw new IllegalStateException("Method [" + this.name +
						"] was discovered in the .class file but cannot be resolved in the class object", ex);
			}
		}

		/**
		 * 计算每个参数在局部变量表中的槽位索引
		 *
		 * 【规则】：
		 * - 实例方法：0 号槽位是 this，参数从 1 开始
		 * - 静态方法：参数从 0 开始
		 * - long 和 double 类型占 2 个槽位
		 *
		 * @param isStatic   是否是静态方法
		 * @param paramTypes 参数类型数组
		 * @return 槽位索引数组
		 */
		private static int[] computeLvtSlotIndices(boolean isStatic, Type[] paramTypes) {
			int[] lvtIndex = new int[paramTypes.length];
			int nextIndex = (isStatic ? 0 : 1);
			for (int i = 0; i < paramTypes.length; i++) {
				lvtIndex[i] = nextIndex;
				if (isWideType(paramTypes[i])) {
					nextIndex += 2;  // long/double 占 2 个槽位
				}
				else {
					nextIndex++;
				}
			}
			return lvtIndex;
		}

		/**
		 * 判断是否是宽类型（long 或 double）
		 * 【说明】：在 JVM 中，long 和 double 占用 2 个局部变量槽位
		 */
		private static boolean isWideType(Type aType) {
			return (aType == Type.LONG_TYPE || aType == Type.DOUBLE_TYPE);
		}
	}

}
