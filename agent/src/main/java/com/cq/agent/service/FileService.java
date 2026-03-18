package com.cq.agent.service;

import com.cq.agent.dto.ApiResponse;
import com.cq.agent.dto.ApiCode;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.model.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;

/**
 * File operation service providing FTP-like commands via HTTP.
 * Based on Apache FTPServer command set.
 */
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    private final Path baseDirectory;
    private final boolean allowOutsideBase;
    private final long maxFileSize;

    public FileService(AgentConfig config) {
        String baseDir = config.getFileBaseDirectory();
        this.baseDirectory = Path.of(baseDir).toAbsolutePath().normalize();
        this.allowOutsideBase = config.isAllowOutsideBaseDirectory();
        this.maxFileSize = config.getMaxFileSize();

        // Ensure base directory exists
        try {
            if(!Files.exists(baseDirectory)) {
                Files.createDirectories(baseDirectory);
                logger.info("File service initialized with base directory: {}", baseDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create base directory: {}", baseDirectory, e);
        }
    }

    /**
     * Check if running on Windows.
     */
    public boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Get system type information (SYST command).
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("os.name", System.getProperty("os.name"));
        info.put("os.version", System.getProperty("os.version"));
        info.put("os.arch", System.getProperty("os.arch"));
        info.put("user.name", System.getProperty("user.name"));
        info.put("user.home", System.getProperty("user.home"));
        info.put("java.version", System.getProperty("java.version"));
        info.put("baseDirectory", baseDirectory.toString());
        info.put("type", IS_WINDOWS ? "Windows_NT" : "UNIX");
        return info;
    }

    /**
     * Get supported features (FEAT command).
     */
    public List<String> getFeatures() {
        return List.of(
                "LIST", "NLST", "RETR", "STOR", "STOU", "APPE", "DELE",
                "MKD", "RMD", "CWD", "CDUP", "PWD", "SIZE", "MDTM",
                "RNFR", "RNTO", "STAT", "CHMOD", "MFMT",
                "MD5", "SHA1", "SHA256"
        );
    }

    /**
     * List files in directory (LIST command).
     */
    public ApiResponse<List<FileInfo>> list(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Path does not exist: " + path);
            }
            if (!Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Path is not a directory: " + path);
            }

            List<FileInfo> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetPath)) {
                for (Path entry : stream) {
                    try {
                        files.add(FileInfo.fromPath(entry));
                    } catch (IOException e) {
                        logger.warn("Failed to get info for: {}", entry, e);
                    }
                }
            }

            // Sort: directories first, then by name
            files.sort((a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });

            return ApiResponse.success(files);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to list directory: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to list directory: " + path);
        }
    }

    /**
     * List file names only (NLST command).
     */
    public ApiResponse<List<String>> nameList(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Path does not exist: " + path);
            }
            if (!Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Path is not a directory: " + path);
            }

            List<String> names = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetPath)) {
                for (Path entry : stream) {
                    names.add(entry.getFileName().toString());
                }
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            return ApiResponse.success(names);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to list directory: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to list directory: " + e.getMessage());
        }
    }

    /**
     * Get file content (RETR command).
     */
    public ApiResponse<byte[]> retrieve(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "File does not exist: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Cannot retrieve directory: " + path);
            }
            if (!Files.isReadable(targetPath)) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "File is not readable: " + path);
            }

            long size = Files.size(targetPath);
            if (size > maxFileSize) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "File too large. Max size: " + maxFileSize + " bytes");
            }

            byte[] content = Files.readAllBytes(targetPath);
            return ApiResponse.success(content);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to retrieve file: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to retrieve file: " + e.getMessage());
        }
    }

    /**
     * Get file content as text (RETR command, text mode).
     */
    public ApiResponse<String> retrieveText(String path, String charset) {
        ApiResponse<byte[]> result = retrieve(path);
        if (!result.isSuccess()) {
            return ApiResponse.failure(result.getMsg());
        }
        try {
            String content = new String(result.getData(),
                    charset != null ? charset : StandardCharsets.UTF_8.name());
            return ApiResponse.success(content);
        } catch (UnsupportedEncodingException e) {
            return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Unsupported charset: " + charset);
        }
    }

    /**
     * Store file (STOR command).
     */
    public ApiResponse<FileInfo> store(String path, byte[] content) {
        try {
            Path targetPath = resolvePath(path);

            if (content.length > maxFileSize) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Content too large. Max size: " + maxFileSize + " bytes");
            }

            // Create parent directories if needed
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.write(targetPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("File stored: {}", targetPath);
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to store file: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to store file: " + e.getMessage());
        }
    }

    /**
     * Store file with unique name (STOU command).
     */
    public ApiResponse<FileInfo> storeUnique(String directory, byte[] content, String prefix) {
        try {
            Path dirPath = resolvePath(directory);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            if (!Files.isDirectory(dirPath)) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Not a directory: " + directory);
            }

            if (content.length > maxFileSize) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Content too large. Max size: " + maxFileSize + " bytes");
            }

            String fileName = (prefix != null ? prefix : "file") + "_" +
                    System.currentTimeMillis() + "_" +
                    UUID.randomUUID().toString().substring(0, 8);

            Path targetPath = dirPath.resolve(fileName);
            Files.write(targetPath, content, StandardOpenOption.CREATE_NEW);
            logger.info("File stored with unique name: {}", targetPath);
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + directory);
        } catch (IOException e) {
            logger.error("Failed to store unique file: {}", directory, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to store file: " + e.getMessage());
        }
    }

    /**
     * Append to file (APPE command).
     */
    public ApiResponse<FileInfo> append(String path, byte[] content) {
        try {
            Path targetPath = resolvePath(path);

            // Create parent directories if needed
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (Files.exists(targetPath)) {
                long currentSize = Files.size(targetPath);
                if (currentSize + content.length > maxFileSize) {
                    return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Resulting file would be too large. Max size: " + maxFileSize + " bytes");
                }
            }

            Files.write(targetPath, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.info("Content appended to file: {}", targetPath);
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to append to file: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to append to file: " + e.getMessage());
        }
    }

    /**
     * Delete file (DELE command).
     */
    public ApiResponse<Void> delete(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "File does not exist: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Cannot delete directory with DELE, use RMD: " + path);
            }

            Files.delete(targetPath);
            logger.info("File deleted: {}", targetPath);
            return ApiResponse.success(null);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to delete file: " + e.getMessage());
        }
    }

    /**
     * Make directory (MKD command).
     */
    public ApiResponse<FileInfo> makeDirectory(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.PATH_ALREADY_EXISTS.getCode(), "Path already exists: " + path);
            }

            Files.createDirectories(targetPath);
            logger.info("Directory created: {}", targetPath);
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.ACCESS_DENIED.getCode(),"Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to create directory: " + path);
        }
    }

    /**
     * Remove directory (RMD command).
     */
    public ApiResponse<Void> removeDirectory(String path, boolean recursive) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Directory does not exist: " + path);
            }
            if (!Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Not a directory: " + path);
            }

            if (recursive) {
                deleteRecursively(targetPath);
            } else {
                // Check if directory is empty
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetPath)) {
                    if (stream.iterator().hasNext()) {
                        return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Directory is not empty: " + path);
                    }
                }
                Files.delete(targetPath);
            }

            logger.info("Directory removed: {}", targetPath);
            return ApiResponse.success(null);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to remove directory: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to remove directory: " + e.getMessage());
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Get current/working directory info (PWD command).
     */
    public ApiResponse<FileInfo> printWorkingDirectory(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Path does not exist: " + path);
            }
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.ACCESS_DENIED.getCode(),"Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to get directory info: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to get directory info: " + e.getMessage());
        }
    }

    /**
     * Get file size (SIZE command).
     */
    public ApiResponse<Long> getSize(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "File does not exist: " + path);
            }
            return ApiResponse.success(Files.size(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to get file size: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to get file size: " + e.getMessage());
        }
    }

    /**
     * Get file modification time (MDTM command).
     */
    public ApiResponse<Long> getModificationTime(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"File does not exist: " + path);
            }
            return ApiResponse.success(Files.getLastModifiedTime(targetPath).toMillis());
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to get modification time: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to get modification time: " + e.getMessage());
        }
    }

    /**
     * Set file modification time (MFMT command).
     */
    public ApiResponse<FileInfo> setModificationTime(String path, long timestamp) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"File does not exist: " + path);
            }
            Files.setLastModifiedTime(targetPath, FileTime.fromMillis(timestamp));
            logger.info("Modification time set for: {}", targetPath);
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to set modification time: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Failed to set modification time: " + e.getMessage());
        }
    }

    /**
     * Rename file (RNFR + RNTO commands).
     */
    public ApiResponse<FileInfo> rename(String fromPath, String toPath) {
        try {
            Path sourcePath = resolvePath(fromPath);
            Path targetPath = resolvePath(toPath);

            if (!Files.exists(sourcePath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Source does not exist: " + fromPath);
            }
            if (Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Target already exists: " + toPath);
            }

            // Create parent directories if needed
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.move(sourcePath, targetPath);
            logger.info("Renamed: {} -> {}", sourcePath, targetPath);
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied");
        } catch (IOException e) {
            logger.error("Failed to rename: {} -> {}", fromPath, toPath, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to rename: " + e.getMessage());
        }
    }

    /**
     * Copy file.
     */
    public ApiResponse<FileInfo> copy(String fromPath, String toPath) {
        try {
            Path sourcePath = resolvePath(fromPath);
            Path targetPath = resolvePath(toPath);

            if (!Files.exists(sourcePath)) {
                return ApiResponse.failure(ApiCode.NOT_FOUND.getCode(), "Source does not exist: " + fromPath);
            }
            if (Files.isDirectory(sourcePath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Cannot copy directory: " + fromPath);
            }
            if (Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.PATH_ALREADY_EXISTS.getCode(),"Target already exists: " + toPath);
            }

            // Create parent directories if needed
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.copy(sourcePath, targetPath);
            logger.info("Copied: {} -> {}", sourcePath, targetPath);
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Access denied");
        } catch (IOException e) {
            logger.error("Failed to copy: {} -> {}", fromPath, toPath, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to copy from '" + fromPath + "' to '" + toPath + "': " + e.getMessage());
        }
    }

    /**
     * Get file/directory status (STAT command).
     */
    public ApiResponse<FileInfo> stat(String path) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Path does not exist: " + path);
            }
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to get status: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to get status: " + e.getMessage());
        }
    }

    /**
     * Check if path exists.
     */
    public ApiResponse<Boolean> exists(String path) {
        try {
            Path targetPath = resolvePath(path);
            return ApiResponse.success(Files.exists(targetPath));
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        }
    }

    /**
     * Change file permissions (CHMOD command - Unix only).
     */
    public ApiResponse<FileInfo> chmod(String path, String permissions) {
        if (IS_WINDOWS) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"CHMOD is not supported on Windows");
        }

        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Path does not exist: " + path);
            }

            Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permissions);
            Files.setPosixFilePermissions(targetPath, perms);
            logger.info("Permissions changed for: {}", targetPath);
            return ApiResponse.success(FileInfo.fromPath(targetPath));
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Invalid permission string: " + permissions);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to change permissions: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Failed to change permissions: " + e.getMessage());
        }
    }

    /**
     * Calculate file checksum (MD5/SHA1/SHA256).
     */
    public ApiResponse<String> checksum(String path, String algorithm) {
        try {
            Path targetPath = resolvePath(path);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"File does not exist: " + path);
            }
            if (Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.INVALID_REQUEST.getCode(), "Cannot calculate checksum for directory: " + path);
            }

            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream is = Files.newInputStream(targetPath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return ApiResponse.success(hexString.toString());
        } catch (NoSuchAlgorithmException e) {
            return ApiResponse.failure(ApiCode.UNSUPPORTED_ALGORITHM.getCode(),"Unsupported algorithm: " + algorithm);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to calculate checksum: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to calculate checksum: " + e.getMessage());
        }
    }

    /**
     * Search for files matching pattern.
     */
    public ApiResponse<List<FileInfo>> search(String basePath, String pattern, int maxDepth) {
        try {
            Path targetPath = resolvePath(basePath);
            if (!Files.exists(targetPath)) {
                return ApiResponse.failure(ApiCode.PATH_NOT_FOUND.getCode(), "Path does not exist: " + basePath);
            }
            if (!Files.isDirectory(targetPath)) {
                return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Path is not a directory: " + basePath);
            }

            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<FileInfo> results = new ArrayList<>();

            try (Stream<Path> stream = Files.walk(targetPath, maxDepth > 0 ? maxDepth : Integer.MAX_VALUE)) {
                stream.filter(p -> matcher.matches(p.getFileName()))
                        .forEach(p -> {
                            try {
                                results.add(FileInfo.fromPath(p));
                            } catch (IOException e) {
                                logger.warn("Failed to get info for: {}", p, e);
                            }
                        });
            }

            return ApiResponse.success(results);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.ACCESS_DENIED.getCode(),"Access denied: " + basePath);
        } catch (IOException e) {
            logger.error("Failed to search: {}", basePath, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(),"Failed to search in " + basePath + ": " + e.getMessage());
        }
    }

    /**
     * Get disk space information.
     */
    public ApiResponse<Map<String, Long>> getDiskSpace(String path) {
        try {
            Path targetPath = resolvePath(path);
            FileStore store = Files.getFileStore(targetPath);

            Map<String, Long> space = new LinkedHashMap<>();
            space.put("total", store.getTotalSpace());
            space.put("usable", store.getUsableSpace());
            space.put("free", store.getUnallocatedSpace());
            return ApiResponse.success(space);
        } catch (SecurityException e) {
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Access denied: " + path);
        } catch (IOException e) {
            logger.error("Failed to get disk space: {}", path, e);
            return ApiResponse.failure(ApiCode.INTERNAL_SERVER_ERROR.getCode(), "Failed to get disk space: " + e.getMessage());
        }
    }

    /**
     * Resolve path relative to base directory.
     */
    private Path resolvePath(String path) {
        if (path == null || path.isEmpty() || path.equals(".")) {
            return baseDirectory;
        }

        Path resolved;
        if (path.startsWith("/") || (IS_WINDOWS && path.length() > 1 && path.charAt(1) == ':')) {
            // Absolute path
            resolved = Path.of(path).toAbsolutePath().normalize();
        } else {
            // Relative path
            resolved = baseDirectory.resolve(path).normalize();
        }

        // Security check: ensure path doesn't escape base directory (if configured)
        if (!allowOutsideBase) {
            if (!resolved.startsWith(baseDirectory)) {
                throw new SecurityException("Access denied: path outside base directory");
            }
        }

        return resolved;
    }
}
