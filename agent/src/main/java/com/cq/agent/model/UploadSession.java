package com.cq.agent.model;

import com.google.gson.Gson;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upload session for tracking chunked file uploads.
 * Supports resumable uploads by tracking received chunks.
 */
public class UploadSession {

    private static final Gson gson = new Gson();

    private final String transferId;
    private final String destFileDir;
    private final String destFileName;
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

    public UploadSession(String transferId, String destFileDir, String destFileName,
                         long totalSize, int totalChunks, int chunkSize, String tempDirectory) {
        this.transferId = transferId;
        this.destFileDir = destFileDir;
        this.destFileName = destFileName;
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

    public String getTransferId() {
        return transferId;
    }

    public String getDestFileDir() {
        return destFileDir;
    }

    public String getDestFileName() {
        return destFileName;
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

    public void removeChunk(int chunkIndex) {
        receivedChunks.remove(chunkIndex);
        completed = false;
        updateLastAccessTime();
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

    public String toJson() {
        return gson.toJson(this);
    }

    public static UploadSession fromJson(String json) {
        UploadSession session = gson.fromJson(json, UploadSession.class);
        // Ensure receivedChunks is thread-safe after deserialization
        if (session != null && !(session.receivedChunks instanceof ConcurrentHashMap.KeySetView)) {
            Set<Integer> safeSet = ConcurrentHashMap.newKeySet();
            safeSet.addAll(session.receivedChunks);
            try {
                java.lang.reflect.Field field = UploadSession.class.getDeclaredField("receivedChunks");
                field.setAccessible(true);
                field.set(session, safeSet);
            } catch (Exception e) {
                // Fallback to synchronized set if reflection fails
                System.err.println("Failed to set thread-safe set via reflection: " + e.getMessage());
            }
        }
        return session;
    }

    public com.cq.agent.dto.ChunkStatusData toChunkStatusData() {
        com.cq.agent.dto.ChunkStatusData data = new com.cq.agent.dto.ChunkStatusData();
        data.setTransferId(this.getTransferId());
        data.setTotalSize(this.getTotalSize());
        data.setTotalChunks(this.getTotalChunks());
        data.setChunkSize(this.getChunkSize());
        data.setMissingChunks(this.getMissingChunks());
        return data;
    }
}
