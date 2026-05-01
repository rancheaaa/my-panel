package com.cq.agent.batch.scanner;

import lombok.Data;
import java.util.List;

@Data
public class ScanResult
{
    private List<ScannedFile> files;
    private int totalFiles;
    private long totalSizeBytes;
    private boolean truncated;
    private int skippedDirectories;

    public static ScanResult of(List<ScannedFile> files, boolean truncated, int skippedDirs)
    {
        ScanResult r = new ScanResult();
        r.setFiles(files);
        r.setTotalFiles(files.size());
        r.setTotalSizeBytes(files.stream().mapToLong(ScannedFile::getSizeBytes).sum());
        r.setTruncated(truncated);
        r.setSkippedDirectories(skippedDirs);
        return r;
    }
}
