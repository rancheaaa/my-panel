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
import java.nio.file.StandardCopyOption;
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

    /**
     * 初始化分片上传会话
     * 
     * 该方法用于创建一个新的分片上传会话或恢复已存在的上传会话。分片上传机制允许大文件被分割成多个小块进行上传，
     * 支持断点续传和网络中断后的重试。每个上传会话都有唯一的transferId标识，客户端可以使用自定义的transferId
     * 或由系统自动生成。
     * 
     * 方法执行流程：
     * 1. 验证文件总大小是否合法（不能为负数且不能超过最大文件大小限制）
     * 2. 解析并验证目标目录路径，确保路径在允许的范围内
     * 3. 确保目标目录存在且可写，如果不存在则尝试创建
     * 4. 检查磁盘空间是否足够（需要文件大小的2倍空间，用于临时文件和最终文件）
     * 5. 计算文件需要分割成的分片总数
     * 6. 确定最终使用的transferId（使用客户端提供的或自动生成）
     * 7. 检查是否已存在相同transferId的会话，如果存在且参数一致则恢复该会话
     * 8. 在目标目录下创建以".transferId"命名的临时目录用于存储分片文件
     * 9. 验证临时目录的写权限（通过创建和删除测试文件）
     * 10. 创建并注册上传会话对象
     * 
     * @param traceId 用于追踪整个上传过程的唯一标识，贯穿所有相关日志，便于问题排查和链路追踪
     * @param transferId 上传传输的唯一标识符，客户端可自定义或留空由系统自动生成UUID。
     *                   如果提供了transferId且该ID对应的会话已存在且参数一致，则恢复该会话；
     *                   如果参数不一致则返回冲突错误。支持断点续传场景。
     * @param destFileDir 目标文件存储目录的相对路径或绝对路径。路径会被解析为绝对路径并规范化，
     *                    如果是相对路径则相对于baseDirectory。目录必须存在且可写，否则会尝试创建。
     * @param destFileName 目标文件名，不包含路径。文件将在destFileDir目录下创建。
     * @param totalSize 要上传的文件总大小（字节）。必须为非负数且不超过配置的最大文件大小限制。
     *                  该值用于计算分片数量和验证上传完成状态。
     * @return ApiResponse<ChunkStatusData> 包含上传会话状态的响应对象。
     *         成功时：返回包含transferId、总大小、分片数量、已接收分片索引等信息的ChunkStatusData
     *         失败时：返回包含错误码和错误信息的失败响应，可能的错误包括：
     *         - INVALID_TOTAL_SIZE: 文件大小为负数
     *         - FILE_SIZE_EXCEEDS_LIMIT: 文件大小超过最大限制
     *         - DIRECTORY_CREATE_NO_PERMISSION: 无权限创建目录
     *         - DIRECTORY_CREATE_FAILED: 目录创建失败
     *         - DIRECTORY_NOT_WRITABLE: 目录不可写
     *         - INSUFFICIENT_DISK_SPACE: 磁盘空间不足
     *         - UPLOAD_SESSION_CONFLICT: transferId冲突（相同ID但参数不同）
     *         - TEMP_DIR_CREATE_FAILED: 临时目录创建失败
     *         - TEMP_DIR_NO_WRITE_PERMISSION: 临时目录无写权限
     *         - TEMP_DIR_VERIFICATION_FAILED: 临时目录验证失败
     *         - INIT_UPLOAD_FAILED: 初始化上传失败（其他未预期的错误）
     */
    public ApiResponse<com.cq.agent.dto.ChunkInitResponse> initUpload(String traceId, String transferId, String destFileDir, String destFileName, long totalSize) {
        try {
            logger.debug("[traceId={}] Initializing upload: transferId={}, destFileDir={}, destFileName={}, totalSize={}", 
                    traceId, transferId, destFileDir, destFileName, totalSize);

            ApiResponse<Void> sizeValidation = validateTotalSize(totalSize);
            if (!sizeValidation.isSuccess()) {
                return ApiResponse.failure(sizeValidation.getCode(), sizeValidation.getMsg());
            }

            Path resolvedPath = resolvePath(destFileDir);
            logger.debug("[traceId={}] Resolved path: {}",traceId, resolvedPath);

            ApiResponse<Void> dirValidation = ensureDestinationDirectory(resolvedPath, traceId);
            if (!dirValidation.isSuccess()) {
                return ApiResponse.failure(dirValidation.getCode(), dirValidation.getMsg());
            }

            ApiResponse<Void> spaceValidation = checkDiskSpace(resolvedPath, totalSize, traceId);
            if (!spaceValidation.isSuccess()) {
                return ApiResponse.failure(spaceValidation.getCode(), spaceValidation.getMsg());
            }

            int totalChunks = calculateTotalChunks(totalSize);
            logger.debug("[traceId={}] Calculated chunks: totalChunks={}, chunkSize={}", traceId, totalChunks, defaultChunkSize);

            String finalTransferId = determineTransferId(transferId);
            logger.debug("[traceId={}] Using transferId: {}", traceId, finalTransferId);

            ApiResponse<ChunkStatusResponse> existingSessionCheck = handleExistingSession(finalTransferId, resolvedPath, totalSize, traceId);
            if (existingSessionCheck != null) {
                com.cq.agent.dto.ChunkInitResponse response = new com.cq.agent.dto.ChunkInitResponse(
                        existingSessionCheck.getData().getTransferId(),
                        existingSessionCheck.getData().getTotalSize(),
                        existingSessionCheck.getData().getTotalChunks(),
                        existingSessionCheck.getData().getChunkSize(),
                        existingSessionCheck.getData().getMissingChunks()
                );
                return ApiResponse.success(response);
            }

            Path sessionTempDir = resolvedPath.resolve("." + finalTransferId);
            ApiResponse<Void> tempDirCreation = createSessionTempDirectory(sessionTempDir, traceId);
            if (!tempDirCreation.isSuccess()) {
                return ApiResponse.failure(tempDirCreation.getCode(), tempDirCreation.getMsg());
            }

            ApiResponse<Void> writePermissionCheck = verifyWritePermission(sessionTempDir, traceId);
            if (!writePermissionCheck.isSuccess()) {
                return ApiResponse.failure(writePermissionCheck.getCode(), writePermissionCheck.getMsg());
            }

            UploadSession session = createUploadSession(finalTransferId, traceId, resolvedPath, destFileName, totalSize, totalChunks, sessionTempDir);
            uploadSessions.put(finalTransferId, session);
            logger.info("[traceId={}] Upload session created: {}, destDir:{} destFile: {}, size: {}, chunks: {}",
                    traceId, finalTransferId, resolvedPath, destFileName, totalSize, totalChunks);

            return ApiResponse.success(session.toChunkInitResponse());
        } catch (Exception e) {
            logger.error("[traceId={}] Failed to initialize upload", traceId, e);
            return ApiResponse.failure(ApiCode.INIT_UPLOAD_FAILED.getCode(), "Failed to initialize upload: " + e.getMessage());
        }
    }

    private ApiResponse<Void> validateTotalSize(long totalSize) {
        if (totalSize < 0) {
            return ApiResponse.failure(ApiCode.INVALID_TOTAL_SIZE.getCode(), "total size can't less than zero");
        }
        if (totalSize > maxFileSize) {
            return ApiResponse.failure(ApiCode.FILE_SIZE_EXCEEDS_LIMIT.getCode(), "File size exceeds maximum allowed: " + maxFileSize);
        }
        return ApiResponse.success(null);
    }

    private ApiResponse<Void> ensureDestinationDirectory(Path resolvedPath, String traceId) {
        if (!Files.exists(resolvedPath)) {
            try {
                Files.createDirectories(resolvedPath);
                logger.info("[traceId={}] Created destination directory: {}", traceId, resolvedPath);
            } catch (SecurityException e) {
                logger.error("[traceId={}] No permission to create directory: {}", traceId, resolvedPath, e);
                return ApiResponse.failure(ApiCode.DIRECTORY_CREATE_NO_PERMISSION.getCode(), 
                        "No permission to create directory: " + resolvedPath);
            } catch (IOException e) {
                logger.error("[traceId={}] Failed to create directory: {}", traceId, resolvedPath, e);
                return ApiResponse.failure(ApiCode.DIRECTORY_CREATE_FAILED.getCode(), 
                        "Failed to create directory: " + resolvedPath + " - " + e.getMessage());
            }
        }

        if (!Files.isWritable(resolvedPath)) {
            logger.error("[traceId={}] Directory is not writable: {}", traceId, resolvedPath);
            return ApiResponse.failure(ApiCode.DIRECTORY_NOT_WRITABLE.getCode(), 
                    "Directory is not writable: " + resolvedPath);
        }
        return ApiResponse.success(null);
    }

    private ApiResponse<Void> checkDiskSpace(Path resolvedPath, long totalSize, String traceId) {
        try {
            java.nio.file.FileStore fileStore = Files.getFileStore(resolvedPath);
            long usableSpace = fileStore.getUsableSpace();
            long requiredSpace = totalSize * 2L;
            long minimumFreeSpace = 1024L * 1024L * 1024L;

            logger.debug("[traceId={}] Disk space check: usable={}, required={}, minimum={}",
                    traceId, usableSpace, requiredSpace, minimumFreeSpace);

            if (usableSpace < requiredSpace) {
                logger.error("[traceId={}] Insufficient disk space: required={}, available={}", traceId, requiredSpace, usableSpace);
                return ApiResponse.failure(ApiCode.INSUFFICIENT_DISK_SPACE.getCode(), 
                        "Insufficient disk space: required " + formatBytes(requiredSpace) + 
                        ", available " + formatBytes(usableSpace));
            }

            if (usableSpace - requiredSpace < minimumFreeSpace) {
                logger.warn("[traceId={}] Low disk space after upload: remaining={}, minimum={}",
                        traceId, usableSpace - requiredSpace, minimumFreeSpace);
            }
            return ApiResponse.success(null);
        } catch (IOException e) {
            logger.error("[traceId={}] Failed to check disk space: {}", traceId, resolvedPath, e);
            return ApiResponse.failure(ApiCode.GET_DISK_SPACE_FAILED.getCode(), 
                    "Failed to check disk space: " + e.getMessage());
        }
    }

    private int calculateTotalChunks(long totalSize) {
        return (int) Math.ceil((double) totalSize / defaultChunkSize);
    }

    private String determineTransferId(String transferId) {
        return (transferId != null && !transferId.isEmpty())
                ? transferId
                : UUID.randomUUID().toString().replace("-", "");
    }

    private ApiResponse<ChunkStatusResponse> handleExistingSession(String transferId, Path resolvedPath, long totalSize, String traceId) {
        if (!uploadSessions.containsKey(transferId)) {
            return null;
        }

        UploadSession existing = uploadSessions.get(transferId);
        if (existing.getDestFileDir().equals(resolvedPath.toString()) && existing.getTotalSize() == totalSize) {
            logger.info("[traceId={}] Resuming existing upload session for transferId: {}", traceId, transferId);
            verifySession(existing, traceId);
            return ApiResponse.success(existing.toChunkStatusResponse());
        } else {
            return ApiResponse.failure(ApiCode.UPLOAD_SESSION_CONFLICT.getCode(), "transferId conflict: another file is being uploaded with same ID");
        }
    }

    private ApiResponse<Void> createSessionTempDirectory(Path sessionTempDir, String traceId) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Files.createDirectories(sessionTempDir);
                if (Files.exists(sessionTempDir) && Files.isDirectory(sessionTempDir)) {
                    logger.debug("[traceId={}] Created session temp directory: {}", traceId, sessionTempDir);
                    return ApiResponse.success(null);
                } else {
                    logger.warn("[traceId={}] Temp directory creation verification failed, attempt {}/{}", traceId, attempt, maxRetries);
                    if (attempt < maxRetries) {
                        Thread.sleep(1000);
                    }
                }
            } catch (SecurityException e) {
                logger.error("[traceId={}] No permission to create temp directory: {}", traceId, sessionTempDir, e);
                return ApiResponse.failure(ApiCode.DIRECTORY_CREATE_NO_PERMISSION.getCode(), 
                        "No permission to create temp directory: " + sessionTempDir);
            } catch (IOException e) {
                logger.error("[traceId={}] Failed to create temp directory: {}", traceId, sessionTempDir, e);
                if (attempt == maxRetries) {
                    return ApiResponse.failure(ApiCode.TEMP_DIR_CREATE_FAILED.getCode(), 
                            "Failed to create temp directory after " + maxRetries + " attempts: " + sessionTempDir + " - " + e.getMessage());
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return ApiResponse.failure(ApiCode.TEMP_DIR_CREATE_FAILED.getCode(), 
                            "Interrupted while waiting to retry temp directory creation");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ApiResponse.failure(ApiCode.TEMP_DIR_CREATE_FAILED.getCode(), 
                        "Interrupted while waiting to retry temp directory creation");
            }
        }

        logger.error("[traceId={}] Failed to create temp directory after {} attempts: {}", traceId, maxRetries, sessionTempDir);
        return ApiResponse.failure(ApiCode.TEMP_DIR_VERIFICATION_FAILED.getCode(), 
                "Failed to verify temp directory creation after " + maxRetries + " attempts: " + sessionTempDir);
    }

    private ApiResponse<Void> verifyWritePermission(Path sessionTempDir, String traceId) {
        Path testFile = sessionTempDir.resolve(".write_test_" + System.currentTimeMillis());
        try {
            Files.writeString(testFile, "write test", StandardOpenOption.CREATE_NEW);
            logger.debug("[traceId={}] Successfully created test file: {}", traceId, testFile);
        } catch (SecurityException e) {
            logger.error("[traceId={}] No write permission in temp directory: {}", traceId, sessionTempDir, e);
            return ApiResponse.failure(ApiCode.TEMP_DIR_NO_WRITE_PERMISSION.getCode(), 
                    "No write permission in temp directory: " + sessionTempDir);
        } catch (IOException e) {
            logger.error("[traceId={}] Failed to create test file in temp directory: {}", traceId, sessionTempDir, e);
            return ApiResponse.failure(ApiCode.TEMP_DIR_NO_WRITE_PERMISSION.getCode(), 
                    "Failed to create test file in temp directory: " + sessionTempDir + " - " + e.getMessage());
        }

        try {
            Files.deleteIfExists(testFile);
            logger.debug("[traceId={}] Successfully deleted test file: {}", traceId, testFile);
        } catch (IOException e) {
            logger.error("[traceId={}] Failed to delete test file: {}", traceId, testFile, e);
            return ApiResponse.failure(ApiCode.TEMP_DIR_VERIFICATION_FAILED.getCode(), 
                    "Failed to delete test file: " + testFile + " - " + e.getMessage());
        }

        if (Files.exists(testFile)) {
            logger.error("[traceId={}] Test file still exists after deletion attempt: {}", traceId, testFile);
            return ApiResponse.failure(ApiCode.TEMP_DIR_VERIFICATION_FAILED.getCode(), 
                    "Test file still exists after deletion attempt: " + testFile);
        }

        return ApiResponse.success(null);
    }

    private UploadSession createUploadSession(String transferId, String traceId, Path resolvedPath, String destFileName, 
                                          long totalSize, int totalChunks, Path sessionTempDir) {
        return new UploadSession(
                transferId,
                traceId,
                resolvedPath.toString(),
                destFileName,
                totalSize,
                totalChunks,
                defaultChunkSize,
                sessionTempDir.toString()
        );
    }

    /**
     * 上传单个文件分片
     * 
     * 该方法用于接收并存储大文件的一个分片。分片上传机制允许大文件被分割成多个小块进行独立上传，
     * 每个分片都有唯一的索引标识。方法会验证分片的合法性，检查是否重复上传，将分片数据写入临时文件，
     * 并更新上传会话状态。支持重试机制以应对临时的IO错误，确保数据完整性。
     * 
     * 方法执行流程：
     * 1. 验证上传会话是否存在（通过transferId查找）
     * 2. 检查会话是否已经合并完成，如果已完成则拒绝接收新分片
     * 3. 验证分片索引是否在有效范围内（0到totalChunks-1）
     * 4. 验证分片大小是否与预期一致（最后一个分片可能小于标准分片大小）
     * 5. 将分片数据写入临时文件，文件命名格式：{destFileName}_chunk_{chunkIndex}
     * 6. 验证实际写入的文件大小是否与预期一致，确保数据完整性
     * 7. 如果写入失败或大小不一致，最多重试3次，每次间隔1秒
     * 8. 重试失败后删除可能存在的不完整文件
     * 9. 分片验证通过后，标记该分片已接收
     * 10. 构建并返回上传结果，包含完成状态和缺失分片数量
     * 
     * 重试机制说明：
     * - 最多重试3次，每次间隔1秒
     * - 每次写入后都会验证文件大小，确保数据完整性
     * - 如果3次重试都失败，会删除可能存在的不完整文件
     * - 重试过程中会记录详细的日志信息
     * 
     * @param traceId 用于追踪整个上传过程的唯一标识，贯穿所有相关日志，便于问题排查和链路追踪
     * @param request 分片上传请求对象，包含以下字段：
     *               - transferId: 上传会话的唯一标识符，必须通过initUpload方法创建
     *               - chunkIndex: 分片索引，从0开始，必须小于totalChunks
     *               - destFileName: 目标文件名，必须与initUpload时传入的文件名一致
     * @param content 分片数据的字节数组，长度必须与预期分片大小一致
     * @return ApiResponse<ChunkUploadResultData> 包含分片上传结果的响应对象。
     *         成功时：返回包含transferId、完成状态、缺失分片数量的ChunkUploadResultData
     *         失败时：返回包含错误码和错误信息的失败响应，可能的错误包括：
     *         - NOT_FOUND: 上传会话不存在
     *         - CHUNKS_ALREADY_MERGED: 分片已经合并完成，不能再接收新分片
     *         - INVALID_CHUNK_INDEX: 分片索引无效（超出范围）
     *         - INVALID_CHUNK_SIZE: 分片大小与预期不符
     *         - CHUNK_WRITE_FAILED: 分片写入失败（包括IO错误和重试失败）
     *         - CHUNK_WRITE_SIZE_MISMATCH: 分片写入后大小验证失败
     */
    public ApiResponse<ChunkUploadResponse> uploadChunk(String traceId, ChunkUploadRequest request, byte[] content) {
        final String transferId = request.getTransferId();
        
        ApiResponse<UploadSession> sessionValidation = validateChunkUploadSession(transferId, traceId);
        if (!sessionValidation.isSuccess()) {
            return ApiResponse.failure(sessionValidation.getCode(), sessionValidation.getMsg());
        }
        
        UploadSession session = sessionValidation.getData();
        logger.debug("[traceId={}] Processing chunk upload: transferId={}, chunkIndex={}", traceId, transferId, request.getChunkIndex());

        ApiResponse<Void> mergedCheck = checkSessionNotMerged(session, transferId, traceId);
        if (!mergedCheck.isSuccess()) {
            return ApiResponse.failure(mergedCheck.getCode(), mergedCheck.getMsg());
        }

        final int chunkIndex = request.getChunkIndex();
        ApiResponse<Void> indexValidation = validateChunkIndex(session, chunkIndex, traceId);
        if (!indexValidation.isSuccess()) {
            return ApiResponse.failure(indexValidation.getCode(), indexValidation.getMsg());
        }

        int expectedChunkSize = request.getChunkSize();
        if (expectedChunkSize <= 0) {
            expectedChunkSize = content.length;
        }
        
        ApiResponse<Void> sizeValidation = validateChunkSize(session, chunkIndex, expectedChunkSize, traceId);
        if (!sizeValidation.isSuccess()) {
            return ApiResponse.failure(sizeValidation.getCode(), sizeValidation.getMsg());
        }
        try {
            final String destFileName = request.getDestFileName();
            Path chunkFile = Path.of(session.getTempDirectory(), destFileName + "_chunk_" + chunkIndex);
            
            // 校验当前分片是否已经上传过，如果已经上传过则直接返回成功，避免重复写入
            ApiResponse<Void> duplicateCheck = checkDuplicateChunk(session, chunkFile, chunkIndex, expectedChunkSize, traceId, transferId);
            if (duplicateCheck.isSuccess()) {
                logger.debug("[traceId={}] Chunk {} already uploaded for session {}, skipping duplicate upload", traceId, chunkIndex, transferId);
                return buildChunkUploadResult(session, transferId, traceId);
            }
            
            ApiResponse<Void> writeResult = writeChunkWithRetry(chunkFile, content, traceId, transferId, chunkIndex);
            if (!writeResult.isSuccess()) {
                return ApiResponse.failure(writeResult.getCode(), writeResult.getMsg());
            }
            
            session.markChunkReceived(chunkIndex);
            logger.debug("[traceId={}] Chunk {} received for session {}, total received: {}/{}", 
                    traceId, chunkIndex, transferId, session.getReceivedChunkCount(), session.getTotalChunks());

            return buildChunkUploadResult(session, transferId, traceId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[traceId={}] Chunk write interrupted for session {}: chunkIndex={}", traceId, transferId, chunkIndex, e);
            return ApiResponse.failure(ApiCode.CHUNK_WRITE_FAILED.getCode(), "Chunk write interrupted: " + e.getMessage());
        } catch (Exception e) {
            logger.error("[traceId={}] Unexpected error writing chunk {} for session {}", traceId, chunkIndex, transferId, e);
            return ApiResponse.failure(ApiCode.CHUNK_WRITE_FAILED.getCode(), "Failed to write chunk: " + e.getMessage());
        }
    }

    private ApiResponse<UploadSession> validateChunkUploadSession(String transferId, String traceId) {
        UploadSession session = uploadSessions.get(transferId);
        if (session == null) {
            logger.debug("[traceId={}] Upload session not found: {}", traceId, transferId);
            return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Upload session not found: " + transferId);
        }
        return ApiResponse.success(session);
    }

    private ApiResponse<Void> checkSessionNotMerged(UploadSession session, String transferId, String traceId) {
        if (session.isMerged()) {
            logger.debug("[traceId={}] Upload session already merged: {}", traceId, transferId);
            return ApiResponse.failure(ApiCode.CHUNKS_ALREADY_MERGED.getCode(), "Upload session already completed and merged");
        }
        return ApiResponse.success(null);
    }

    private ApiResponse<Void> validateChunkIndex(UploadSession session, int chunkIndex, String traceId) {
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            logger.debug("[traceId={}] Invalid chunk index: {} for session {} (totalChunks: {})", 
                    traceId, chunkIndex, session.getTransferId(), session.getTotalChunks());
            return ApiResponse.failure(ApiCode.INVALID_CHUNK_INDEX.getCode(), "Invalid chunk index: " + chunkIndex);
        }
        return ApiResponse.success(null);
    }

    private ApiResponse<Void> validateChunkSize(UploadSession session, int chunkIndex, int contentSize, String traceId) {
        int expectedSize = calculateExpectedChunkSize(session, chunkIndex);
        if (contentSize != expectedSize) {
            logger.debug("[traceId={}] Invalid chunk size for session {}: expected={}, got={}", 
                    traceId, session.getTransferId(), expectedSize, contentSize);
            return ApiResponse.failure(ApiCode.INVALID_CHUNK_SIZE.getCode(), 
                    "Invalid chunk size. Expected: " + expectedSize + ", got: " + contentSize);
        }
        return ApiResponse.success(null);
    }

    private int calculateExpectedChunkSize(UploadSession session, int chunkIndex) {
        return chunkIndex == session.getTotalChunks() - 1
                ? (int) (session.getTotalSize() - (long) chunkIndex * session.getChunkSize())
                : session.getChunkSize();
    }

    /**
     * 校验分片是否重复上传
     * 
     * 该方法用于检查指定分片是否已经成功上传过，避免重复写入和浪费资源。
     * 通过检查 UploadSession 中的已接收分片索引集合以及物理文件的存在性和大小来验证。
     * 
     * 方法执行流程：
     * 1. 检查分片索引是否已在 UploadSession 的已接收集合中
     * 2. 如果不在集合中，说明该分片未上传过，返回失败（需要上传）
     * 3. 如果在集合中，进一步检查物理文件是否存在
     * 4. 如果物理文件不存在，说明文件可能被意外删除，返回失败（需要重新上传）
     * 5. 如果物理文件存在，验证文件大小是否与预期一致
     * 6. 如果文件大小一致，说明分片已成功上传，返回成功（跳过重复上传）
     * 7. 如果文件大小不一致，说明文件可能损坏，返回失败（需要重新上传）
     * 
     * @param session 上传会话对象，包含已接收分片的索引集合
     * @param chunkFile 分片文件的物理路径
     * @param chunkIndex 分片索引
     * @param expectedSize 预期的分片大小
     * @param traceId 追踪ID，用于日志记录
     * @param transferId 传输ID，用于日志记录
     * @return ApiResponse<Void> 成功表示分片已存在且有效（跳过上传），失败表示需要上传
     */
    private ApiResponse<Void> checkDuplicateChunk(UploadSession session, Path chunkFile, int chunkIndex, 
                                                  int expectedSize, String traceId, String transferId) {
        if (!session.isChunkReceived(chunkIndex)) {
            logger.debug("[traceId={}] Chunk {} not yet received for session {}", traceId, chunkIndex, transferId);
            return ApiResponse.failure(ApiCode.CHUNK_NOT_RECEIVED.getCode(), "Chunk not yet received");
        }
        
        if (!Files.exists(chunkFile)) {
            logger.warn("[traceId={}] Chunk {} marked as received but file missing for session {}, need to re-upload", 
                    traceId, chunkIndex, transferId);
            return ApiResponse.failure(ApiCode.CHUNK_FILE_MISSING.getCode(), "Chunk file missing, need to re-upload");
        }
        
        try {
            long actualSize = Files.size(chunkFile);
            if (actualSize == expectedSize) {
                logger.debug("[traceId={}] Chunk {} already exists with correct size {} for session {}", 
                        traceId, chunkIndex, actualSize, transferId);
                return ApiResponse.success(null);
            } else {
                logger.warn("[traceId={}] Chunk {} file size mismatch for session {}: expected={}, actual={}, need to re-upload", 
                        traceId, chunkIndex, transferId, expectedSize, actualSize);
                return ApiResponse.failure(ApiCode.CHUNK_FILE_SIZE_MISMATCH.getCode(),
                        "Chunk file size mismatch. Expected: " + expectedSize + ", actual: " + actualSize);
            }
        } catch (IOException e) {
            logger.error("[traceId={}] Failed to check chunk file size for session {}: chunkIndex={}", 
                    traceId, transferId, chunkIndex, e);
            return ApiResponse.failure(ApiCode.CHUNK_FILE_CHECK_FAILED.getCode(), 
                    "Failed to check chunk file: " + e.getMessage());
        }
    }

    private ApiResponse<Void> writeChunkWithRetry(Path chunkFile, byte[] content, String traceId, String transferId, int chunkIndex) 
            throws InterruptedException {
        logger.debug("[traceId={}] Writing chunk to file: {}", traceId, chunkFile);
        
        int maxRetries = 3;
        int retryIntervalMs = 1000;
        boolean writeSuccess = false;
        IOException lastException = null;
        long lastActualSize = 0;
        int expectedSize = content.length;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Files.write(chunkFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                
                long actualSize = Files.size(chunkFile);
                if (actualSize == expectedSize) {
                    writeSuccess = true;
                    if (attempt > 1) {
                        logger.info("[traceId={}] Chunk write succeeded on attempt {}/{}: chunkIndex={}, size={}", 
                                traceId, attempt, maxRetries, chunkIndex, actualSize);
                    }
                    break;
                }
                
                lastActualSize = actualSize;
                if (attempt < maxRetries) {
                    logger.warn("[traceId={}] Chunk write size verification failed (attempt {}/{}), retrying in {}ms: chunkIndex={}, expected={}, actual={}", 
                            traceId, attempt, maxRetries, retryIntervalMs, chunkIndex, expectedSize, actualSize);
                    Thread.sleep(retryIntervalMs);
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    logger.warn("[traceId={}] Chunk write failed (attempt {}/{}), retrying in {}ms: chunkIndex={}", 
                            traceId, attempt, maxRetries, retryIntervalMs, chunkIndex, e);
                    Thread.sleep(retryIntervalMs);
                }
            }
        }
        
        if (!writeSuccess) {
            logger.error("[traceId={}] Chunk write failed after {} attempts for session {}: chunkIndex={}, expected={}, lastActual={}, exception={}", 
                    traceId, maxRetries, transferId, chunkIndex, expectedSize, lastActualSize, 
                    lastException != null ? lastException.getMessage() : "size mismatch");
            
            deleteIncompleteChunkFile(chunkFile, traceId);
            
            if (lastException != null) {
                return ApiResponse.failure(ApiCode.CHUNK_WRITE_FAILED.getCode(), 
                        "Chunk write failed after " + maxRetries + " attempts: " + lastException.getMessage());
            } else {
                return ApiResponse.failure(ApiCode.CHUNK_WRITE_SIZE_MISMATCH.getCode(), 
                        "Chunk write size verification failed after " + maxRetries + " attempts. Expected: " + expectedSize + ", actual: " + lastActualSize);
            }
        }
        
        return ApiResponse.success(null);
    }

    private void deleteIncompleteChunkFile(Path chunkFile, String traceId) {
        try {
            Files.deleteIfExists(chunkFile);
            logger.debug("[traceId={}] Deleted incomplete chunk file: {}", traceId, chunkFile);
        } catch (IOException deleteException) {
            logger.warn("[traceId={}] Failed to delete incomplete chunk file: {}", traceId, chunkFile, deleteException);
        }
    }

    private ApiResponse<ChunkUploadResponse> buildChunkUploadResult(UploadSession session, String transferId, String traceId) {
        ChunkUploadResponse result = new ChunkUploadResponse();
        result.setTransferId(transferId);
        result.setCompleted(session.isCompleted());
        result.setMissingChunksCount(session.getTotalChunks() - session.getReceivedChunkCount());

        if (session.isCompleted()) {
            logger.debug("[traceId={}] All chunks received for session: {}", traceId, transferId);
        }

        return ApiResponse.success(result);
    }

    public ApiResponse<ChunkMergeResponse> mergeChunks(String traceId, String transferId) {
        UploadSession session = uploadSessions.get(transferId);
        if (session == null) {
            logger.debug("[traceId={}] Upload session not found: {}", traceId, transferId);
            return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Upload session not found: " + transferId);
        }

        logger.debug("[traceId={}] Processing chunk merge: transferId={}", traceId, transferId);
        session.updateLastAccessTime();

        if (!session.isCompleted()) {
            logger.debug("[traceId={}] Upload not completed for session {}: missing chunks {}", traceId, transferId, session.getMissingChunks());
            return ApiResponse.failure(ApiCode.UPLOAD_NOT_COMPLETED.getCode(), "Upload not completed. Missing chunks: " + session.getMissingChunks());
        }

        if (session.isMerged()) {
            logger.debug("[traceId={}] Chunks already merged for session: {}", traceId, transferId);
            return ApiResponse.failure(ApiCode.CHUNKS_ALREADY_MERGED.getCode(), "Chunks already merged");
        }

        try {
            Path targetDir = Path.of(session.getDestFileDir());
            String destFileName = session.getDestFileName();
            Path tempMergeFile = Path.of(session.getTempDirectory(), destFileName + ".tmp");
            
            logger.debug("[traceId={}] Merging chunks to temp file: {}", traceId, tempMergeFile);

            try (FileChannel outChannel = FileChannel.open(tempMergeFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                for (int i = 0; i < session.getTotalChunks(); i++) {
                    Path chunkFile = Path.of(session.getTempDirectory(), destFileName + "_chunk_" + i);
                    logger.debug("[traceId={}] Reading chunk file: {}", traceId, chunkFile);
                    try (FileChannel chunkChannel = FileChannel.open(chunkFile, StandardOpenOption.READ)) {
                        long size = chunkChannel.size();
                        long transferred = 0;
                        while (transferred < size) {
                            transferred += chunkChannel.transferTo(transferred, size - transferred, outChannel);
                        }
                        logger.debug("[traceId={}] Transferred {} bytes from chunk {}", traceId, transferred, i);
                    }
                }
            }

            Path targetFullFileName = targetDir.resolve(destFileName);
            Files.move(tempMergeFile, targetFullFileName, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            logger.info("[traceId={}] Moved merged file to target: {}", traceId, targetFullFileName);
            long mergedSize = Files.size(targetFullFileName);
            logger.debug("[traceId={}] Merged temp file size: {}, expected: {}", traceId, mergedSize, session.getTotalSize());
            if (mergedSize != session.getTotalSize()) {
                logger.error("[traceId={}] Merged file size mismatch for session {}. Expected: {}, Actual: {}",
                        traceId, transferId, session.getTotalSize(), mergedSize);
                Files.deleteIfExists(tempMergeFile);
                return ApiResponse.failure(ApiCode.MERGE_SIZE_MISMATCH.getCode(), "Merged file size does not match original file size");
            }
            session.setMerged(true);
            logger.info("[traceId={}] Chunks merged for session {}: {}", traceId, transferId, targetFullFileName);

            logger.debug("[traceId={}] Cleaning up temp files for session: {}", traceId, transferId);
            cleanupSessionTempFiles(session);

            ChunkMergeResponse resultData = new ChunkMergeResponse();
            resultData.setDestFileDir(session.getDestFileDir());
            resultData.setDestFileName(session.getDestFileName());
            resultData.setSize(mergedSize);
            
            logger.debug("[traceId={}] Merge completed successfully for session: {}", traceId, transferId);
            return ApiResponse.success(resultData);
        } catch (IOException e) {
            logger.error("[traceId={}] Failed to merge chunks for session {}", traceId, transferId, e);
            return ApiResponse.failure(ApiCode.MERGE_CHUNKS_FAILED.getCode(), "Failed to merge chunks: " + e.getMessage());
        }
    }

    public ApiResponse<ChunkStatusResponse> getUploadStatus(String transferId, String traceId) {
        UploadSession session = uploadSessions.get(transferId);
        if (session == null) {
            return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Upload session not found: " + transferId);
        }
        session.updateLastAccessTime();
        verifySession(session, traceId);
        return ApiResponse.success(session.toChunkStatusResponse());
    }

    private void verifySession(UploadSession session, String traceId) {
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
                    logger.warn("[traceId={}] Corrupted chunk detected: session={}, chunk={}, expected size={}, actual size={}",
                            traceId, session.getTransferId(), chunkIndex, expectedSize, actualSize);
                    Files.deleteIfExists(chunkFile);
                    corruptedChunks.add(chunkIndex);
                }
            } catch (IOException e) {
                logger.warn("[traceId={}] Failed to verify chunk file {}: {}", traceId, chunkFile, e.getMessage(), e);
                corruptedChunks.add(chunkIndex);
            }
        }

        if (!corruptedChunks.isEmpty()) {
            // Remove corrupted chunks from session
            corruptedChunks.forEach(session::removeChunk);
        }
    }

    public ApiResponse<Void> cancelUpload(String transferId, String traceId) {
        UploadSession session = uploadSessions.remove(transferId);
        if (session == null) {
            return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Upload session not found: " + transferId);
        }

        cleanupSessionTempFiles(session);
        logger.info("[traceId={}] Upload session cancelled: {}", traceId, transferId);
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
            return ApiResponse.failure(ApiCode.READ_FILE_FAILED.getCode(), "Failed to read file: " + e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> getDownloadInfo(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "File not found: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.DOWNLOAD_DIRECTORY_FAILED.getCode(), "Cannot download directory: " + path);
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
            return ApiResponse.failure(ApiCode.ACCESS_DENIED.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to get download info: {}", path, e);
            return ApiResponse.failure(ApiCode.GET_DOWNLOAD_INFO_FAILED.getCode(), "Failed to get download info: " + e.getMessage());
        }
    }

    public List<UploadSession> listUploadSessions() {
        return new ArrayList<>(uploadSessions.values());
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

    String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}