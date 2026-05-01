package com.cq.agent.handler.batch;

import com.cq.agent.batch.scanner.BatchFileScanner;
import com.cq.agent.batch.scanner.ScanRequest;
import com.cq.agent.batch.scanner.ScanResponse;
import com.cq.agent.dto.ApiResponse;
import com.cq.agent.handler.BaseHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

public class BatchScanHandler extends BaseHandler
{
    private static final Gson gson = new Gson();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatchScanHandler.class);
    private final BatchFileScanner scanner;

    public BatchScanHandler(FileService fileService, ChunkedTransferService chunkedTransferService, BatchFileScanner scanner)
    {
        super(fileService, chunkedTransferService);
        this.scanner = scanner;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest request)
    {
        try
        {
            ScanRequest scanRequest = parseBody(request, ScanRequest.class);
            if (scanRequest == null)
            {
                sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST,
                        createErrorResponse(com.cq.agent.dto.ApiCode.INVALID_REQUEST, "Invalid request body"));
                return;
            }
            ScanResponse response = scanner.scan(scanRequest);
            sendSuccessResponse(ctx, request, response);
        }
        catch (Exception e)
        {
            log.error("BatchScanHandler error: {}", e.getMessage(), e);
            sendResponse(ctx, request, io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse(com.cq.agent.dto.ApiCode.GENERIC_ERROR, "Scan error: " + e.getMessage()));
        }
    }
}
