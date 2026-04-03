package com.cq.panel.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 *
 * @author cq 2026/4/3 16:59
 * @since 1.0.0
 */
public class IpUtils {

    private static final Logger log = LoggerFactory.getLogger(IpUtils.class);

    public static String getLocalHost() throws SocketException, UnknownHostException {
        try {
            // 方法1: 尝试获取非回环地址
            java.net.InetAddress address = getPreferredInetAddress();
            if (address != null && !address.isLoopbackAddress()) {
                return address.getHostAddress();
            }

            // 方法2: 获取所有网络接口的IP地址
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }

            // 方法3: 回退到传统方式
            return java.net.InetAddress.getLocalHost().getHostAddress();

        } catch (Exception e) {
            log.error("无法获取本地IP地址", e);
            throw e;
        }
    }

    /**
     * 获取首选网络地址，参考Spring Cloud的实现
     */
    private static java.net.InetAddress getPreferredInetAddress() {
        try {
            // 优先获取非回环的IPv4地址
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (shouldIgnoreInterface(networkInterface)) {
                    continue;
                }

                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (isPreferredAddress(address)) {
                        return address;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("获取首选网络地址失败", e);
        }
        return null;
    }

    /**
     * 判断是否应该忽略该网络接口
     */
    private static boolean shouldIgnoreInterface(java.net.NetworkInterface networkInterface) {
        try {
            return networkInterface.isLoopback() ||
                    !networkInterface.isUp() ||
                    networkInterface.isVirtual() ||
                    networkInterface.isPointToPoint();
        } catch (SocketException e) {
            return true;
        }
    }

    /**
     * 判断是否为优选地址
     */
    private static boolean isPreferredAddress(java.net.InetAddress address) {
        return !address.isLoopbackAddress() &&
                address instanceof java.net.Inet4Address &&
                !address.isLinkLocalAddress() &&
                !address.isMulticastAddress();
    }
}
