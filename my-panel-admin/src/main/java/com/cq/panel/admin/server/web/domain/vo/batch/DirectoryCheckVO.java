package com.cq.panel.admin.server.web.domain.vo.batch;

import lombok.Data;

import java.util.List;

@Data
public class DirectoryCheckVO {

    private String agentId;
    private String agentName;
    private String agentIp;
    private Integer agentPort;
    private String dirPath;
    private String role;

    private Boolean exists;
    private Boolean isDirectory;
    private Boolean canRead;
    private Boolean canWrite;
    private Boolean canExecute;
    private String posixPermissions;

    private Long diskTotal;
    private Long diskUsable;
    private Long diskFree;
    private Long diskTotalMB;
    private Long diskUsableMB;
    private Long diskFreeMB;
    private Boolean diskSufficient;

    private String errorMessage;

    private Boolean agentOnline;

    @Data
    public static class DirectoryCheckResult {
        private DirectoryCheckVO source;
        private List<DirectoryCheckVO> targets;
    }
}
