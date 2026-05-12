package com.cq.agent.model;

import com.cq.agent.dto.ChunkInitResponse;
import com.cq.agent.dto.ChunkStatusResponse;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Upload session for tracking chunked file uploads.
 * Supports resumable uploads by tracking received chunks.
 */
public class UploadSession {

    private static final Logger log = LoggerFactory.getLogger(UploadSession.class);

    @Getter
    private final String transferId;
    @Getter
    private final String traceId;
    @Getter
    private final String destFileDir;
    @Getter
    private final String destFileName;
    @Getter
    private final long totalSize;
    @Getter
    private final int totalChunks;
    @Getter
    private final int chunkSize;
    private final long createTime;
    private final Set<Integer> receivedChunks;
    @Getter
    private final String tempDirectory;
    private volatile long lastAccessTime;
    private volatile boolean completed;
    private volatile boolean merged;

    public UploadSession(
            String transferId,
            String traceId,
            String destFileDir,
            String destFileName,
            long totalSize,
            int totalChunks,
            int chunkSize,
            String tempDirectory,
            long createTime,
            long lastAccessTime,
            Set<Integer> receivedChunks,
            boolean completed,
            boolean merged) {
        this.transferId = transferId;
        this.traceId = traceId;
        this.destFileDir = destFileDir;
        this.destFileName = destFileName;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.tempDirectory = tempDirectory;
        this.createTime = createTime;
        this.lastAccessTime = lastAccessTime;
        this.receivedChunks = receivedChunks;
        this.completed = completed;
        this.merged = merged;
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void updateLastAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    public void refresh() {
        Set<Integer> chunks = ConcurrentHashMap.newKeySet();
        Path tempDir = Paths.get(getTempDirectory());

        if (!Files.exists(tempDir) || !Files.isDirectory(tempDir)) {
            return;
        }
        try {
            Pattern chunkPattern = Pattern.compile(Pattern.quote(getDestFileName()) + "_chunk_(\\d+)");

            Files.list(tempDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String fileName = file.getFileName().toString();
                        Matcher matcher = chunkPattern.matcher(fileName);

                        if (matcher.matches()) {
                            try {
                                int chunkIndex = Integer.parseInt(matcher.group(1));

                                if (chunkIndex >= 0 && chunkIndex < totalChunks) {
                                    long fileSize = Files.size(file);
                                    long expectedSize = calculateExpectedChunkSize(chunkIndex);

                                    if (fileSize == expectedSize) {
                                        chunks.add(chunkIndex);
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Error processing chunk file {}: {}", fileName, e.getMessage());
                            }
                        }
                    });
            if (!receivedChunks.equals(chunks)) {
                synchronized (receivedChunks) {
                    receivedChunks.clear();
                    receivedChunks.addAll(chunks);
                }
            }
        } catch (Exception e) {
            log.error("Error scanning chunk directory: {}", e.getMessage());
        }
    }

    public Set<Integer> getReceivedChunks() {
        refresh();
        return Collections.unmodifiableSet(receivedChunks);
    }
    
    private long calculateExpectedChunkSize(int chunkIndex) {
        if (chunkIndex == totalChunks - 1) {
            return totalSize - (long) chunkIndex * chunkSize;
        }
        return chunkSize;
    }

    public boolean isChunkReceived(int chunkIndex) {
        refresh();
        return receivedChunks.contains(chunkIndex);
    }

    public int getReceivedChunkCount() {
        refresh();
        return receivedChunks.size();
    }

    public List<Integer> getMissingChunks() {
        refresh();
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!receivedChunks.contains(i)) {
                missing.add(i);
            }
        }
        return missing;
    }

    public double getProgress() {
        refresh();
        return totalChunks > 0 ? (double) receivedChunks.size() / totalChunks * 100.0 : 0.0;
    }

    public boolean isCompleted() {
        refresh();
        if (receivedChunks.size() == totalChunks) {
            completed = true;
        }
        return completed;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - lastAccessTime > timeoutMs;
    }

    public ChunkStatusResponse toChunkStatusResponse() {
        refresh();
        ChunkStatusResponse data = new ChunkStatusResponse();
        data.setTransferId(this.getTransferId());
        data.setTotalSize(this.getTotalSize());
        data.setTotalChunks(this.getTotalChunks());
        data.setChunkSize(this.getChunkSize());
        data.setMissingChunks(this.getMissingChunks());
        return data;
    }

    public ChunkInitResponse toChunkInitResponse() {
        refresh();
        final ChunkInitResponse chunkInitResponse = new ChunkInitResponse();
        chunkInitResponse.setTransferId(this.getTransferId());
        chunkInitResponse.setTotalSize(this.getTotalSize());
        chunkInitResponse.setTotalChunks(this.getTotalChunks());
        chunkInitResponse.setChunkSize(this.getChunkSize());
        chunkInitResponse.setMissingChunks(this.getMissingChunks());

        return chunkInitResponse;
    }

    @Override
    public String toString() {
        return "UploadSession{" +
                "transferId='" + transferId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", destFileDir='" + destFileDir + '\'' +
                ", destFileName='" + destFileName + '\'' +
                ", totalSize=" + totalSize +
                ", totalChunks=" + totalChunks +
                ", chunkSize=" + chunkSize +
                ", createTime=" + createTime +
                ", receivedChunks=" + receivedChunks +
                ", tempDirectory='" + tempDirectory + '\'' +
                ", lastAccessTime=" + lastAccessTime +
                ", completed=" + completed +
                ", merged=" + merged +
                '}';
    }
}