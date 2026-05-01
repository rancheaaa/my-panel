package com.cq.agent.batch.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchFileScannerTest
{
    private final BatchFileScanner scanner = new BatchFileScanner();

    @TempDir
    Path tempDir;

    @Test
    void scanShouldApplyIncludeExcludeAndSortByLastModified() throws Exception
    {
        Path logsDir = Files.createDirectories(tempDir.resolve("logs"));
        Path rootTxt = Files.writeString(tempDir.resolve("app.txt"), "root-file");
        Path logFile = Files.writeString(logsDir.resolve("server.log"), "log-file");
        Files.writeString(logsDir.resolve("skip.tmp"), "tmp-file");

        Files.setLastModifiedTime(logFile, FileTime.from(Instant.parse("2026-04-30T09:00:00Z")));
        Files.setLastModifiedTime(rootTxt, FileTime.from(Instant.parse("2026-04-30T10:00:00Z")));

        ScanRequest request = new ScanRequest();
        request.setRequestId("req-1");
        request.setBaseDir(tempDir.toAbsolutePath().toString());
        request.setIncludePatterns(List.of("**/*.log", "*.txt"));
        request.setExcludePatterns(List.of("**/*.tmp"));
        request.setComputeMd5(true);
        request.setMaxFiles(10);

        ScanResponse response = scanner.scan(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getResult());
        assertEquals(2, response.getResult().getFiles().size());
        assertEquals("logs/server.log", response.getResult().getFiles().get(0).getRelativePath());
        assertEquals("app.txt", response.getResult().getFiles().get(1).getRelativePath());
        assertNotNull(response.getResult().getFiles().get(0).getMd5());
        assertFalse(response.getResult().isTruncated());
    }

    @Test
    void scanShouldStopWhenReachingMaxFiles() throws Exception
    {
        Files.writeString(tempDir.resolve("a.log"), "a");
        Files.writeString(tempDir.resolve("b.log"), "b");
        Files.writeString(tempDir.resolve("c.log"), "c");

        ScanRequest request = new ScanRequest();
        request.setRequestId("req-2");
        request.setBaseDir(tempDir.toAbsolutePath().toString());
        request.setIncludePatterns(List.of("*.log"));
        request.setMaxFiles(2);

        ScanResponse response = scanner.scan(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getResult());
        assertEquals(2, response.getResult().getFiles().size());
        assertTrue(response.getResult().isTruncated());
    }
}
