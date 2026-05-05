package com.cq.agent.batch.scanner;

import lombok.Data;
import java.util.List;

@Data
public class ScanRequest
{
    private String requestId;
    private Long taskId;
    private String baseDir;
    private List<String> includePatterns;
    private List<String> excludePatterns;
    private int maxFiles = 10000;
    private boolean computeMd5 = true;
}
