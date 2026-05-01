package com.cq.agent.batch.postprocess;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class PostProcessResult
{
    private int totalFiles;
    private int successCount;
    private int failedCount;
    private long durationMs;
    private List<String> successFiles = new ArrayList<>();
    private List<String> failedFiles = new ArrayList<>();
    private List<String> errorMessages = new ArrayList<>();

    public static PostProcessResult empty()
    {
        return new PostProcessResult();
    }
}
