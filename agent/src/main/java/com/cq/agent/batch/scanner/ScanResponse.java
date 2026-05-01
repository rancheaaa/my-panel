package com.cq.agent.batch.scanner;

import lombok.Data;
import java.util.List;

@Data
public class ScanResponse
{
    private String requestId;
    private boolean success;
    private long scanDurationMs;
    private String errorMessage;
    private ScanResult result;

    public static ScanResponse success(String requestId, ScanResult result, long durationMs)
    {
        ScanResponse resp = new ScanResponse();
        resp.setRequestId(requestId);
        resp.setSuccess(true);
        resp.setResult(result);
        resp.setScanDurationMs(durationMs);
        return resp;
    }

    public static ScanResponse error(String requestId, String errorMessage)
    {
        ScanResponse resp = new ScanResponse();
        resp.setRequestId(requestId);
        resp.setSuccess(false);
        resp.setErrorMessage(errorMessage);
        return resp;
    }
}
