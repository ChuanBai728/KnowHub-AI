package com.baidu.fsg.uid.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * 网络工具类 - 获取本机网络信息
 *
 * 【类的作用】
 * 提供获取本机IP地址的功能，在UID生成器中用于：
 * 1. 生成Worker ID时可能使用IP地址作为输入
 * 2. 日志记录时标识当前服务器
 * 3. 分布式环境下的节点识别
 *
 * 【设计模式】
 * 使用了"静态工具类"模式：
 * - 所有方法都是静态的
 * - 通过静态初始化块在类加载时获取本机IP
 * - 缓存结果，避免重复获取
 *
 * 【IP地址获取逻辑】
 * 1. 遍历所有网络接口
     * 2. 排除回环接口（loopback，如127.0.0.1）
 * 3. 排除链路本地地址（link-local）
 * 4. 排除通配地址（any-local，如0.0.0.0）
 * 5. 返回第一个有效的非回环IP地址
 *
 * 【注意事项】
 * - 在多网卡环境下，可能返回的不是期望的IP地址
 * - 在Docker容器中，可能返回容器内部的IP
 * - 如果获取失败，会抛出RuntimeException
 */
public abstract class AbstractNetUtils {

    /**
     * 本机IP地址对象
     * 在类加载时通过静态初始化块获取并缓存
     */
    public static InetAddress localAddress;

    /**
     * 静态初始化块
     * 在类加载时自动执行，获取本机IP地址
     */
    static {
        try {
            localAddress = getLocalInetAddress();
        } catch (SocketException e) {
            throw new RuntimeException("fail to get local ip.");
        }
    }

    /**
     * 获取本机的InetAddress对象
     *
     * @return 本机的InetAddress对象
     * @throws SocketException 当网络接口访问失败时抛出
     *
     * 【实现逻辑】
     * 遍历所有网络接口，找到第一个有效的非回环IP地址
     */
    public static InetAddress getLocalInetAddress() throws SocketException {

        // 获取所有网络接口
        Enumeration<NetworkInterface> enu = NetworkInterface.getNetworkInterfaces();

        while (enu.hasMoreElements()) {
            NetworkInterface ni = enu.nextElement();
            // 跳过回环接口（如127.0.0.1）
            if (ni.isLoopback()) {
                continue;
            }

            // 获取该接口的所有IP地址
            Enumeration<InetAddress> addressEnumeration = ni.getInetAddresses();
            while (addressEnumeration.hasMoreElements()) {
                InetAddress address = addressEnumeration.nextElement();

                // 跳过链路本地地址、回环地址和通配地址
                if (address.isLinkLocalAddress() || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                    continue;
                }

                // 返回第一个有效的IP地址
                return address;
            }
        }

        // 如果没有找到有效的IP地址，抛出异常
        throw new RuntimeException("No validated local address!");
    }

    /**
     * 获取本机IP地址的字符串表示
     *
     * @return IP地址字符串，例如："192.168.1.100"
     */
    public static String getLocalAddress() {
        return localAddress.getHostAddress();
    }

}
