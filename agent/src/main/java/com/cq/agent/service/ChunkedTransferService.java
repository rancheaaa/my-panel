 package com.cq.agent.service;

 import com.cq.agent.config.AgentConfig;
 import com.cq.agent.model.FileInfo;
 import com.cq.agent.model.UploadSession;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import java.io.IOException;
 import java.io.RandomAccessFile;
 import java.nio.ByteBuffer;
 import java.nio.channels.FileChannel;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.nio.file.StandardOpenOption;
 import java.security.MessageDigest;
 import java.security.NoSuchAlgorithmException;
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
            Files.createDirectories(tempDirectory);
            logger.info("Chunked transfer service initialized. Temp dir: {}", tempDirectory);
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

    public FileService.ServiceResult<UploadSession> initUpload(String targetPath, String fileName, long totalSize) {
        try {
            if (totalSize <= 0) {
                return FileService.ServiceResult.error("Invalid file size");
            }
            if (totalSize > maxFileSize) {
                return FileService.ServiceResult.error("File size exceeds maximum allowed: " + maxFileSize);
            }

            Path resolvedPath = resolvePath(targetPath);

            // Calculate chunks
            int totalChunks = (int) Math.ceil((double) totalSize / defaultChunkSize);

            // Generate session ID
            String sessionId = UUID.randomUUID().toString().replace("-", "");

            // Create session temp directory
            Path sessionTempDir = tempDirectory.resolve(sessionId);
            Files.createDirectories(sessionTempDir);

            UploadSession session = new UploadSession(
                    sessionId,
                    resolvedPath.toString(),
                    fileName,
                    totalSize,
                    totalChunks,
                    defaultChunkSize,
                    sessionTempDir.toString()
            );

            uploadSessions.put(sessionId, session);
            logger.info("Upload session created: {}, file: {}, size: {}, chunks: {}",
                    sessionId, fileName, totalSize, totalChunks);

            return FileService.ServiceResult.success(session);
        } catch (SecurityException e) {
            return FileService.ServiceResult.error("Access denied: " + targetPath);
        } catch (IOException e) {
            logger.error("Failed to initialize upload", e);
            return FileService.ServiceResult.error("Failed to initialize upload: " + e.getMessage());
        }
    }

    public FileService.ServiceResult<Map<String, Object>> uploadChunk(String sessionId, int chunkIndex, byte[] data) {
        UploadSession session = uploadSessions.get(sessionId);
        if (session == null) {
            return FileService.ServiceResult.error("Upload session not found: " + sessionId);
        }

        if (session.isMerged()) {
            return FileService.ServiceResult.error("Upload session already completed and merged");
        }

        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            return FileService.ServiceResult.error("Invalid chunk index: " + chunkIndex);
        }

        // Validate chunk size (last chunk may be smaller)
        int expectedSize = chunkIndex == session.getTotalChunks() - 1
                ? (int) (session.getTotalSize() - (long) chunkIndex * session.getChunkSize())
                : session.getChunkSize();

        if (data.length != expectedSize) {
            return FileService.ServiceResult.error("Invalid chunk size. Expected: " + expectedSize + ", got: " + data.length);
        }

        try {
            // Write chunk to temp file
            Path chunkFile = Path.of(session.getTempDirectory(), "chunk_" + chunkIndex);
            Files.write(chunkFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            session.markChunkReceived(chunkIndex);
            logger.debug("Chunk {} received for session {}", chunkIndex, sessionId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId", sessionId);
            result.put("chunkIndex", chunkIndex);
            result.put("received", session.getReceivedChunkCount());
            result.put("total", session.getTotalChunks());
            result.put("progress", String.format("%.2f%%", session.getProgress()));
            result.put("completed", session.isCompleted());

            return FileService.ServiceResult.success(result);
        } catch (IOException e) {
            logger.error("Failed to write chunk {} for session {}", chunkIndex, sessionId, e);
            return FileService.ServiceResult.error("Failed to write chunk: " + e.getMessage());
        }
    }

    public FileService.ServiceResult<FileInfo> mergeChunks(String sessionId) {
        UploadSession session = uploadSessions.get(sessionId);
        if (session == null) {
            return FileService.ServiceResult.error("Upload session not found: " + sessionId);
        }

        if (!session.isCompleted()) {
            return FileService.ServiceResult.error("Upload not completed. Missing chunks: " + session.getMissingChunks());
        }

        if (session.isMerged()) {
            return FileService.ServiceResult.error("Chunks already merged");
        }

        try {
            Path targetPath = Path.of(session.getTargetPath());

            // Create parent directories
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            try (FileChannel outChannel = FileChannel.open(targetPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                MessageDigest md5 = MessageDigest.getInstance("MD5");

                for (int i = 0; i < session.getTotalChunks(); i++) {
                    Path chunkFile = Path.of(session.getTempDirectory(), "chunk_" + i);
                    byte[] chunkData = Files.readAllBytes(chunkFile);
                    md5.update(chunkData);
                    outChannel.write(ByteBuffer.wrap(chunkData));
                }

                // Calculate checksum
                byte[] digest = md5.digest();
                StringBuilder checksum = new StringBuilder();
                for (byte b : digest) {
                    checksum.append(String.format("%02x", b));
                }
                session.setChecksum(checksum.toString());
            }

            session.setMerged(true);
            logger.info("Chunks merged for session {}: {}", sessionId, targetPath);

            // Cleanup temp files
            cleanupSessionTempFiles(session);

            return FileService.ServiceResult.success(FileInfo.fromPath(targetPath));
        } catch (NoSuchAlgorithmException e) {
            return FileService.ServiceResult.error("MD5 algorithm not available");
        } catch (IOException e) {
            logger.error("Failed to merge chunks for session {}", sessionId, e);
            return FileService.ServiceResult.error("Failed to merge chunks: " + e.getMessage());
        }
    }

    public FileService.ServiceResult<Map<String, Object>> getUploadStatus(String sessionId) {
        UploadSession session = uploadSessions.get(sessionId);
        if (session == null) {
            return FileService.ServiceResult.error("Upload session not found: " + sessionId);
        }
        return FileService.ServiceResult.success(session.toMap());
    }

    public FileService.ServiceResult<Void> cancelUpload(String sessionId) {
        UploadSession session = uploadSessions.remove(sessionId);
        if (session == null) {
            return FileService.ServiceResult.error("Upload session not found: " + sessionId);
        }

        cleanupSessionTempFiles(session);
        logger.info("Upload session cancelled: {}", sessionId);
        return FileService.ServiceResult.success(null);
    }

    public FileService.ServiceResult<ChunkedDownloadResult> downloadRange(String path, long start, long end) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return FileService.ServiceResult.error("File not found: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return FileService.ServiceResult.error("Cannot download directory: " + path);
            }

            long fileSize = Files.size(targetPath);

            // Validate range
            if (start < 0) start = 0;
            if (end < 0 || end >= fileSize) end = fileSize - 1;
            if (start > end) {
                return FileService.ServiceResult.error("Invalid range: " + start + "-" + end);
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

            return FileService.ServiceResult.success(result);
        } catch (SecurityException e) {
            return FileService.ServiceResult.error("Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to read file range: {}", path, e);
            return FileService.ServiceResult.error("Failed to read file: " + e.getMessage());
        }
    }

    public FileService.ServiceResult<Map<String, Object>> getDownloadInfo(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return FileService.ServiceResult.error("File not found: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return FileService.ServiceResult.error("Cannot download directory: " + path);
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

            return FileService.ServiceResult.success(info);
        } catch (SecurityException e) {
            return FileService.ServiceResult.error("Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to get download info: {}", path, e);
            return FileService.ServiceResult.error("Failed to get file info: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listUploadSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UploadSession session : uploadSessions.values()) {
            list.add(session.toMap());
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

        for (String sessionId : expiredIds) {
            UploadSession session = uploadSessions.remove(sessionId);
            if (session != null) {
                cleanupSessionTempFiles(session);
                logger.info("Expired upload session cleaned up: {}", sessionId);
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
            logger.warn("Failed to cleanup session temp files: {}", session.getSessionId(), e);
        }
    }

    private Path resolvePath(String path) {
        if (path == null || path.isEmpty() || path.equals(".")) {
            return baseDirectory;
        }

        Path resolved;
        if (path.startsWith("/") || (IS_WINDOWS && path.length() > 1 && path.charAt(1) == ':')) {
            resolved = Path.of(path).toAbsolutePath().normalize();
        } else {
            resolved = baseDirectory.resolve(path).normalize();
        }

        if (!allowOutsideBase && !resolved.startsWith(baseDirectory)) {
            throw new SecurityException("Access denied: path outside base directory");
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
