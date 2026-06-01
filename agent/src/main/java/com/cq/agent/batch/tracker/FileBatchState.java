package com.cq.agent.batch.tracker;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class FileBatchState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long fileBatchId;
    private Long taskId;
    private String taskName;
    private Long scanBatchId;

    private SourceInfo source;
    private TransferConfigInfo transferConfig;
    private List<TargetProgress> targets;
    private Summary summary;

    private String status; // PENDING / FAILED / COMPLETED
    private String createTime;
    private String updateTime;

    @Data
    public static class SourceInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String agentId;
        private String agentName;
        private String sourceDir;
        private String filePath;
        private String fileName;
        private long fileSizeBytes;
        private String lastModified;
    }

    @Data
    public static class TransferConfigInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String transferMode;
        private String routingStrategy;
        private String postTransferAction;
        private String backupDir;
        private String backupMode;
    }

    @Data
    public static class TargetProgress implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String agentId;
        private String agentName;
        private String targetDir;
        private String targetPath;
        private String status; // PENDING / COMPLETED / FAILED / FINAL_FAILURE
        private String completedAt;
    }

    @Data
    public static class Summary implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private int totalTargets;
        private int completedCount;
        private int failedCount;
        private int pendingCount;
    }
}
