package com.cq.agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicFileWriter {

    private static final Logger logger = LoggerFactory.getLogger(AtomicFileWriter.class);

    private AtomicFileWriter() {
    }

    public static void writeAtomically(Path targetPath, String content) throws IOException {
        Path tmpPath = targetPath.resolveSibling(
                targetPath.getFileName().toString() + ".tmp_" + System.nanoTime()
        );

        try {
            Files.writeString(tmpPath, content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            forceSync(tmpPath);

            Files.move(tmpPath, targetPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            logger.debug("原子写入完成: {}", targetPath);

        } catch (IOException e) {
            cleanupQuietly(tmpPath);
            throw new IOException("原子写入失败: " + targetPath, e);
        }
    }

    public static void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
            logger.debug("文件已删除: {}", path);
        }
    }

    private static void forceSync(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void cleanupQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            logger.warn("清理临时文件失败: {}", path);
        }
    }
}
