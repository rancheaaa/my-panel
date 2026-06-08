package com.cq.panel.admin.server.repository.service.impl;

import com.cq.panel.admin.server.repository.domain.AgentRegistry;
import com.cq.panel.admin.server.repository.mapper.AgentRegistryMapper;
import com.cq.panel.admin.server.repository.service.IAgentConnectivityService;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO.PortProbeResult;
import com.cq.panel.admin.server.web.domain.vo.batch.AgentConnectivityVO.Status;
import com.cq.panel.common.utils.IpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentConnectivityServiceImpl implements IAgentConnectivityService {

    private static final Logger log = LoggerFactory.getLogger(AgentConnectivityServiceImpl.class);

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int HTTP_TIMEOUT_S = 10;

    private final AgentRegistryMapper agentRegistryMapper;
    private final ObjectMapper objectMapper;

    @Value("${server.port:8888}")
    private int adminPort;

    public AgentConnectivityServiceImpl(AgentRegistryMapper agentRegistryMapper, ObjectMapper objectMapper) {
        this.agentRegistryMapper = agentRegistryMapper;
        this.objectMapper = objectMapper;
    }

    private String getAdminAddress() {
        try {
            return IpUtils.getLocalHost() + ":" + adminPort;
        } catch (UnknownHostException | java.net.SocketException e) {
            log.warn("获取Admin本地IP失败: {}", e.getMessage());
            return "未知:" + adminPort;
        }
    }

    @Override
    public AgentConnectivityVO checkConnectivity(String sourceNodeName, String targetNodeName) {
        AgentConnectivityVO vo = new AgentConnectivityVO();
        vo.setSourceNodeName(sourceNodeName);
        vo.setTargetNodeName(targetNodeName);
        List<String> details = new ArrayList<>();

        if (!validateInput(vo, sourceNodeName, targetNodeName, details)) {
            vo.setCheckDetails(details);
            vo.setReverseCheckDetails(List.of("⏭️ 跳过：输入校验未通过"));
            return vo;
        }

        // 提前查询两个节点，确保所有状态字段都能从DB获取
        AgentRegistry source = agentRegistryMapper.selectByNodeName(sourceNodeName);
        AgentRegistry target = agentRegistryMapper.selectByNodeName(targetNodeName);

        // 始终设置全部六个字段，不提前返回，确保非null
        boolean sourceExists = source != null;
        boolean targetExists = target != null;
        vo.setSourceExists(sourceExists);
        vo.setTargetExists(targetExists);

        boolean sourceOnline = sourceExists && source.getNodeStatus() != null && source.getNodeStatus() == 1;
        boolean targetOnline = targetExists && target.getNodeStatus() != null && target.getNodeStatus() == 1;
        vo.setSourceOnline(sourceOnline);
        vo.setTargetOnline(targetOnline);

        boolean sourceEnabled = sourceExists && source.getNodeEnabled() != null && source.getNodeEnabled() == 0;
        boolean targetEnabled = targetExists && target.getNodeEnabled() != null && target.getNodeEnabled() == 0;
        vo.setSourceEnabled(sourceEnabled);
        vo.setTargetEnabled(targetEnabled);

        if (sourceExists) {
            vo.setSourceIp(source.getAgentIp());
            vo.setSourcePort(source.getAgentPort());
        }
        if (targetExists) {
            vo.setTargetIp(target.getAgentIp());
            vo.setTargetPort(target.getAgentPort());
        }

        // 逐项检查并记录详情，不提前返回
        Status firstFailure = null;
        String firstFailureReason = null;

        if (!sourceExists) {
            details.add("❌ 源节点 [" + sourceNodeName + "] 在注册表中不存在");
            if (firstFailure == null) { firstFailure = Status.SOURCE_NOT_FOUND; firstFailureReason = "源节点不存在: " + sourceNodeName; }
        } else {
            details.add("✅ 源节点 [" + sourceNodeName + "] 存在，IP: " + source.getAgentIp() + ":" + source.getAgentPort());
        }

        if (!targetExists) {
            details.add("❌ 目标节点 [" + targetNodeName + "] 在注册表中不存在");
            if (firstFailure == null) { firstFailure = Status.TARGET_NOT_FOUND; firstFailureReason = "目标节点不存在: " + targetNodeName; }
        } else {
            details.add("✅ 目标节点 [" + targetNodeName + "] 存在，IP: " + target.getAgentIp() + ":" + target.getAgentPort());
        }

        if (sourceExists && !sourceOnline) {
            details.add("❌ 源节点离线，当前状态: " + nodeStatusDesc(source.getNodeStatus()));
            if (firstFailure == null) { firstFailure = Status.SOURCE_OFFLINE; firstFailureReason = "源节点离线，当前状态: " + nodeStatusDesc(source.getNodeStatus()); }
        } else if (sourceExists) {
            details.add("✅ 源节点在线");
        }

        if (sourceExists && !sourceEnabled) {
            details.add("❌ 源节点已禁用，当前状态: " + nodeEnabledDesc(source.getNodeEnabled()));
            if (firstFailure == null) { firstFailure = Status.SOURCE_DISABLED; firstFailureReason = "源节点已禁用，当前状态: " + nodeEnabledDesc(source.getNodeEnabled()); }
        } else if (sourceExists) {
            details.add("✅ 源节点已启用");
        }

        if (targetExists && !targetOnline) {
            details.add("❌ 目标节点离线，当前状态: " + nodeStatusDesc(target.getNodeStatus()));
            if (firstFailure == null) { firstFailure = Status.TARGET_OFFLINE; firstFailureReason = "目标节点离线，当前状态: " + nodeStatusDesc(target.getNodeStatus()); }
        } else if (targetExists) {
            details.add("✅ 目标节点在线");
        }

        if (targetExists && !targetEnabled) {
            details.add("❌ 目标节点已禁用，当前状态: " + nodeEnabledDesc(target.getNodeEnabled()));
            if (firstFailure == null) { firstFailure = Status.TARGET_DISABLED; firstFailureReason = "目标节点已禁用，当前状态: " + nodeEnabledDesc(target.getNodeEnabled()); }
        } else if (targetExists) {
            details.add("✅ 目标节点已启用");
        }

        // 前置检查有失败项，跳过网络探测
        if (firstFailure != null) {
            vo.setConnectivityStatus(firstFailure.name());
            vo.setFailureReason(firstFailureReason);
            vo.setCheckDetails(details);
            vo.setReverseCheckDetails(List.of("⏭️ 跳过：前置检查未通过"));
            return vo;
        }

        String adminAddr = getAdminAddress();

        // Layer 1: Admin → Source
        PortProbeResult adminToSource = probePort("admin", "源Agent", source.getAgentIp(), source.getAgentPort());
        vo.setAdminToSource(adminToSource);
        if (!adminToSource.isReachable()) {
            details.add("❌ Admin[" + adminAddr + "] → 源Agent [" + source.getAgentIp() + ":" + source.getAgentPort() + "] 不可达 - " + adminToSource.getFailureReason());
            vo.setConnectivityStatus(Status.ADMIN_TO_SOURCE_UNREACHABLE.name());
            vo.setFailureReason("Admin无法连接源Agent: " + adminToSource.getFailureReason());
            vo.setCheckDetails(details);
            vo.setReverseCheckDetails(List.of("⏭️ 跳过：Admin无法连接源Agent"));
            return vo;
        }
        details.add("✅ Admin[" + adminAddr + "] → 源Agent [" + source.getAgentIp() + ":" + source.getAgentPort() + "] 可达");

        // Layer 2: Admin → Target
        PortProbeResult adminToTarget = probePort("admin", "目标Agent", target.getAgentIp(), target.getAgentPort());
        vo.setAdminToTarget(adminToTarget);
        if (!adminToTarget.isReachable()) {
            details.add("❌ Admin[" + adminAddr + "] → 目标Agent [" + target.getAgentIp() + ":" + target.getAgentPort() + "] 不可达 - " + adminToTarget.getFailureReason());
            vo.setConnectivityStatus(Status.ADMIN_TO_TARGET_UNREACHABLE.name());
            vo.setFailureReason("Admin无法连接目标Agent: " + adminToTarget.getFailureReason());
            vo.setCheckDetails(details);
            vo.setReverseCheckDetails(List.of("⏭️ 跳过：Admin无法连接目标Agent"));
            return vo;
        }
        details.add("✅ Admin[" + adminAddr + "] → 目标Agent [" + target.getAgentIp() + ":" + target.getAgentPort() + "] 可达");

        // Layer 3: Source → Target (via agent probe API)
        PortProbeResult sourceToTarget = probeViaAgent(source.getAgentIp(), source.getAgentPort(),
                target.getAgentIp(), target.getAgentPort());
        vo.setSourceToTarget(sourceToTarget);
        if (!sourceToTarget.isReachable()) {
            details.add("❌ 源Agent [" + source.getAgentIp() + ":" + source.getAgentPort() + "] → 目标Agent [" + target.getAgentIp() + ":" + target.getAgentPort() + "] 不可达 - " + sourceToTarget.getFailureReason());
            vo.setConnectivityStatus(Status.SOURCE_TO_TARGET_UNREACHABLE.name());
            vo.setFailureReason("源Agent无法连接目标Agent: " + sourceToTarget.getFailureReason());
            vo.setCheckDetails(details);
            vo.setReverseCheckDetails(List.of("⏭️ 跳过：源Agent无法连接目标Agent"));
            return vo;
        }
        details.add("✅ 源Agent [" + source.getAgentIp() + ":" + source.getAgentPort() + "] → 目标Agent [" + target.getAgentIp() + ":" + target.getAgentPort() + "] 可达");

        // Layer 4: Target → Source (via target agent probe API)
        List<String> reverseDetails = new ArrayList<>();
        PortProbeResult targetToSource = probeViaAgent(target.getAgentIp(), target.getAgentPort(),
                source.getAgentIp(), source.getAgentPort());
        vo.setTargetToSource(targetToSource);
        if (!targetToSource.isReachable()) {
            reverseDetails.add("❌ 目标Agent [" + target.getAgentIp() + ":" + target.getAgentPort() + "] → 源Agent [" + source.getAgentIp() + ":" + source.getAgentPort() + "] 不可达 - " + targetToSource.getFailureReason());
            vo.setReverseStatus(Status.UNREACHABLE.name());
            vo.setReverseFailureReason("目标Agent无法连接源Agent: " + targetToSource.getFailureReason());
        } else {
            reverseDetails.add("✅ 目标Agent [" + target.getAgentIp() + ":" + target.getAgentPort() + "] → 源Agent [" + source.getAgentIp() + ":" + source.getAgentPort() + "] 可达");
            vo.setReverseStatus(Status.REACHABLE.name());
        }

        vo.setConnectivityStatus(Status.REACHABLE.name());
        vo.setCheckDetails(details);
        vo.setReverseCheckDetails(reverseDetails);
        return vo;
    }

    private boolean validateInput(AgentConnectivityVO vo, String sourceNodeName, String targetNodeName, List<String> details) {
        if (sourceNodeName == null || sourceNodeName.isBlank()) {
            vo.setConnectivityStatus(Status.SOURCE_NOT_FOUND.name());
            vo.setFailureReason("源节点名称不能为空");
            details.add("❌ 源节点名称为空");
            return false;
        }
        if (targetNodeName == null || targetNodeName.isBlank()) {
            vo.setConnectivityStatus(Status.TARGET_NOT_FOUND.name());
            vo.setFailureReason("目标节点名称不能为空");
            details.add("❌ 目标节点名称为空");
            return false;
        }
        return true;
    }

    public PortProbeResult probePort(String from, String to, String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            return PortProbeResult.of(from, to, ip, port, true, null);
        } catch (IOException e) {
            String reason = classifyConnectionFailure(e);
            log.warn("端口探测失败 {}:{} - {}", ip, port, reason);
            return PortProbeResult.of(from, to, ip, port, false, reason);
        }
    }

    public PortProbeResult probeViaAgent(String sourceIp, int sourcePort, String targetIp, int targetPort) {
        try {
            String url = String.format("http://%s:%d/api/probe?host=%s&port=%d",
                    sourceIp, sourcePort, targetIp, targetPort);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_S))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(HTTP_TIMEOUT_S))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                boolean success = root.path("success").asBoolean(false);
                if (success) {
                    JsonNode data = root.path("data");
                    boolean reachable = data.path("reachable").asBoolean(false);
                    String reason = reachable ? null : extractFailureFromDetails(data);
                    return PortProbeResult.of("源Agent", "目标Agent", targetIp, targetPort, reachable, reason);
                } else {
                    String msg = root.path("msg").asText("未知错误");
                    return PortProbeResult.of("源Agent", "目标Agent", targetIp, targetPort, false, "Agent返回错误: " + msg);
                }
            } else {
                return PortProbeResult.of("源Agent", "目标Agent", targetIp, targetPort, false,
                        "Agent返回HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            String reason = "调用源Agent探测接口失败: " + e.getMessage();
            log.warn("调用源Agent探测接口失败: {}", e.getMessage());
            return PortProbeResult.of("源Agent", "目标Agent", targetIp, targetPort, false, reason);
        }
    }

    private String extractFailureFromDetails(JsonNode data) {
        JsonNode detailsNode = data.path("details");
        if (detailsNode.isArray()) {
            for (JsonNode item : detailsNode) {
                String text = item.asText();
                if (text.startsWith("❌")) {
                    return text.substring(text.indexOf("- ") + 2);
                }
            }
        }
        return "端口不可达";
    }

    public String classifyConnectionFailure(IOException e) {
        String msg = e.getMessage();
        if (msg == null) return "未知原因";
        String lowerMsg = msg.toLowerCase();
        if (lowerMsg.contains("connection refused")) return "连接被拒绝（目标进程未启动或端口未监听）";
        if (lowerMsg.contains("network is unreachable") || lowerMsg.contains("no route to host")) return "网络不可达（可能因网络策略限制）";
        if (lowerMsg.contains("connection timed out") || lowerMsg.contains("timed out")) return "连接超时（可能因防火墙拦截或网络不通）";
        if (lowerMsg.contains("connection reset")) return "连接被重置（对端拒绝连接）";
        if (lowerMsg.contains("unreachable")) return "目标主机不可达";
        if (lowerMsg.contains("permission denied")) return "权限被拒绝（可能因安全策略限制）";
        return "未知原因: " + msg;
    }

    private String nodeStatusDesc(Integer nodeStatus) {
        if (nodeStatus == null) return "未知(null)";
        return switch (nodeStatus) {
            case 0 -> "离线";
            case 1 -> "在线";
            case 2 -> "未知";
            default -> "未知(" + nodeStatus + ")";
        };
    }

    private String nodeEnabledDesc(Integer nodeEnabled) {
        if (nodeEnabled == null) return "未知(null)";
        return switch (nodeEnabled) {
            case 0 -> "启用";
            case 1 -> "临时关闭";
            case 2 -> "永久关闭";
            default -> "未知(" + nodeEnabled + ")";
        };
    }
}
