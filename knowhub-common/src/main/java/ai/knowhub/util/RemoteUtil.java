package ai.knowhub.util;

import org.apache.commons.lang.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 【远程客户端识别工具类】
 *
 * 作用：从 HTTP 请求中提取客户端的唯一标识（IP + User-Agent），用于：
 *   - 限流：识别同一客户端的请求频率。
 *   - 防重复提交：判断是否为同一客户端的重复请求。
 *   - 审计日志：记录操作来源。
 *
 * 实现原理：
 *   将客户端 IP 地址和 User-Agent 拼接为一个字符串作为客户端标识。
 *   同一设备在同一网络环境下，这个组合基本是唯一的。
 *
 * 注意事项：
 *   - 如果请求经过反向代理（如 Nginx），真实 IP 在 X-Forwarded-For 头中。
 *   - X-Forwarded-For 可能包含多个 IP（代理链），取第一个（最初的客户端 IP）。
 */
public class RemoteUtil {

    /**
     * 从 HTTP 请求中获取客户端唯一标识
     * 标识格式：IP地址 + User-Agent字符串
     *
     * 获取 IP 的优先级：
     *   1. X-Forwarded-For 头（经过代理时的真实客户端 IP）
     *   2. request.getRemoteAddr()（直连时的 IP）
     *
     * @param request HTTP 请求对象
     * @return 客户端唯一标识字符串（IP + User-Agent）
     */
    public static String getRemoteId(HttpServletRequest request) {
        // 从代理头中获取真实 IP
        String forward = request.getHeader("X-Forwarded-For");
        String ip = getRemoteIpFromForward(forward);
        // 获取浏览器/客户端的 User-Agent 标识
        String ua = request.getHeader("user-agent");
        if (StringUtils.isNotBlank(ip)) {
            return ip + ua;
        }
        // 如果没有代理头，直接获取连接 IP
        return request.getRemoteAddr() + ua;
    }

    /**
     * 从 X-Forwarded-For 头中提取客户端真实 IP
     * X-Forwarded-For 格式：client, proxy1, proxy2
     * 取第一个 IP 即为最初的客户端 IP。
     *
     * @param forward X-Forwarded-For 头的值
     * @return 客户端 IP 地址，如果 header 为空则返回 null
     */
    private static String getRemoteIpFromForward(String forward) {
        if (StringUtils.isNotBlank(forward)) {
            // 按逗号分割，取第一个 IP（最初的客户端 IP）
            String[] ipList = forward.split(",");
            return StringUtils.trim(ipList[0]);
        }
        return null;
    }
}
