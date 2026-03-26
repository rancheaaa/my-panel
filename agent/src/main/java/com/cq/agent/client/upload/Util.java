package com.cq.agent.client.upload;

import com.cq.agent.client.RemoteAgentInfo;

import java.io.IOException;
import java.net.URL;
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

    @SuppressWarnings("all")
    public static RemoteAgentInfo resolveRemoteAgentInfo(String str) {
        if (str == null || str.isBlank()) return null;
        // str like 192.168.1.100:7777@root:/tmp/upload
        // or like http://192.168.1.100:7777@root:/tmp/upload
        // ip:port@username:destFilePath
        String[] parts = str.split("@");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid remote agent info: " + str + ", should be ip:port@username:destFilePath or http://ip:port@username:destFilePath");
        }
        String ipPort = parts[0];
        if (ipPort == null || ipPort.isBlank()) {
            throw new IllegalArgumentException("ip:port can't be empty: " + str + ", should be ip:port@username:destFilePath");
        };
        String usernameDest = parts[1];
        if (usernameDest == null || usernameDest.isBlank()) {
            throw new IllegalArgumentException("username:destFilePath can't be empty: " + str + ", should be ip:port@username:destFilePath");
        }
        
        String ip;
        int portInt;
        
        // Check if ipPort starts with http:// or https://
        String normalizedIpPort = ipPort.toLowerCase();
        if (normalizedIpPort.startsWith("http://") || normalizedIpPort.startsWith("https://")) {
            // Use URL parsing for http/https format
            try {
                final URL url = new URL(ipPort);
                ip = url.getHost();
                portInt = url.getPort();
                if (portInt == -1) {
                    throw new IllegalArgumentException("Port not specified in URL: " + ipPort);
                }
                if (portInt < 0 || portInt > 65535) {
                    throw new IllegalArgumentException("Port must be between 0 and 65535: " + ipPort);
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid URL format: " + ipPort + ", should be http://ip:port@username:destFilePath", e);
            }
        } else {
            // Parse ip:port format without http
            String[] ipPortParts = ipPort.split(":");
            if (ipPortParts.length != 2) {
                throw new IllegalArgumentException("Invalid remote agent info format: " + str + ", should be ip:port@username:destFilePath");
            }
            
            ip = ipPortParts[0];
            if (ip == null || ip.isBlank()) {
                throw new IllegalArgumentException("ip can't be empty: " + str + ", should be ip:port@username:destFilePath");
            }
            String portStr = ipPortParts[1];
            if (portStr == null || portStr.isBlank()) {
                throw new IllegalArgumentException("port can't be empty: " + str + ", should be ip:port@username:destFilePath");
            }
            try {
                portInt = Integer.parseInt(portStr);
                if (portInt < 0 || portInt > 65535) {
                    throw new IllegalArgumentException("port must be between 0 and 65535: " + str + ", should be ip:port@username:destFilePath");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("port must be a number: " + str + ", should be ip:port@username:destFilePath");
            }
        }
        
        String username = usernameDest.split(":")[0];
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username can't be empty: " + str + ", should be ip:port@username:destFilePath");
        }
        // Handle Windows paths like C:/Users/... by splitting only on the first colon
        int firstColonIndex = usernameDest.indexOf(':');
        if (firstColonIndex == -1) {
            throw new IllegalArgumentException("username:destFilePath format error: " + str + ", should be username:destFilePath");
        }
        String destFilePath = usernameDest.substring(firstColonIndex + 1);
        if (destFilePath == null || destFilePath.isBlank()) {
            throw new IllegalArgumentException("destFilePath can't be empty: " + str + ", should be ip:port@username:destFilePath");
        }
        return new RemoteAgentInfo(ip, portInt, username, destFilePath);
    }

}