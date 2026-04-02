package com.cq.agent.dto;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author cq 2026/3/18 14:21
 * @since 1.0.0
 */
@Data
public class ChunkUploadResponse {

    private String transferId;
    private Boolean completed;
    private final AtomicInteger missingChunksCount =  new AtomicInteger(0);
    private int chunkIndex;

    /**
     * 设置缺失分片数量
     * @param count 缺失分片数量
     */
    public void setMissingChunksCount(int count) {
        this.missingChunksCount.set(count);
    }

    /**
     * 获取缺失分片数量
     * @return 缺失分片数量
     */
    public int getMissingChunksCount() {
        return this.missingChunksCount.get();
    }
}