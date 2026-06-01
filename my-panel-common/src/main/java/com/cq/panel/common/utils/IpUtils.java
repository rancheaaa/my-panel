package com.cq.panel.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class IpUtils {

    private static final Logger log = LoggerFactory.getLogger(IpUtils.class);
    private static final AtomicReference<String> cachedLocalIp = new AtomicReference<>();

    public static String getLocalHost() throws SocketException, UnknownHostException {
        String cached = cachedLocalIp.get();
        if (cached != null) {
            return cached;
        }
        
        synchronized (IpUtils.class) {
            cached = cachedLocalIp.get();
            if (cached != null) {
                return cached;
            }
            try {
                String ip = resolveLocalIp();
                cachedLocalIp.set(ip);
                log.info("Resolved and cached local IP: {}", ip);
                return ip;
            } catch (Exception e) {
                log.error("无法获取本地IP地址", e);
                throw e;
            }
        }
    }

    public static void clearCache() {
        cachedLocalIp.set(null);
        log.info("IP cache cleared");
    }

    private static String resolveLocalIp() throws SocketException, UnknownHostException {
        InetAddress address = getPreferredInetAddress();
        if (address != null && !address.isLoopbackAddress()) {
            return address.getHostAddress();
        }

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }

        return InetAddress.getLocalHost().getHostAddress();
    }

    private static InetAddress getPreferredInetAddress() {
        try {
            List<NetworkInterfaceInfo> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (shouldIgnoreInterface(networkInterface)) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (isPreferredAddress(address)) {
                        candidates.add(new NetworkInterfaceInfo(networkInterface, address));
                    }
                }
            }
            
            if (candidates.isEmpty()) {
                log.warn("No suitable network interface found");
                return null;
            }
            
            log.debug("Found {} candidate network interfaces", candidates.size());
            for (NetworkInterfaceInfo info : candidates) {
                int score = calculateInterfaceScore(info.networkInterface);
                log.debug("Interface: {} ({}) - IP: {} - Score: {}", 
                    info.networkInterface.getName(), 
                    info.networkInterface.getDisplayName(),
                    info.address.getHostAddress(),
                    score);
            }
            
            candidates.sort((a, b) -> {
                int scoreA = calculateInterfaceScore(a.networkInterface);
                int scoreB = calculateInterfaceScore(b.networkInterface);
                return Integer.compare(scoreB, scoreA);
            });
            
            NetworkInterfaceInfo selected = candidates.get(0);
            log.info("Selected network interface: {} ({}) - IP: {}", 
                selected.networkInterface.getName(),
                selected.networkInterface.getDisplayName(),
                selected.address.getHostAddress());
            
            return selected.address;
        } catch (Exception e) {
            log.error("获取首选网络地址失败", e);
        }
        return null;
    }
    
    static class NetworkInterfaceInfo {
        final NetworkInterface networkInterface;
        final InetAddress address;
        
        NetworkInterfaceInfo(NetworkInterface networkInterface, InetAddress address) {
            this.networkInterface = networkInterface;
            this.address = address;
        }
    }

    @SuppressWarnings("all")
    static int calculateInterfaceScore(NetworkInterface networkInterface) {
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
            } else if (isWindowsPhysicalAdapter(name)) {
                score += 190;
            } else if (name.startsWith("wlan") || name.startsWith("wlx")) {
                score -= 100;
            }
            
            try {
                java.lang.reflect.Method speedMethod = NetworkInterface.class.getMethod("getSpeed");
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

    private static boolean isWindowsPhysicalAdapter(String name) {
        String[] windowsPhysicalPatterns = {
            "ethernet", "wi-fi", "wifi", "local", "连接"
        };
        
        for (String pattern : windowsPhysicalPatterns) {
            if (name.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    private static boolean shouldIgnoreInterface(NetworkInterface networkInterface) {
        try {
            String displayName = networkInterface.getDisplayName().toLowerCase();
            String name = networkInterface.getName().toLowerCase();
            
            if (networkInterface.isLoopback() ||
                    !networkInterface.isUp() ||
                    networkInterface.isPointToPoint()) {
                return true;
            }
            
            if (isDockerInterface(displayName, name)) {
                log.debug("忽略Docker网卡: {} ({})", displayName, name);
                return true;
            }
            
            if (isVirtualInterface(displayName, name)) {
                log.debug("忽略虚拟网卡: {} ({})", displayName, name);
                return true;
            }
            
            return false;
            
        } catch (SocketException e) {
            return true;
        }
    }
    
    private static boolean isDockerInterface(String displayName, String name) {
        String[] dockerPatterns = {
            "docker", "veth", "br-", "cni", "flannel", "calico", 
            "weave", "kube", "k8s", "container", "podman",
            "vEthernet", "wsl", "hyper-v switch"
        };
        
        for (String pattern : dockerPatterns) {
            if (displayName.contains(pattern) || name.contains(pattern)) {
                return true;
            }
        }
        
        if (isDockerInternalNetwork(name, displayName)) {
            return true;
        }
        
        return false;
    }

    private static boolean isDockerInternalNetwork(String name, String displayName) {
        if (name.matches("br-[a-f0-9]{12}")) {
            return true;
        }
        
        if (name.matches("veth[0-9a-f]+")) {
            return true;
        }
        
        if (displayName.contains("Docker NAT") || displayName.contains("docker0")) {
            return true;
        }
        
        if (name.startsWith("virbr")) {
            return true;
        }
        
        return false;
    }
    
    private static boolean isVirtualInterface(String displayName, String name) {
        String[] virtualPatterns = {
            "virtual", "vmware", "virtualbox", "vbox",
            "vpn", "tun", "tap", "virbr", "vnet",
            "loopback", "hamachi"
        };
        
        for (String pattern : virtualPatterns) {
            if (displayName.toLowerCase().contains(pattern) || name.toLowerCase().contains(pattern)) {
                return true;
            }
        }
        
        try {
            java.lang.reflect.Method isVirtualMethod = NetworkInterface.class.getMethod("isVirtual");
            Boolean isVirtual = (Boolean) isVirtualMethod.invoke(NetworkInterface.getByName(name));
            if (isVirtual != null && isVirtual) {
                return true;
            }
        } catch (Exception e) {
            // 忽略反射异常
        }
        
        return false;
    }

    private static boolean isPreferredAddress(InetAddress address) {
        return !address.isLoopbackAddress() &&
                address instanceof Inet4Address &&
                !address.isLinkLocalAddress() &&
                !address.isMulticastAddress();
    }

    public static void main(String[] args) throws SocketException, UnknownHostException {
        System.out.println(getLocalHost());
    }
}
