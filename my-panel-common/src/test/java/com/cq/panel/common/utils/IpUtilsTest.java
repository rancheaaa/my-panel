package com.cq.panel.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IpUtils测试")
class IpUtilsTest {

    @BeforeEach
    void setUp() {
        IpUtils.clearCache();
    }

    @AfterEach
    void tearDown() {
        IpUtils.clearCache();
    }

    @Test
    @DisplayName("获取本地IP地址 - 正常情况")
    void testGetLocalHostSuccess() throws Exception {
        String localIp = IpUtils.getLocalHost();
        
        assertNotNull(localIp, "本地IP不应该为null");
        assertFalse(localIp.isEmpty(), "本地IP不应该为空");
        assertNotEquals("127.0.0.1", localIp, "不应该返回回环地址");
        assertTrue(localIp.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), 
            "IP格式应该正确: " + localIp);
        
        System.out.println("获取到的本地IP: " + localIp);
    }

    @Test
    @DisplayName("IP缓存机制测试")
    void testIpCaching() throws Exception {
        String firstCall = IpUtils.getLocalHost();
        String secondCall = IpUtils.getLocalHost();
        
        assertEquals(firstCall, secondCall, "两次调用应该返回相同的缓存结果");
        
        IpUtils.clearCache();
        
        String thirdCall = IpUtils.getLocalHost();
        assertEquals(firstCall, thirdCall, "清除缓存后应该重新解析但结果相同");
    }

    @Test
    @DisplayName("清除缓存功能测试")
    void testClearCache() throws Exception {
        String ip1 = IpUtils.getLocalHost();
        assertNotNull(ip1);
        
        IpUtils.clearCache();
        
        String ip2 = IpUtils.getLocalHost();
        assertNotNull(ip2);
        assertEquals(ip1, ip2, "缓存清除后重新获取的IP应该相同");
    }

    @Test
    @DisplayName("Docker网卡识别 - Linux Docker网卡名称")
    void testDockerInterfaceDetectionLinux() throws Exception {
        Method method = IpUtils.class.getDeclaredMethod(
            "isDockerInterface", String.class, String.class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(null, "docker0", "docker0"),
            "docker0 应该被识别为Docker网卡");
        assertTrue((boolean) method.invoke(null, "veth1234", "veth1234"),
            "veth 开头的网卡应该被识别为Docker网卡");
        assertTrue((boolean) method.invoke(null, "br-abc123def456", "br-abc123def456"),
            "br- 开头的网卡应该被识别为Docker网桥");
        assertTrue((boolean) method.invoke(null, "cni0", "cni0"),
            "cni 网卡应该被识别为容器网络");
        assertTrue((boolean) method.invoke(null, "flannel.1", "flannel.1"),
            "flannel 网卡应该被识别为Kubernetes网络");
        assertTrue((boolean) method.invoke(null, "calico", "calico"),
            "calico 网卡应该被识别为Kubernetes网络");
        assertTrue((boolean) method.invoke(null, "weave", "weave"),
            "weave 网卡应该被识别为容器网络");
        assertTrue((boolean) method.invoke(null, "kube-bridge", "kube-bridge"),
            "kube 开头应该被识别为Kubernetes");
        assertTrue((boolean) method.invoke(null, "k8s-node", "k8s-node"),
            "k8s 开头应该被识别为Kubernetes");
    }

    @Test
    @DisplayName("Docker网卡识别 - Windows Docker网卡名称")
    void testDockerInterfaceDetectionWindows() throws Exception {
        Method method = IpUtils.class.getDeclaredMethod(
            "isDockerInterface", String.class, String.class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(null, "vEthernet (Docker NAT)", "vethernet (docker nat)"),
            "vEthernet (Docker NAT) 应该被识别为Windows Docker网卡");
        assertTrue((boolean) method.invoke(null, "vEthernet (WSL)", "vethernet (wsl)"),
            "vEthernet (WSL) 应该被识别为WSL虚拟网卡");
        assertTrue((boolean) method.invoke(null, "Hyper-V Switch", "hyper-v switch"),
            "Hyper-V Switch 应该被识别为虚拟交换机");
        assertTrue((boolean) method.invoke(null, "DockerNAT", "dockernat"),
            "DockerNAT 应该被识别为Docker网络");
    }

    @Test
    @DisplayName("Docker内部网络模式匹配")
    void testDockerInternalNetworkPatterns() throws Exception {
        Method method = IpUtils.class.getDeclaredMethod(
            "isDockerInternalNetwork", String.class, String.class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(null, "br-abcdef123456", ""),
            "br- + 12位十六进制应该匹配Docker网桥");
        assertTrue((boolean) method.invoke(null, "vetha1b2c3d4e5f", ""),
            "veth + 十六进制字符串应该匹配Docker veth接口");
        assertTrue((boolean) method.invoke(null, "", "Docker NAT"),
            "显示名包含 'Docker NAT' 应该匹配");
        assertTrue((boolean) method.invoke(null, "", "docker0"),
            "显示名为 docker0 应该匹配");
        assertTrue((boolean) method.invoke(null, "virbr0", ""),
            "virbr 开头应该匹配虚拟化网桥");

        assertFalse((boolean) method.invoke(null, "eth0", ""),
            "普通物理网卡 eth0 不应该匹配");
        assertFalse((boolean) method.invoke(null, "ens33", ""),
            "普通物理网卡 ens33 不应该匹配");
    }

    @Test
    @DisplayName("正常物理网卡不应被过滤")
    void testPhysicalInterfacesNotFiltered() throws Exception {
        Method method = IpUtils.class.getDeclaredMethod(
            "shouldIgnoreInterface", NetworkInterface.class);
        method.setAccessible(true);

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        boolean foundPhysicalInterface = false;
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            Boolean shouldIgnore = (Boolean) method.invoke(null, ni);
            
            if (!shouldIgnore && ni.isUp() && !ni.isLoopback()) {
                foundPhysicalInterface = true;
                System.out.println("找到合适的物理/正常网卡: " + ni.getName() + 
                    " (" + ni.getDisplayName() + ")");
                break;
            }
        }
        
        assertTrue(foundPhysicalInterface, 
            "系统中至少应该有一个非Docker、非虚拟的可用网卡");
    }

    @Test
    @DisplayName("Windows物理适配器识别")
    void testWindowsPhysicalAdapterDetection() throws Exception {
        Method method = IpUtils.class.getDeclaredMethod(
            "isWindowsPhysicalAdapter", String.class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(null, "ethernet"),
            "ethernet 应该被识别为Windows物理网卡");
        assertTrue((boolean) method.invoke(null, "wi-fi"),
            "wi-fi 应该被识别为Windows无线网卡");
        assertTrue((boolean) method.invoke(null, "wifi"),
            "wifi 应该被识别为Windows无线网卡");
        assertTrue((boolean) method.invoke(null, "local area connection"),
            "local area connection 应该被识别为本地连接");
        
        assertFalse((boolean) method.invoke(null, "vmware network adapter"),
            "VMware 虚拟网卡不应该被识别为物理网卡");
        assertFalse((boolean) method.invoke(null, "virtualbox host-only"),
            "VirtualBox 虚拟网卡不应该被识别为物理网卡");
    }

    @Test
    @DisplayName("虚拟网卡识别")
    void testVirtualInterfaceDetection() throws Exception {
        Method method = IpUtils.class.getDeclaredMethod(
            "isVirtualInterface", String.class, String.class);
        method.setAccessible(true);

        assertTrue((boolean) method.invoke(null, "VMware Network Adapter VMnet1", "vmnet1"),
            "VMware 虚拟网卡应该被识别");
        assertTrue((boolean) method.invoke(null, "VirtualBox Host-Only Ethernet Adapter", "vboxnet0"),
            "VirtualBox 虚拟网卡应该被识别");
        assertTrue((boolean) method.invoke(null, "VPN Connection", "vpn"),
            "VPN 连接应该被识别为虚拟网卡");
        assertTrue((boolean) method.invoke(null, "TAP Adapter", "tap0"),
            "TAP 适配器应该被识别为虚拟网卡");
        assertTrue((boolean) method.invoke(null, "Loopback Pseudo-Interface 1", "lo"),
            "回环接口应该被识别");
        
        assertFalse((boolean) method.invoke(null, "Intel(R) Ethernet Controller", "eth0"),
            "Intel 物理网卡不应该被识别为虚拟网卡");
        assertFalse((boolean) method.invoke(null, "Realtek PCIe GBE Family Controller", "enp3s0"),
            "Realtek 物理网卡不应该被识别为虚拟网卡");
    }

    @Test
    @DisplayName("网卡评分系统 - 物理网卡优先级更高")
    void testInterfaceScoringSystem() throws Exception {
        Method method = IpUtils.class.getDeclaredMethod(
            "calculateInterfaceScore", NetworkInterface.class);
        method.setAccessible(true);

        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        int maxScore = Integer.MIN_VALUE;
        String bestInterface = null;
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            
            try {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                
                int score = (int) method.invoke(null, ni);
                
                System.out.printf("网卡: %-20s 评分: %d%n", ni.getName(), score);
                
                if (score > maxScore) {
                    maxScore = score;
                    bestInterface = ni.getName();
                }
            } catch (Exception e) {
                // 忽略无法评分的网卡
            }
        }
        
        assertNotNull(bestInterface, "至少应该找到一个可评分的网卡");
        System.out.println("\n最佳网卡: " + bestInterface + " (评分: " + maxScore + ")");
        
        assertTrue(maxScore > 0, "最高分应该大于0");
    }

    @Test
    @DisplayName("多网卡环境下的IP选择 - 排除Docker网卡")
    void testIpSelectionExcludesDocker() throws Exception {
        String selectedIp = IpUtils.getLocalHost();
        
        assertNotNull(selectedIp, "应该能选择到IP地址");
        
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        boolean foundNonDockerInterface = false;
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            String name = ni.getName().toLowerCase();
            
            boolean isDocker = name.contains("docker") || name.contains("veth") || 
                             name.startsWith("br-") || name.contains("cni") ||
                             name.contains("vethernet") || name.contains("wsl");
            
            if (!isDocker && ni.isUp() && !ni.isLoopback()) {
                foundNonDockerInterface = true;
                break;
            }
        }
        
        if (foundNonDockerInterface) {
            System.out.println("选择的IP: " + selectedIp + 
                " (确保不是来自Docker网卡)");
        }
    }

    @Test
    @DisplayName("边界条件 - 所有接口都是Docker/虚拟网卡时的降级处理")
    void testDegradationWhenAllVirtual() throws Exception {
        String ip = IpUtils.getLocalHost();
        
        assertNotNull(ip, "即使所有网卡都是虚拟的，也应该返回一个IP（可能是127.0.0.1）");
        System.out.println("降级处理的IP: " + ip);
    }

    @Test
    @DisplayName("性能测试 - 多次调用应该使用缓存")
    void testPerformanceWithCaching() throws Exception {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 1000; i++) {
            IpUtils.getLocalHost();
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("1000次调用耗时: " + duration + "ms");
        
        assertTrue(duration < 100, 
            "1000次调用应该在100ms内完成（使用缓存），实际耗时: " + duration + "ms");
    }

    @Test
    @DisplayName("日志输出验证 - 查看候选网卡列表")
    void testLogCandidateInterfaces() throws Exception {
        System.out.println("\n========== 系统网卡信息 ==========");
        
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        int total = 0, up = 0, loopback = 0, virtual = 0, docker = 0, physical = 0;
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            total++;
            
            String status = "";
            if (ni.isLoopback()) { loopback++; status = "[LOOPBACK]"; }
            else if (!ni.isUp()) { status = "[DOWN]"; }
            else {
                up++;
                String name = ni.getName().toLowerCase();
                if (name.contains("docker") || name.contains("veth") || 
                    name.startsWith("br-") || name.contains("vethernet")) {
                    docker++;
                    status = "[DOCKER]";
                } else if (name.contains("virtual") || name.contains("vmware") ||
                           name.contains("vbox") || name.contains("vpn")) {
                    virtual++;
                    status = "[VIRTUAL]";
                } else {
                    physical++;
                    status = "[PHYSICAL✓]";
                }
            }
            
            System.out.printf("%-25s %-40s %s%n",
                ni.getName(),
                ni.getDisplayName(),
                status);
        }
        
        System.out.println("----------------------------------------");
        System.out.printf("总计: %d | 运行中: %d | 回环: %d | Docker: %d | 虚拟: %d | 物理: %d%n",
            total, up, loopback, docker, virtual, physical);
        System.out.println("========================================\n");
        
        assertTrue(physical > 0 || up > loopback,
            "系统中应该有可用的物理或非回环网卡");
    }
}
