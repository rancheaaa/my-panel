package com.cq.agent.client.upload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Util {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks local file readability and returns error message if not readable.
     */
    public static String checkLocalFileReadable(String localFilePath) {
        if (localFilePath == null || localFilePath.isBlank()) return "本地路径为空";
        java.io.File f = new java.io.File(localFilePath);
        if (!f.exists()) return "本地文件不存在: " + localFilePath;
        if (!f.isFile()) return "本地路径不是文件: " + localFilePath;
        if (!f.canRead()) return "本地文件无读取权限: " + localFilePath;
        return null;
    }

    public static String transferToLinuxPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.replace("\\", "/");
    }

    public static String currentTime() {
        LocalDateTime now = LocalDateTime.now();
        return DATE_TIME_FORMATTER.format(now);
    }

    public static String resolveAndCreatePathIfAbsent(String basePath, String relativePath) throws IOException {
        final Path base = Path.of(basePath).toAbsolutePath().normalize();
        final Path relative = base.resolve(relativePath);
        if (!Files.exists(relative)) {
            Files.createDirectories(relative);
        }
        return relative.toString();
    }

}
