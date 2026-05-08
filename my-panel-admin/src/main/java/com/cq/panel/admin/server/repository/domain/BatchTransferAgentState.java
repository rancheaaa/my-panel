package com.cq.panel.admin.server.repository.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
public class BatchTransferAgentState implements Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String agentId;
    private String transferId;
    private Long subtaskId;
    private Long taskId;
    private String filePath;
    private String fileName;
    private String remoteTargetPath;
    private String status;
    private Long totalSize;
    private Integer chunkSize;
    private Integer totalChunks;
    private Integer transferredChunks;
    private Integer retryCount;
    private String exceptionDesc;
    private String senderNodeName;
    private String receiverNodeName;
    private String taskName;
    private String createTimeStr;
    private String updateTimeStr;
    private String enqueuedTime;
    private String initUploadStartTime;
    private String initUploadEndTime;
    private String uploadChunksStartTime;
    private String uploadChunksEndTime;
    private String mergeChunksStartTime;
    private String mergeChunksEndTime;
    private String uploadSuccessTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
