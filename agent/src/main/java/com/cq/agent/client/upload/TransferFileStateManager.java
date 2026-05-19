package com.cq.agent.client.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 传输文件状态管理器
 * 通过将原始文件重命名为隐藏文件（.{fileName}.transferring）来标记文件正在传输中。
 * FileScanner 已内置跳过隐藏文件（以.开头的文件）的逻辑，因此隐藏后的文件不会被重复扫描。
 *
 * 命名规则：
 * - 原始文件: /data/files/report.csv
 * - 传输中文件: /data/files/.report.csv.transferring
 */
public class TransferFileStateManager {

    private static final Logger log = LoggerFactory.getLogger(TransferFileStateManager.class);

    /** 传输中文件后缀 */
    public static final String TRANSFERRING_SUFFIX = ".transferring";

    private TransferFileStateManager() {
        // 工具类不允许实例化
    }

    /**
     * 将原始文件隐藏（重命名为 .{fileName}.transferring），标记为传输中
     *
     * @param originalPath 原始文件路径
     * @return 隐藏后的文件路径
     * @throws IOException 如果文件不存在或重命名失败
     */
    public static Path hideFile(Path originalPath) throws IOException {
        if (originalPath == null) {
            throw new IllegalArgumentException("originalPath不能为null");
        }
        if (!Files.exists(originalPath)) {
            throw new IOException("文件不存在，无法隐藏: " + originalPath);
        }

        Path transferringPath = getTransferringPath(originalPath);

        // 如果隐藏文件已存在，说明文件已在传输中
        if (Files.exists(transferringPath)) {
            log.warn("隐藏文件已存在，文件可能已在传输中: {}", transferringPath);
            throw new IOException("隐藏文件已存在，文件可能已在传输中: " + transferringPath);
        }

        try {
            Files.move(originalPath, transferringPath, StandardCopyOption.ATOMIC_MOVE);
            log.info("文件已标记为传输中: {} -> {}", originalPath.getFileName(), transferringPath.getFileName());
            return transferringPath;
        } catch (IOException e) {
            log.error("隐藏文件失败: {} -> {}", originalPath, transferringPath, e);
            throw new IOException("隐藏文件失败: " + originalPath + " -> " + transferringPath, e);
        }
    }

    /**
     * 将隐藏文件恢复为原始文件（取消传输中标记）
     *
     * @param transferringPath 隐藏文件路径
     * @return 恢复后的原始文件路径
     * @throws IOException 如果文件不存在或重命名失败
     */
    public static Path unhideFile(Path transferringPath) throws IOException {
        if (transferringPath == null) {
            throw new IllegalArgumentException("transferringPath不能为null");
        }
        if (!Files.exists(transferringPath)) {
            throw new IOException("传输中文件不存在，无法恢复: " + transferringPath);
        }

        Path originalPath = getOriginalPath(transferringPath);

        // 如果原始文件已存在，说明可能存在冲突
        if (Files.exists(originalPath)) {
            log.warn("原始文件已存在，将覆盖: {}", originalPath);
        }

        try {
            Files.move(transferringPath, originalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("文件已恢复为原始状态: {} -> {}", transferringPath.getFileName(), originalPath.getFileName());
            return originalPath;
        } catch (IOException e) {
            log.error("恢复文件失败: {} -> {}", transferringPath, originalPath, e);
            throw new IOException("恢复文件失败: " + transferringPath + " -> " + originalPath, e);
        }
    }

    /**
     * 判断原始文件是否处于传输中状态（检查对应的隐藏文件是否存在）
     *
     * @param originalPath 原始文件路径
     * @return true 如果文件正在传输中
     */
    public static boolean isTransferring(Path originalPath) {
        if (originalPath == null) {
            return false;
        }
        Path transferringPath = getTransferringPath(originalPath);
        return Files.exists(transferringPath);
    }

    /**
     * 判断路径是否是传输中文件（以.{name}.transferring命名）
     *
     * @param path 文件路径
     * @return true 如果是传输中文件
     */
    public static boolean isTransferringFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString();
        return fileName.startsWith(".") && fileName.endsWith(TRANSFERRING_SUFFIX);
    }

    /**
     * 根据原始文件路径计算传输中文件路径
     * 规则：在文件名前加"."，后加".transferring"
     * 例如: report.csv -> .report.csv.transferring
     *
     * @param originalPath 原始文件路径
     * @return 传输中文件路径
     */
    public static Path getTransferringPath(Path originalPath) {
        if (originalPath == null) {
            return null;
        }
        Path parent = originalPath.getParent();
        String fileName = originalPath.getFileName().toString();
        String transferringFileName = "." + fileName + TRANSFERRING_SUFFIX;
        return parent != null ? parent.resolve(transferringFileName) : Path.of(transferringFileName);
    }

    /**
     * 根据传输中文件路径推算原始文件路径
     * 例如: .report.csv.transferring -> report.csv
     *
     * @param transferringPath 传输中文件路径
     * @return 原始文件路径
     */
    public static Path getOriginalPath(Path transferringPath) {
        if (transferringPath == null) {
            return null;
        }
        String fileName = transferringPath.getFileName().toString();
        if (fileName.startsWith(".") && fileName.endsWith(TRANSFERRING_SUFFIX)) {
            String originalName = fileName.substring(1, fileName.length() - TRANSFERRING_SUFFIX.length());
            Path parent = transferringPath.getParent();
            return parent != null ? parent.resolve(originalName) : Path.of(originalName);
        }
        // 如果不是传输中文件格式，返回原路径
        return transferringPath;
    }

    /**
     * 安全地隐藏文件，如果文件已经在传输中则返回已有的隐藏路径而不抛异常
     *
     * @param originalPath 原始文件路径
     * @return 隐藏后的文件路径，如果已在传输中则返回已有的隐藏路径
     * @throws IOException 如果文件不存在
     */
    public static Path hideFileSafely(Path originalPath) throws IOException {
        if (isTransferring(originalPath)) {
            log.debug("文件已在传输中，返回已有的隐藏路径: {}", originalPath);
            return getTransferringPath(originalPath);
        }
        return hideFile(originalPath);
    }

    /**
     * 安全地恢复文件，如果文件不存在或不是传输中文件则不操作
     *
     * @param transferringPath 传输中文件路径
     * @return 恢复后的原始文件路径，如果未操作则返回null
     */
    public static Path unhideFileSafely(Path transferringPath) {
        if (transferringPath == null || !Files.exists(transferringPath)) {
            return null;
        }
        if (!isTransferringFile(transferringPath)) {
            return null;
        }
        try {
            return unhideFile(transferringPath);
        } catch (IOException e) {
            log.warn("安全恢复文件失败: {}", transferringPath, e);
            return null;
        }
    }
}
