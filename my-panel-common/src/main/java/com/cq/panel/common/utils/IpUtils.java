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
    private static volatile String cachedLocalIp = null;

    public static String getLocalHost() throws SocketException, UnknownHostException {
        if (cachedLocalIp != null) {
            return cachedLocalIp;
        }
        synchronized (IpUtils.class) {
            if (cachedLocalIp != null) {
                return cachedLocalIp;
            }
            try {
                String ip = resolveLocalIp();
                cachedLocalIp = ip;
                log.info("Resolved and cached local IP: {}", ip);
                return ip;
            } catch (Exception e) {
                log.error("无法获取本地IP地址", e);
                throw e;
            }
        }
    }

    private static String resolveLocalIp() throws SocketException, UnknownHostException {
        try {
            java.net.InetAddress address = getPreferredInetAddress();
            if (address != null && !address.isLoopbackAddress()) {
                return address.getHostAddress();
            }

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

            return java.net.InetAddress.getLocalHost().getHostAddress();

        } catch (Exception e) {
            log.error("无法获取本地IP地址", e);
            throw e;
        }
    }

    /**
     * 获取首选网络地址，参考Spring Cloud的实现
     * 优先选择静态IP，过滤DHCP分配的IP
     */
    private static java.net.InetAddress getPreferredInetAddress() {
        try {
            java.util.List<NetworkInterfaceInfo> candidates = new java.util.ArrayList<>();
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
                        candidates.add(new NetworkInterfaceInfo(networkInterface, address));
                    }
                }
            }
            
            if (candidates.isEmpty()) {
                return null;
            }
            
            candidates.sort((a, b) -> {
                int scoreA = calculateInterfaceScore(a.networkInterface);
                int scoreB = calculateInterfaceScore(b.networkInterface);
                return Integer.compare(scoreB, scoreA);
            });
            
            return candidates.getFirst().address;
        } catch (Exception e) {
            log.debug("获取首选网络地址失败", e);
        }
        return null;
    }
    
    private static class NetworkInterfaceInfo {
        final java.net.NetworkInterface networkInterface;
        final java.net.InetAddress address;
        
        NetworkInterfaceInfo(java.net.NetworkInterface networkInterface, java.net.InetAddress address) {
            this.networkInterface = networkInterface;
            this.address = address;
        }
    }

    @SuppressWarnings("all")
    private static int calculateInterfaceScore(java.net.NetworkInterface networkInterface) {
        int score = 0;
        String name = networkInterface.getName().toLowerCase();
        
        try {
            if (networkInterface.getHardwareAddress() != null && networkInterface.getHardwareAddress().length > 0) {
                score += 100;
            }
            
            if (networkInterface.getMTU() == 1500) {
                score += 50;
            }
            
            if (name.matches("eth\\d+") || name.matches("ens\\d+") || name.matches("enp\\d+s\\d+")) {
                score += 200;
            } else if (name.matches("em\\d+")) {
                score += 180;
            } else if (name.startsWith("bond")) {
                score += 150;
            } else if (name.startsWith("wlan") || name.startsWith("wlx")) {
                score -= 100;
            }
            
            try {
                java.lang.reflect.Method speedMethod = java.net.NetworkInterface.class.getMethod("getSpeed");
                Long speed = (Long) speedMethod.invoke(networkInterface);
                if (speed != null && speed > 0) {
                    score += (int) Math.min(speed / 100000000, 100);
                }
            } catch (Exception e) {
                //
            }
            
        } catch (Exception e) {
            log.debug("计算网卡评分失败: {}", name, e);
        }
        
        return score;
    }

    /**
     * 判断是否应该忽略该网络接口
     */
    private static boolean shouldIgnoreInterface(java.net.NetworkInterface networkInterface) {
        try {
            String displayName = networkInterface.getDisplayName().toLowerCase();
            String name = networkInterface.getName().toLowerCase();
            
            // 基础过滤条件
            if (networkInterface.isLoopback() ||
                    !networkInterface.isUp() ||
                    networkInterface.isPointToPoint()) {
                return true;
            }
            
            // 过滤Docker相关网卡
            if (isDockerInterface(displayName, name)) {
                log.debug("忽略Docker网卡: {} ({})", displayName, name);
                return true;
            }
            
            // 过滤其他虚拟网卡
            if (isVirtualInterface(displayName, name)) {
                log.debug("忽略虚拟网卡: {} ({})", displayName, name);
                return true;
            }
            
            return false;
            
        } catch (SocketException e) {
            return true;
        }
    }
    
    /**
     * 判断是否为Docker网卡
     */
    private static boolean isDockerInterface(String displayName, String name) {
        // Docker网卡名称模式
        String[] dockerPatterns = {
            "docker", "veth", "br-", "cni", "flannel", "calico", 
            "weave", "kube", "k8s", "container", "podman"
        };
        
        for (String pattern : dockerPatterns) {
            if (displayName.contains(pattern) || name.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 判断是否为虚拟网卡
     */
    private static boolean isVirtualInterface(String displayName, String name) {
        // 虚拟网卡名称模式
        String[] virtualPatterns = {
            "virtual", "vmware", "virtualbox", "vbox", "hyper-v",
            "vpn", "tun", "tap", "virbr", "vnet"
        };
        
        for (String pattern : virtualPatterns) {
            if (displayName.contains(pattern) || name.contains(pattern)) {
                return true;
            }
        }
        
        // 检查是否为虚拟接口
        try {
            java.lang.reflect.Method isVirtualMethod = java.net.NetworkInterface.class.getMethod("isVirtual");
            Boolean isVirtual = (Boolean) isVirtualMethod.invoke(java.net.NetworkInterface.getByName(name));
            if (isVirtual != null && isVirtual) {
                return true;
            }
        } catch (Exception e) {
            // 忽略反射异常
        }
        
        return false;
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

    public static void main(String[] args) throws SocketException, UnknownHostException {
        System.out.println(getLocalHost());
    }
}