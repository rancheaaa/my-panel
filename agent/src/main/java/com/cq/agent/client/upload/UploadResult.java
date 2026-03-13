package com.cq.agent.client.upload;

/**
 * Represents the result of a successful file upload.
 */
public class UploadResult {

    private final String path;
    private final long size;
    private final String checksum;
    private final UploadCompletionState completionState;

    public UploadResult(String path, long size, String checksum) {
        this(path, size, checksum, UploadCompletionState.MERGED); // Default to MERGED
    }

    public UploadResult(String path, long size, String checksum, UploadCompletionState completionState) {
        this.path = path;
        this.size = size;
        this.checksum = checksum;
        this.completionState = completionState;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getChecksum() {
        return checksum;
    }

    public UploadCompletionState getCompletionState() {
        return completionState;
    }

    @Override
    public String toString() {
        return "UploadResult{" +
                "path='" + path + '\'' +
                ", size=" + size +
                ", checksum='" + checksum + '\'' +
                ", completionState=" + completionState +
                '}';
    }
}
