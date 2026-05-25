package com.cq.agent.batch.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BatchFileGenerator {

    private final SecureRandom random = new SecureRandom();

    private Path targetDir;
    private long minFileSizeBytes;
    private long maxFileSizeBytes;
    private String namePattern;
    private int fileCount;

    public BatchFileGenerator() {
    }

    public BatchFileGenerator targetDir(Path targetDir) {
        this.targetDir = targetDir;
        return this;
    }

    public BatchFileGenerator fileSizeRange(long minBytes, long maxBytes) {
        this.minFileSizeBytes = minBytes;
        this.maxFileSizeBytes = maxBytes;
        return this;
    }

    public BatchFileGenerator namePattern(String pattern) {
        this.namePattern = pattern;
        return this;
    }

    public BatchFileGenerator fileCount(int count) {
        this.fileCount = count;
        return this;
    }

    public List<Path> generate() throws IOException {
        if (targetDir == null) {
            throw new IllegalStateException("targetDir未设置");
        }
        if (!targetDir.toFile().exists()) {
            Files.createDirectories(targetDir);
        }

        Path stagingDir = Files.createTempDirectory(Paths.get("/"), ".batch-gen-staging-");
        List<Path> generatedFiles = new ArrayList<>(fileCount);

        try {
            for (int i = 0; i < fileCount; i++) {
                String fileName = generateFileName();
                long fileSize = random.nextLong(minFileSizeBytes, maxFileSizeBytes + 1);
                Path stagedFile = stagingDir.resolve(fileName);
                generateFileWithContent(stagedFile, fileSize);

                Path targetPath = targetDir.resolve(fileName);
                Files.move(stagedFile, targetPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                generatedFiles.add(targetPath);
            }
        } finally {
            deleteRecursively(stagingDir);
        }

        return generatedFiles;
    }

    private void generateFileWithContent(Path path, long size) throws IOException {
        byte[] content = new byte[(int) size];
        random.nextBytes(content);
        Files.write(path, content);
    }

    private String generateFileName() {
        if (namePattern != null && !namePattern.isEmpty()) {
            return expandWildcard(namePattern);
        }
        return "file-" + randomAlphanumeric(8) + ".dat";
    }

    private String expandWildcard(String pattern) {
        StringBuilder sb = new StringBuilder();
        int len = pattern.length();
        for (int i = 0; i < len; i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                sb.append(randomAlphanumeric(ThreadLocalRandom.current().nextInt(3, 10)));
            } else if (c == '?') {
                sb.append(randomAlphanumeric(1));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String randomAlphanumeric(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir).sorted((a, b) -> b.compareTo(a))) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new  BatchFileGenerator()
                .targetDir(Paths.get("/tmp/auto-generated"))
                .fileSizeRange(2048, 1024 * 1024)
                .namePattern("b-file-*.txt")
                .fileCount(100)
                .generate();
    }
}
