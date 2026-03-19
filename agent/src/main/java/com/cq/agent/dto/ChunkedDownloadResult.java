package com.cq.agent.dto;

/**
 *
 * @author cq 2026/3/19 14:34
 * @since 1.0.0
 */
public class ChunkedDownloadResult {

    private final byte[] data;
    private final long rangeStart;
    private final long rangeEnd;
    private final long totalSize;
    private final String fileName;

    public ChunkedDownloadResult(byte[] data, long rangeStart, long rangeEnd, long totalSize, String fileName) {
        this.data = data;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.totalSize = totalSize;
        this.fileName = fileName;
    }

    public byte[] getData() {
        return data;
    }

    public long getRangeStart() {
        return rangeStart;
    }

    public long getRangeEnd() {
        return rangeEnd;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentRange() {
        return "bytes " + rangeStart + "-" + rangeEnd + "/" + totalSize;
    }

    public boolean isPartial() {
        return rangeStart > 0 || rangeEnd < totalSize - 1;
    }
}
