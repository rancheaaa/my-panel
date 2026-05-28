package com.cq.agent.handler;

import com.cq.agent.batch.config.ConfigChangeListener;
import com.cq.agent.batch.config.ConfigFileManager;
import com.cq.agent.client.upload.BatchTaskSchedulerUploader;
import com.cq.agent.handler.batch.BatchConfigReceiveHandler;
import com.cq.agent.handler.batch.BatchTaskControlHandler;
import com.cq.agent.handler.download.ChunkDownloadHandler;
import com.cq.agent.handler.download.ChunkDownloadInfoHandler;
import com.cq.agent.handler.upload.*;
import com.cq.agent.handler.file.*;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;

import java.util.HashMap;
import java.util.Map;

public class HandlerFactory {

    private final Map<String, IRequestHandler> handlerMap = new HashMap<>();

    public HandlerFactory(FileService fileService, ChunkedTransferService chunkedTransferService,
                          ConfigFileManager configFileManager, ConfigChangeListener configChangeListener,
                          BatchTaskSchedulerUploader batchTaskUploader) {
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

        // Batch config receive
        handlerMap.put("/api/batch/task/config", new BatchConfigReceiveHandler(fileService, chunkedTransferService, configFileManager, configChangeListener));
        
        // Batch task control (pause/resume/delete)
        handlerMap.put("/api/batch/task/control", new BatchTaskControlHandler(batchTaskUploader));
    }

    public IRequestHandler getHandler(String path) {
        return handlerMap.get(path);
    }
}
