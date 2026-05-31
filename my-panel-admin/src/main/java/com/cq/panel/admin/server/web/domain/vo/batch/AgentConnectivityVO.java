package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;

import java.util.List;

@Data
public class AgentConnectivityVO {

    private String sourceNodeName;

    private String targetNodeName;

    private String sourceIp;

    private Integer sourcePort;

    private String targetIp;

    private Integer targetPort;

    private boolean sourceExists;

    private boolean targetExists;

    private boolean sourceOnline;

    private boolean targetOnline;

    private boolean sourceEnabled;

    private boolean targetEnabled;

    private PortProbeResult adminToSource;

    private PortProbeResult adminToTarget;

    private PortProbeResult sourceToTarget;

    private String connectivityStatus;

    private String failureReason;

    private List<String> checkDetails;

    @Data
    public static class PortProbeResult {
        private String from;
        private String to;
        private String targetIp;
        private Integer targetPort;
        private boolean reachable;
        private String failureReason;

        public static PortProbeResult of(String from, String to, String ip, int port, boolean reachable, String reason) {
            PortProbeResult r = new PortProbeResult();
            r.setFrom(from);
            r.setTo(to);
            r.setTargetIp(ip);
            r.setTargetPort(port);
            r.setReachable(reachable);
            r.setFailureReason(reason);
            return r;
        }
    }

    public enum Status {
        REACHABLE,
        UNREACHABLE,
        SOURCE_NOT_FOUND,
        TARGET_NOT_FOUND,
        SOURCE_OFFLINE,
        TARGET_OFFLINE,
        SOURCE_DISABLED,
        TARGET_DISABLED,
        ADMIN_TO_SOURCE_UNREACHABLE,
        ADMIN_TO_TARGET_UNREACHABLE,
        SOURCE_TO_TARGET_UNREACHABLE,
        UNKNOWN_ERROR
    }
}
