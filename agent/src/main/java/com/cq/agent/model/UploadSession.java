package com.cq.agent.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upload session for tracking chunked file uploads.
 * Supports resumable uploads by tracking received chunks.
 */
public class UploadSession {

    private final String sessionId;
    private final String targetPath;
    private final String fileName;
    private final long totalSize;
    private final int totalChunks;
    private final int chunkSize;
    private final long createTime;
    private final Set<Integer> receivedChunks;
    private final String tempDirectory;
    private volatile long lastAccessTime;
    private volatile boolean completed;
    private volatile boolean merged;
    private String checksum;

    public UploadSession(String sessionId, String targetPath, String fileName,
                         long totalSize, int totalChunks, int chunkSize, String tempDirectory) {
        this.sessionId = sessionId;
        this.targetPath = targetPath;
        this.fileName = fileName;
        this.totalSize = totalSize;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.tempDirectory = tempDirectory;
        this.createTime = System.currentTimeMillis();
        this.lastAccessTime = this.createTime;
        this.receivedChunks = ConcurrentHashMap.newKeySet();
        this.completed = false;
        this.merged = false;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getFileName() {
        return fileName;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getChunkSize() {
        return chunkSize;
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

    public String getTempDirectory() {
        return tempDirectory;
    }

    public Set<Integer> getReceivedChunks() {
        return Collections.unmodifiableSet(receivedChunks);
    }

    public void markChunkReceived(int chunkIndex) {
        receivedChunks.add(chunkIndex);
        updateLastAccessTime();
        if (receivedChunks.size() == totalChunks) {
            completed = true;
        }
    }

    public boolean isChunkReceived(int chunkIndex) {
        return receivedChunks.contains(chunkIndex);
    }

    public int getReceivedChunkCount() {
        return receivedChunks.size();
    }

    public List<Integer> getMissingChunks() {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!receivedChunks.contains(i)) {
                missing.add(i);
            }
        }
        return missing;
    }

    public double getProgress() {
        return totalChunks > 0 ? (double) receivedChunks.size() / totalChunks * 100.0 : 0.0;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public boolean isExpired(long timeoutMs) {
        return System.currentTimeMillis() - lastAccessTime > timeoutMs;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionId", sessionId);
        map.put("targetPath", targetPath);
        map.put("fileName", fileName);
        map.put("totalSize", totalSize);
        map.put("totalChunks", totalChunks);
        map.put("chunkSize", chunkSize);
        map.put("receivedChunks", receivedChunks.size());
        map.put("missingChunks", getMissingChunks());
        map.put("progress", String.format("%.2f%%", getProgress()));
        map.put("completed", completed);
        map.put("merged", merged);
        map.put("createTime", createTime);
        map.put("lastAccessTime", lastAccessTime);
        if (checksum != null) {
            map.put("checksum", checksum);
        }
        return map;
    }
}
