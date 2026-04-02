package com.cq.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response data for chunk init API.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChunkInitResponse {

    private String transferId;
    private long totalSize;
    private int totalChunks;
    private int chunkSize;
    private List<Integer> missingChunks;
    private String initTime;

    /**
     * 带参数的构造器
     * @param transferId 传输ID
     * @param totalSize 总大小
     * @param totalChunks 总分片数
     * @param chunkSize 分片大小
     * @param missingChunks 缺失分片列表
     */
    public ChunkInitResponse(String transferId, long totalSize, int totalChunks, int chunkSize, List<Integer> missingChunks) {
        this.transferId = transferId;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.missingChunks = missingChunks;
    }
}