package com.cq.agent.handler;

import com.cq.agent.batch.queue.BatchUploadTask;
import com.cq.agent.handler.download.ChunkDownloadHandler;
import com.cq.agent.handler.download.ChunkDownloadInfoHandler;
import com.cq.agent.handler.upload.*;
import com.cq.agent.handler.file.*;
import com.cq.agent.handler.batch.*;
import com.cq.agent.batch.scanner.BatchFileScanner;
import com.cq.agent.batch.queue.BatchTransferQueueManager;
import com.cq.agent.batch.postprocess.PostTransferHandler;
import com.cq.agent.batch.report.ProxyReportClient;
import com.cq.agent.batch.scheduler.BatchScanScheduler;
import com.cq.agent.client.upload.BatchAwareAgentUploader;
import com.cq.agent.config.AgentConfig;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandlerFactory {

    private final Map<String, IRequestHandler> handlerMap = new HashMap<>();
    private final BatchScanScheduler scanScheduler;

    public HandlerFactory(AgentConfig agentConfig, FileService fileService, ChunkedTransferService chunkedTransferService) {
        String apiPrefix = "/api/file";
        
        // File operations
        handlerMap.put(apiPrefix + "/syst", new SystHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/feat", new FeatHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/list", new ListHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/nlst", new NlstHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/retr", new RetrHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/stor", new StorHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/stou", new StouHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/appe", new AppeHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/dele", new DeleHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/mkd", new MkdHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/rmd", new RmdHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/pwd", new PwdHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/size", new SizeHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/mdtm", new MdtmHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/mfmt", new MfmtHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/rename", new RenameHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/copy", new CopyHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/stat", new StatHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/exists", new ExistsHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/chmod", new ChmodHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/checksum", new ChecksumHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/search", new SearchHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/disk", new DiskHandler(fileService, chunkedTransferService));

        // Chunked transfer operations
        handlerMap.put(apiPrefix + "/chunk/init", new ChunkInitHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/chunk/upload", new ChunkUploadHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/chunk/merge", new ChunkMergeHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/chunk/status", new ChunkStatusHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/chunk/cancel", new ChunkCancelHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/chunk/sessions", new ChunkSessionsHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/chunk/download/info", new ChunkDownloadInfoHandler(fileService, chunkedTransferService));
        handlerMap.put(apiPrefix + "/chunk/download", new ChunkDownloadHandler(fileService, chunkedTransferService));

        // Batch transfer operations
        String batchPrefix = "/api/internal/batch";
        BatchFileScanner batchScanner = new BatchFileScanner();
        BatchTransferQueueManager queueManager = new BatchTransferQueueManager(10000);
        PostTransferHandler postTransferHandler = new PostTransferHandler();
        ProxyReportClient proxyReportClient = buildProxyReportClient(agentConfig);
        BatchAwareAgentUploader batchUploader = new BatchAwareAgentUploader(agentConfig, queueManager, proxyReportClient);

        try {
            this.scanScheduler = new BatchScanScheduler(batchScanner, proxyReportClient, agentConfig.getFileBaseDirectory());
        } catch (Exception e) {
            throw new RuntimeException("Failed to init BatchScanScheduler: " + e.getMessage(), e);
        }

        handlerMap.put(batchPrefix + "/scan", new BatchScanHandler(fileService, chunkedTransferService, batchScanner));
        handlerMap.put(batchPrefix + "/dispatch", new BatchDispatchHandler(fileService, chunkedTransferService, queueManager));
        handlerMap.put(batchPrefix + "/post-process", new BatchPostProcessHandler(fileService, chunkedTransferService, postTransferHandler));
        handlerMap.put(batchPrefix + "/schedule", new BatchScanScheduleHandler(fileService, chunkedTransferService, scanScheduler));
    }

    private ProxyReportClient buildProxyReportClient(AgentConfig agentConfig)
    {
        List<String> registryServerUrls = agentConfig.getRegistryServerUrls();
        String proxyBaseUrl = (registryServerUrls == null || registryServerUrls.isEmpty())
                ? "http://127.0.0.1:9876"
                : registryServerUrls.get(0);
        return new ProxyReportClient(proxyBaseUrl);
    }

    public IRequestHandler getHandler(String path) {
        return handlerMap.get(path);
    }

    public BatchScanScheduler getScanScheduler() {
        return scanScheduler;
    }
}
