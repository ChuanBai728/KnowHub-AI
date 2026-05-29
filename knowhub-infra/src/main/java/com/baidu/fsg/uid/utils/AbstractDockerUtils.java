package com.baidu.fsg.uid.utils;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Docker环境工具类 - 检测和获取Docker容器环境信息
 *
 * 【类的作用】
 * 这个工具类用于检测应用是否运行在Docker容器中，并获取容器的主机和端口信息。
 * 在UID生成器中，这些信息可能用于：
 * 1. 生成Worker ID时考虑容器环境
 * 2. 日志记录时标识容器实例
 * 3. 调试时区分不同的容器实例
 *
 * 【环境变量说明】
 * - JPAAS_HOST: 容器的主机地址
 * - JPAAS_HTTP_PORT: 容器的HTTP端口
 * - JPAAS_HOST_PORT_8080: 容器的8080端口映射（备用）
 *
 * 【设计模式】
 * 使用了"静态工具类"模式：
 * - 所有方法都是静态的
 * - 通过静态初始化块在类加载时获取环境信息
 * - 避免重复获取，提高性能
 *
 * 【使用场景】
 * 在PaaS（平台即服务）环境中，应用通常运行在Docker容器中。
 * 这些环境变量由PaaS平台自动注入，用于标识容器的网络信息。
 */
public abstract class AbstractDockerUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDockerUtils.class);

    /** 环境变量Key：容器主机地址 */
    private static final String ENV_KEY_HOST = "JPAAS_HOST";

    /** 环境变量Key：容器HTTP端口 */
    private static final String ENV_KEY_PORT = "JPAAS_HTTP_PORT";

    /** 环境变量Key：容器8080端口映射（备用） */
    private static final String ENV_KEY_PORT_ORIGINAL = "JPAAS_HOST_PORT_8080";

    /** Docker容器的主机地址 */
    private static String DOCKER_HOST = "";

    /** Docker容器的端口 */
    private static String DOCKER_PORT = "";

    /** 是否运行在Docker容器中 */
    private static boolean IS_DOCKER;

    /**
     * 静态初始化块
     * 在类加载时自动执行，获取Docker环境信息
     */
    static {
        retrieveFromEnv();
    }

    /**
     * 获取Docker容器的主机地址
     *
     * @return 主机地址字符串，如果不是Docker环境则返回空字符串
     */
    public static String getDockerHost() {
        return DOCKER_HOST;
    }

    /**
     * 获取Docker容器的端口
     *
     * @return 端口字符串，如果不是Docker环境则返回空字符串
     */
    public static String getDockerPort() {
        return DOCKER_PORT;
    }

    /**
     * 判断是否运行在Docker容器中
     *
     * @return true表示在Docker中运行，false表示不在
     */
    public static boolean isDocker() {
        return IS_DOCKER;
    }

    /**
     * 从环境变量中获取Docker信息
     *
     * 【判断逻辑】
     * 1. 尝试获取JPAAS_HOST和JPAAS_HTTP_PORT环境变量
     * 2. 如果JPAAS_HTTP_PORT为空，尝试获取JPAAS_HOST_PORT_8080
     * 3. 如果host和port都有值，说明在Docker中
     * 4. 如果host和port都为空，说明不在Docker中
     * 5. 如果只有一个有值，说明配置错误，抛出异常
     */
    private static void retrieveFromEnv() {

        // 获取环境变量
        DOCKER_HOST = System.getenv(ENV_KEY_HOST);
        DOCKER_PORT = System.getenv(ENV_KEY_PORT);

        // 如果主端口为空，尝试备用端口
        if (StringUtils.isBlank(DOCKER_PORT)) {
            DOCKER_PORT = System.getenv(ENV_KEY_PORT_ORIGINAL);
        }

        boolean hasEnvHost = StringUtils.isNotBlank(DOCKER_HOST);
        boolean hasEnvPort = StringUtils.isNotBlank(DOCKER_PORT);

        // 判断是否在Docker环境中
        if (hasEnvHost && hasEnvPort) {
            // host和port都有值，说明在Docker中
            IS_DOCKER = true;

        } else if (!hasEnvHost && !hasEnvPort) {
            // host和port都为空，说明不在Docker中
            IS_DOCKER = false;

        } else {
            // 只有一个有值，配置错误
            LOGGER.error("Missing host or port from env for Docker. host:{}, port:{}", DOCKER_HOST, DOCKER_PORT);
            throw new RuntimeException(
                    "Missing host or port from env for Docker. host:" + DOCKER_HOST + ", port:" + DOCKER_PORT);
        }
    }

}
