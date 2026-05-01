package com.cq.agent.batch.report;

import com.cq.agent.dto.ApiResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyReportClient
{
    private static final Logger logger = LoggerFactory.getLogger(ProxyReportClient.class);
    private final HttpClient httpClient;
    private final String proxyBaseUrl;
    private final Gson gson = new Gson();
    private final ExecutorService reportExecutor;

    public ProxyReportClient(String proxyBaseUrl)
    {
        this.proxyBaseUrl = proxyBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.reportExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "proxy-report");
            t.setDaemon(true);
            return t;
        });
    }

    public void asyncReportProgress(ProgressReport report)
    {
        CompletableFuture.runAsync(() -> {
            try
            {
                postApi("/api/internal/batch/progress", report);
            }
            catch (Exception e)
            {
                logger.warn("Failed to report progress for subtask {}: {}", report.getSubtaskId(), e.getMessage());
            }
        }, reportExecutor);
    }

    public void reportQueueSnapshot(QueueSnapshotReport snapshot)
    {
        try
        {
            postApi("/api/internal/batch/queue/snapshot", snapshot);
        }
        catch (Exception e)
        {
            logger.warn("Failed to report queue snapshot: {}", e.getMessage());
        }
    }

    public void reportPostProcessResult(Long taskId, Object result)
    {
        try
        {
            postApi("/api/internal/batch/post-process-result", result);
        }
        catch (Exception e)
        {
            logger.warn("Failed to report post-process result for task {}: {}", taskId, e.getMessage());
        }
    }

    private void postApi(String endpoint, Object body) throws Exception
    {
        String json = gson.toJson(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(proxyBaseUrl + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
        {
            logger.warn("Proxy API {} returned status {}: {}", endpoint, response.statusCode(), response.body());
        }
    }
}
