package com.cq.agent.client.upload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class Util {
    static String md5(String input) {
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
    static String checkLocalFileReadable(String localFilePath) {
        if (localFilePath == null || localFilePath.isBlank()) return "本地路径为空";
        java.io.File f = new java.io.File(localFilePath);
        if (!f.exists()) return "本地文件不存在: " + localFilePath;
        if (!f.isFile()) return "本地路径不是文件: " + localFilePath;
        if (!f.canRead()) return "本地文件无读取权限: " + localFilePath;
        return null;
    }

    static String transferToLinuxPath(String path) {
        return path.replace("\\", "/");
    }

}
