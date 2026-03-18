package com.cq.agent.service;

import com.cq.agent.dto.*;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.model.UploadSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Chunked transfer service for large file uploads and downloads.
 * Supports resumable uploads and range-based downloads.
 */
public class ChunkedTransferService {

    private static final Logger logger = LoggerFactory.getLogger(ChunkedTransferService.class);
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    private final Path baseDirectory;
    private final Path tempDirectory;
    private final boolean allowOutsideBase;
    private final int defaultChunkSize;
    private final long maxFileSize;
    private final long sessionTimeoutMs;

    private final Map<String, UploadSession> uploadSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public ChunkedTransferService(AgentConfig config) {
        String baseDir = config.getFileBaseDirectory();
        this.baseDirectory = Path.of(baseDir).toAbsolutePath().normalize();
        this.allowOutsideBase = config.isAllowOutsideBaseDirectory();
        this.defaultChunkSize = config.getChunkSize();
        this.maxFileSize = config.getMaxFileSize();
        this.sessionTimeoutMs = config.getUploadSessionTimeoutMinutes() * 60 * 1000L;

        this.tempDirectory = baseDirectory.resolve(".agent_chunks");
        try {
            if(!Files.exists(tempDirectory)) {
                Files.createDirectories(tempDirectory);
                logger.info("Chunked transfer service initialized. Temp dir: {}", tempDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create temp directory: {}", tempDirectory, e);
        }

        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chunk-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredSessions, 5, 5, TimeUnit.MINUTES);
    }

    public ApiResponse<ChunkStatusData> initUpload(String transferId, String destFileDir, String destFileName, long totalSize) {
        try {
            logger.debug("Initializing upload: transferId={}, destFileDir={}, destFileName={}, totalSize={}", 
                    transferId, destFileDir, destFileName, totalSize);
            if (totalSize < 0) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "total size can't less than zero");
            }
            if (totalSize > maxFileSize) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "File size exceeds maximum allowed: " + maxFileSize);
            }

            Path resolvedPath = resolvePath(destFileDir);
            logger.debug("Resolved path: {}", resolvedPath);

            // Calculate chunks
            int totalChunks = (int) Math.ceil((double) totalSize / defaultChunkSize);
            logger.debug("Calculated chunks: totalChunks={}, chunkSize={}", totalChunks, defaultChunkSize);

            // Use provided transferId or generate one if missing
            String finalTransferId = (transferId != null && !transferId.isEmpty())
                    ? transferId
                    : UUID.randomUUID().toString().replace("-", "");
            logger.debug("Using transferId: {}", finalTransferId);

            // Check if session already exists for this transferId
            if (uploadSessions.containsKey(finalTransferId)) {
                UploadSession existing = uploadSessions.get(finalTransferId);
                // Verify if it's the same file
                if (existing.getDestFileDir().equals(resolvedPath.toString()) && existing.getTotalSize() == totalSize) {
                    logger.info("Resuming existing upload session for transferId: {}", finalTransferId);
                    verifySession(existing);
                    return ApiResponse.success(existing.toChunkStatusData());
                } else {
                    return ApiResponse.failure(ApiCode.UPLOAD_SESSION_CONFLICT.getCode(), "transferId conflict: another file is being uploaded with the same ID");
                }
            }

            // Create session temp directory
            Path sessionTempDir = resolvedPath.resolve(finalTransferId);
            Files.createDirectories(sessionTempDir);
            logger.debug("Created session temp directory: {}", sessionTempDir);

            UploadSession session = new UploadSession(
                    finalTransferId,
                    resolvedPath.toString(),
                    destFileName,
                    totalSize,
                    totalChunks,
                    defaultChunkSize,
                    sessionTempDir.toString()
            );

            uploadSessions.put(finalTransferId, session);
            logger.info("Upload session created: {}, destDir:{} destFile: {}, size: {}, chunks: {}",
                    finalTransferId, resolvedPath, destFileName, totalSize, totalChunks);

            return ApiResponse.success(session.toChunkStatusData());
        } catch (Exception e) {
            logger.error("Failed to initialize upload", e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to initialize upload: " + e.getMessage());
        }
    }

    public ApiResponse<ChunkUploadResultData> uploadChunk(ChunkUploadRequest request, byte[] content) {
        final String transferId = request.getTransferId();
        logger.debug("Processing chunk upload: transferId={}, chunkIndex={}", transferId, request.getChunkIndex());
        UploadSession session = uploadSessions.get(transferId);
        if (session == null) {
            logger.debug("Upload session not found: {}", transferId);
            return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Upload session not found: " + transferId);
        }

        if (session.isMerged()) {
            // If already merged, it's a conflict with the current state, not a bad request.
            logger.debug("Upload session already merged: {}", transferId);
            return ApiResponse.failure(ApiCode.CHUNKS_ALREADY_MERGED.getCode(), "Upload session already completed and merged");
        }

        final int chunkIndex = request.getChunkIndex();
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            logger.debug("Invalid chunk index: {} for session {} (totalChunks: {})", chunkIndex, transferId, session.getTotalChunks());
            return ApiResponse.failure(ApiCode.INVALID_CHUNK_INDEX.getCode(), "Invalid chunk index: " + chunkIndex);
        }

        // Validate chunk size (last chunk may be smaller)
        int expectedSize = chunkIndex == session.getTotalChunks() - 1
                ? (int) (session.getTotalSize() - (long) chunkIndex * session.getChunkSize())
                : session.getChunkSize();

        if (content.length != expectedSize) {
            logger.debug("Invalid chunk size for session {}: expected={}, got={}", transferId, expectedSize, content.length);
            return ApiResponse.failure(ApiCode.INVALID_CHUNK_SIZE.getCode(), "Invalid chunk size. Expected: " + expectedSize + ", got: " + content.length);
        }

        try {
            // Write chunk to temp file
            final String destFileName = request.getDestFileName();
            Path chunkFile = Path.of(session.getTempDirectory(), destFileName + "_chunk_" + chunkIndex);
            logger.debug("Writing chunk to file: {}", chunkFile);
            Files.write(chunkFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            session.markChunkReceived(chunkIndex);
            logger.debug("Chunk {} received for session {}, total received: {}/{}", 
                    chunkIndex, transferId, session.getReceivedChunkCount(), session.getTotalChunks());

            ChunkUploadResultData result = new ChunkUploadResultData();
            result.setTransferId(transferId);
            result.setCompleted(session.isCompleted());
            result.setMissingChunksCount(session.getTotalChunks() - session.getReceivedChunkCount());

            if (session.isCompleted()) {
                logger.debug("All chunks received for session: {}", transferId);
            }

            return ApiResponse.success(result);
        } catch (IOException e) {
            logger.error("Failed to write chunk {} for session {}", chunkIndex, transferId, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to write chunk");
        }
    }

    public ApiResponse<MergeResultData> mergeChunks(String transferId) {
        logger.debug("Processing chunk merge for transferId: {}", transferId);
        UploadSession session = uploadSessions.get(transferId);
        if (session == null) {
            logger.debug("Upload session not found: {}", transferId);
            return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Upload session not found: " + transferId);
        }

        session.updateLastAccessTime();

        if (!session.isCompleted()) {
            logger.debug("Upload not completed for session {}: missing chunks {}", transferId, session.getMissingChunks());
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Upload not completed. Missing chunks: " + session.getMissingChunks());
        }

        if (session.isMerged()) {
            logger.debug("Chunks already merged for session: {}", transferId);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Chunks already merged");
        }

        try {
            Path targetFullFileName = Path.of(session.getDestFileDir(), session.getDestFileName());
            logger.debug("Merging chunks to target file: {}", targetFullFileName);

            // Create parent directories
//            Path parent = targetPath.getParent();
//            if (parent != null && !Files.exists(parent)) {
//                Files.createDirectories(parent);
//            }

            try (FileChannel outChannel = FileChannel.open(targetFullFileName,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                for (int i = 0; i < session.getTotalChunks(); i++) {
                    Path chunkFile = Path.of(session.getTempDirectory(), session.getDestFileName() + "_chunk_" + i);
                    logger.debug("Reading chunk file: {}", chunkFile);
                    try (FileChannel chunkChannel = FileChannel.open(chunkFile, StandardOpenOption.READ)) {
                        long size = chunkChannel.size();
                        long transferred = 0;
                        while (transferred < size) {
                            transferred += chunkChannel.transferTo(transferred, size - transferred, outChannel);
                        }
                        logger.debug("Transferred {} bytes from chunk {}", transferred, i);
                    }
                }
            }

            // Verify merged file size
            long mergedSize = Files.size(targetFullFileName);
            logger.debug("Merged file size: {}, expected: {}", mergedSize, session.getTotalSize());
            if (mergedSize != session.getTotalSize()) {
                logger.error("Merged file size mismatch for session {}. Expected: {}, Actual: {}",
                        transferId, session.getTotalSize(), mergedSize);
                Files.deleteIfExists(targetFullFileName); // Clean up inconsistent file
                return ApiResponse.failure(ApiCode.MERGE_SIZE_MISMATCH.getCode(), "Merged file size does not match original file size");
            }

            session.setMerged(true);
            logger.info("Chunks merged for session {}: {}", transferId, targetFullFileName);

            // Cleanup temp files
            logger.debug("Cleaning up temp files for session: {}", transferId);
            cleanupSessionTempFiles(session);

            MergeResultData resultData = new MergeResultData();
            resultData.setDestFileDir(session.getDestFileDir());
            resultData.setDestFileName(session.getDestFileName());
            resultData.setSize(mergedSize);
            
            logger.debug("Merge completed successfully for session: {}", transferId);
            return ApiResponse.success(resultData);
        } catch (IOException e) {
            logger.error("Failed to merge chunks for session {}", transferId, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to merge chunks: " + e.getMessage());
        }
    }

    public ApiResponse<ChunkStatusData> getUploadStatus(String transferId) {
        UploadSession session = uploadSessions.get(transferId);
        if (session == null) {
            return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Upload session not found: " + transferId);
        }
        session.updateLastAccessTime();
        verifySession(session);
        return ApiResponse.success(session.toChunkStatusData());
    }

    private void verifySession(UploadSession session) {
        List<Integer> corruptedChunks = new ArrayList<>();

        for (int chunkIndex : session.getReceivedChunks()) {
            Path chunkFile = Path.of(session.getTempDirectory(), "chunk_" + chunkIndex);
            if (!Files.exists(chunkFile)) {
                corruptedChunks.add(chunkIndex);
                continue;
            }

            try {
                long actualSize = Files.size(chunkFile);
                long expectedSize = chunkIndex == session.getTotalChunks() - 1
                        ? (int) (session.getTotalSize() - (long) chunkIndex * session.getChunkSize())
                        : session.getChunkSize();

                if (actualSize != expectedSize) {
                    logger.warn("Corrupted chunk detected: session={}, chunk={}, expected size={}, actual size={}",
                            session.getTransferId(), chunkIndex, expectedSize, actualSize);
                    Files.deleteIfExists(chunkFile);
                    corruptedChunks.add(chunkIndex);
                }
            } catch (IOException e) {
                logger.warn("Failed to verify chunk file {}: {}", chunkFile, e.getMessage());
                corruptedChunks.add(chunkIndex);
            }
        }

        if (!corruptedChunks.isEmpty()) {
            // Remove corrupted chunks from session
            corruptedChunks.forEach(session::removeChunk);
        }
    }

    public ApiResponse<Void> cancelUpload(String transferId) {
        UploadSession session = uploadSessions.remove(transferId);
        if (session == null) {
            return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Upload session not found: " + transferId);
        }

        cleanupSessionTempFiles(session);
        logger.info("Upload session cancelled: {}", transferId);
        return ApiResponse.success(null);
    }

    public ApiResponse<ChunkedDownloadResult> downloadRange(String path, long start, long end) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.PATH_NOT_FOUND.getCode(), "File not found: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.PATH_IS_NOT_FILE.getCode(), "Cannot download directory: " + path);
            }

            long fileSize = Files.size(targetPath);

            // Validate range
            if (start < 0) start = 0;
            if (end < 0 || end >= fileSize) end = fileSize - 1;
            if (start > end) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Invalid range: " + start + "-" + end);
            }

            long length = end - start + 1;

            // Read the specified range
            byte[] data = new byte[(int) length];
            try (RandomAccessFile raf = new RandomAccessFile(targetPath.toFile(), "r")) {
                raf.seek(start);
                raf.readFully(data);
            }

            ChunkedDownloadResult result = new ChunkedDownloadResult(
                    data, start, end, fileSize,
                    targetPath.getFileName().toString()
            );

            return ApiResponse.success(result);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.ACCESS_DENIED.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to read file range: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to read file: " + e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getDownloadInfo(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "File not found: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Cannot download directory: " + path);
            }

            long fileSize = Files.size(targetPath);
            int recommendedChunks = (int) Math.ceil((double) fileSize / defaultChunkSize);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("path", targetPath.toString());
            info.put("fileName", targetPath.getFileName().toString());
            info.put("size", fileSize);
            info.put("chunkSize", defaultChunkSize);
            info.put("totalChunks", recommendedChunks);
            info.put("supportsRange", true);

            return ApiResponse.success(info);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to get download info: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to get download info: " + e.getMessage());
        }
    }

    public List<ChunkStatusData> listUploadSessions() {
        List<ChunkStatusData> list = new ArrayList<>();
        for (UploadSession session : uploadSessions.values()) {
            list.add(session.toChunkStatusData());
        }
        return list;
    }

    private void cleanupExpiredSessions() {
        List<String> expiredIds = new ArrayList<>();
        for (Map.Entry<String, UploadSession> entry : uploadSessions.entrySet()) {
            if (entry.getValue().isExpired(sessionTimeoutMs)) {
                expiredIds.add(entry.getKey());
            }
        }

        for (String transferId : expiredIds) {
            UploadSession session = uploadSessions.remove(transferId);
            if (session != null) {
                cleanupSessionTempFiles(session);
                logger.info("Expired upload session cleaned up: {}", transferId);
            }
        }
    }

    private void cleanupSessionTempFiles(UploadSession session) {
        try {
            Path sessionTempDir = Path.of(session.getTempDirectory());
            if (Files.exists(sessionTempDir)) {
                Files.walk(sessionTempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                logger.warn("Failed to delete temp file: {}", p, e);
                            }
                        });
            }
        } catch (IOException e) {
            logger.warn("Failed to cleanup session temp files: {}", session.getTransferId(), e);
        }
    }

    private Path resolvePath(String path) {
        if (path == null || path.isEmpty() || path.equals(".")) {
            return baseDirectory;
        }

        Path resolved;
        // Check if path is absolute
        Path p = Path.of(path);
        if (p.isAbsolute()) {
            resolved = p.normalize();
        } else {
            resolved = baseDirectory.resolve(path).normalize();
        }

        // If allowOutsideBase is false, we must ensure the resolved path is within baseDirectory.
        // However, if the user provided an absolute path that happens to be inside baseDirectory, that's fine.
        // If the user provided an absolute path outside baseDirectory, and allowOutsideBase is false, we throw exception.
        if (!allowOutsideBase && !resolved.startsWith(baseDirectory)) {
            throw new SecurityException("Access denied: path outside base directory (" + resolved + ")");
        }

        return resolved;
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupExecutor.shutdownNow();
        }
    }

    public static class ChunkedDownloadResult {
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
}