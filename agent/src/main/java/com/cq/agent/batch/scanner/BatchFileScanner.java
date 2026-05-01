package com.cq.agent.batch.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class BatchFileScanner
{
    private static final Logger logger = LoggerFactory.getLogger(BatchFileScanner.class);
    private static final Set<String> SKIP_DIRS = Set.of(".git", ".svn", ".hg", "node_modules", "__pycache__", ".idea", ".vscode");
    private static final long MD5_FILE_SIZE_LIMIT = 100 * 1024 * 1024;

    public ScanResponse scan(ScanRequest request)
    {
        long start = System.currentTimeMillis();
        try
        {
            Path baseDir = validateRequest(request);
            if (!Files.isDirectory(baseDir))
            {
                return ScanResponse.error(request.getRequestId(), "源目录不存在或不是目录: " + baseDir);
            }
            if (!Files.isReadable(baseDir))
            {
                return ScanResponse.error(request.getRequestId(), "源目录不可读: " + baseDir);
            }
            GlobMatcher matcher = new GlobMatcher(baseDir.toString(), request.getIncludePatterns(), request.getExcludePatterns());
            List<ScannedFile> files = new ArrayList<>();
            int[] skippedDirs = {0};
            boolean[] truncated = {false};
            Files.walkFileTree(baseDir, new SimpleFileVisitor<>()
            {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
                {
                    if (baseDir.equals(dir))
                    {
                        return FileVisitResult.CONTINUE;
                    }
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (SKIP_DIRS.contains(dirName) || dirName.startsWith(".") || Files.isHidden(dir))
                    {
                        skippedDirs[0]++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                {
                    if (truncated[0])
                    {
                        return FileVisitResult.TERMINATE;
                    }
                    try
                    {
                        if (!attrs.isRegularFile() || attrs.isSymbolicLink())
                        {
                            return FileVisitResult.CONTINUE;
                        }
                        String relativePath = baseDir.relativize(file).toString().replace('\\', '/');
                        if (!matcher.matches(relativePath))
                        {
                            return FileVisitResult.CONTINUE;
                        }
                        ScannedFile sf = new ScannedFile();
                        sf.setRelativePath(relativePath);
                        sf.setAbsolutePath(file.toAbsolutePath().toString());
                        sf.setSizeBytes(attrs.size());
                        sf.setLastModified(Date.from(attrs.lastModifiedTime().toInstant()));
                        if (request.isComputeMd5() && attrs.size() <= MD5_FILE_SIZE_LIMIT)
                        {
                            sf.setMd5(computeMd5(file));
                        }
                        files.add(sf);
                        if (files.size() >= request.getMaxFiles())
                        {
                            truncated[0] = true;
                            return FileVisitResult.TERMINATE;
                        }
                    }
                    catch (Exception e)
                    {
                        logger.warn("Error visiting file {}: {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc)
                {
                    logger.warn("Failed to visit file {}: {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
            files.sort(Comparator.comparing(ScannedFile::getLastModified));
            ScanResult result = ScanResult.of(files, truncated[0], skippedDirs[0]);
            return ScanResponse.success(request.getRequestId(), result, System.currentTimeMillis() - start);
        }
        catch (Exception e)
        {
            logger.error("Scan error for request {}: {}", request.getRequestId(), e.getMessage(), e);
            return ScanResponse.error(request.getRequestId(), e.getMessage());
        }
    }

    private Path validateRequest(ScanRequest request)
    {
        if (request == null)
        {
            throw new IllegalArgumentException("扫描请求不能为空");
        }
        if (request.getBaseDir() == null || request.getBaseDir().isBlank())
        {
            throw new IllegalArgumentException("源目录不能为空");
        }
        if (request.getBaseDir().contains(".."))
        {
            throw new IllegalArgumentException("源目录路径不允许包含..");
        }
        Path p = Paths.get(request.getBaseDir());
        if (!p.isAbsolute())
        {
            throw new IllegalArgumentException("源目录必须是绝对路径");
        }
        if (request.getMaxFiles() <= 0)
        {
            throw new IllegalArgumentException("maxFiles必须大于0");
        }
        return p.toAbsolutePath().normalize();
    }

    private String computeMd5(Path file) throws Exception
    {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream inputStream = Files.newInputStream(file))
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1)
            {
                md.update(buffer, 0, read);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest)
        {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
