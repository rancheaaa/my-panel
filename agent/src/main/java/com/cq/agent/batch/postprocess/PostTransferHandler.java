package com.cq.agent.batch.postprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class PostTransferHandler
{
    private static final Logger logger = LoggerFactory.getLogger(PostTransferHandler.class);

    public PostProcessResult execute(Long taskId, List<String> successFiles, PostTransferConfig config)
    {
        long start = System.currentTimeMillis();
        if (config == null || config.getAction() == null)
        {
            return PostProcessResult.empty();
        }
        List<String> files = successFiles == null ? List.of() : successFiles;
        Path sourceBaseDir = resolveAndValidateBaseDir(config.getSourceBaseDir());
        PostProcessResult result = switch (config.getAction().toUpperCase())
        {
            case "DELETE" -> deleteFiles(files, sourceBaseDir);
            case "BACKUP" -> backupFiles(files, sourceBaseDir, config);
            default -> PostProcessResult.empty();
        };
        result.setDurationMs(System.currentTimeMillis() - start);
        logger.info("Post process finished, taskId={}, action={}, success={}, failed={}",
                taskId, config.getAction(), result.getSuccessCount(), result.getFailedCount());
        return result;
    }

    private PostProcessResult deleteFiles(List<String> filePaths, Path sourceBaseDir)
    {
        PostProcessResult result = new PostProcessResult();
        result.setTotalFiles(filePaths.size());
        for (String relativePath : filePaths)
        {
            try
            {
                Path file = resolveTaskFile(sourceBaseDir, relativePath);
                if (Files.exists(file))
                {
                    Files.deleteIfExists(file);
                    result.getSuccessFiles().add(relativePath);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                }
                else
                {
                    result.getSuccessFiles().add(relativePath);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                }
            }
            catch (IOException e)
            {
                logger.warn("Failed to delete file {}: {}", relativePath, e.getMessage());
                result.getFailedFiles().add(relativePath);
                result.getErrorMessages().add(relativePath + ": " + e.getMessage());
                result.setFailedCount(result.getFailedCount() + 1);
            }
        }
        return result;
    }

    private PostProcessResult backupFiles(List<String> filePaths, Path sourceBaseDir, PostTransferConfig config)
    {
        PostProcessResult result = new PostProcessResult();
        result.setTotalFiles(filePaths.size());
        Path backupDir;
        try
        {
            backupDir = validateBackupDir(config.getBackupDir());
        }
        catch (IOException | IllegalArgumentException e)
        {
            result.getErrorMessages().add(e.getMessage());
            result.setFailedCount(filePaths.size());
            result.getFailedFiles().addAll(filePaths);
            return result;
        }
        for (String relativePath : filePaths)
        {
            try
            {
                Path sourceFile = resolveTaskFile(sourceBaseDir, relativePath);
                if (!Files.exists(sourceFile))
                {
                    result.getFailedFiles().add(relativePath);
                    result.getErrorMessages().add(relativePath + ": source file not found");
                    result.setFailedCount(result.getFailedCount() + 1);
                    continue;
                }
                Path targetFile;
                if (config.isPreserveDirStructure())
                {
                    targetFile = backupDir.resolve(relativePath);
                }
                else
                {
                    targetFile = backupDir.resolve(sourceFile.getFileName().toString());
                }
                Files.createDirectories(targetFile.getParent());
                if ("MOVE".equalsIgnoreCase(config.getBackupMode()))
                {
                    Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
                else
                {
                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
                result.getSuccessFiles().add(relativePath);
                result.setSuccessCount(result.getSuccessCount() + 1);
            }
            catch (IOException e)
            {
                logger.warn("Failed to backup file {}: {}", relativePath, e.getMessage());
                result.getFailedFiles().add(relativePath);
                result.getErrorMessages().add(relativePath + ": " + e.getMessage());
                result.setFailedCount(result.getFailedCount() + 1);
            }
        }
        return result;
    }

    private Path resolveAndValidateBaseDir(String sourceBaseDir)
    {
        if (sourceBaseDir == null || sourceBaseDir.isBlank())
        {
            throw new IllegalArgumentException("sourceBaseDir不能为空");
        }
        Path baseDir = Paths.get(sourceBaseDir).toAbsolutePath().normalize();
        if (!baseDir.isAbsolute())
        {
            throw new IllegalArgumentException("sourceBaseDir必须是绝对路径");
        }
        return baseDir;
    }

    private Path resolveTaskFile(Path sourceBaseDir, String relativePath)
    {
        if (relativePath == null || relativePath.isBlank())
        {
            throw new IllegalArgumentException("文件相对路径不能为空");
        }
        Path resolved = sourceBaseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(sourceBaseDir))
        {
            throw new SecurityException("后处理路径超出任务源目录范围: " + relativePath);
        }
        return resolved;
    }

    private Path validateBackupDir(String backupDirPath) throws IOException
    {
        if (backupDirPath == null || backupDirPath.isBlank())
        {
            throw new IllegalArgumentException("备份目录不能为空");
        }
        Path backupDir = Paths.get(backupDirPath).toAbsolutePath().normalize();
        if (!Files.exists(backupDir))
        {
            throw new IllegalArgumentException("备份目录不存在: " + backupDir);
        }
        if (!Files.isDirectory(backupDir))
        {
            throw new IllegalArgumentException("备份路径不是目录: " + backupDir);
        }
        if (!Files.isWritable(backupDir))
        {
            throw new IllegalArgumentException("备份目录不可写: " + backupDir);
        }
        return backupDir;
    }
}
