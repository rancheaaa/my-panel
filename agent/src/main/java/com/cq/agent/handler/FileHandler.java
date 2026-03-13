 package com.cq.agent.handler;

 import com.cq.agent.model.FileInfo;
 import com.cq.agent.model.UploadSession;
 import com.cq.agent.service.ChunkedTransferService;
 import com.cq.agent.service.FileService;
 import com.cq.agent.service.FileService.ServiceResult;
 import com.google.gson.Gson;
 import com.google.gson.JsonObject;
 import com.google.gson.JsonSyntaxException;
 import io.netty.buffer.ByteBuf;
 import io.netty.buffer.Unpooled;
 import io.netty.channel.ChannelFuture;
 import io.netty.channel.ChannelFutureListener;
 import io.netty.channel.ChannelHandlerContext;
 import io.netty.channel.DefaultFileRegion;
 import io.netty.channel.SimpleChannelInboundHandler;
 import io.netty.handler.codec.http.*;
 import io.netty.handler.codec.http.multipart.*;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import java.io.IOException;
 import java.io.RandomAccessFile;
 import java.nio.charset.StandardCharsets;
 import java.nio.file.Path;
 import java.util.Base64;
 import java.util.List;
 import java.util.Map;

/**
 * HTTP handler for file operations (FTP-like commands).
 */
public class FileHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger logger = LoggerFactory.getLogger(FileHandler.class);
    private static final Gson gson = new Gson();

    // API Paths
    private static final String API_PREFIX = "/api/file";
    private static final String SYST_PATH = API_PREFIX + "/syst";
    private static final String FEAT_PATH = API_PREFIX + "/feat";
    private static final String LIST_PATH = API_PREFIX + "/list";
    private static final String NLST_PATH = API_PREFIX + "/nlst";
    private static final String RETR_PATH = API_PREFIX + "/retr";
    private static final String RETR_RAW_PATH = API_PREFIX + "/retr-raw";
    private static final String STOR_PATH = API_PREFIX + "/stor";
    private static final String STOU_PATH = API_PREFIX + "/stou";
    private static final String APPE_PATH = API_PREFIX + "/appe";
    private static final String DELE_PATH = API_PREFIX + "/dele";
    private static final String MKD_PATH = API_PREFIX + "/mkd";
    private static final String RMD_PATH = API_PREFIX + "/rmd";
    private static final String PWD_PATH = API_PREFIX + "/pwd";
    private static final String SIZE_PATH = API_PREFIX + "/size";
    private static final String MDTM_PATH = API_PREFIX + "/mdtm";
    private static final String MFMT_PATH = API_PREFIX + "/mfmt";
    private static final String RENAME_PATH = API_PREFIX + "/rename";
    private static final String COPY_PATH = API_PREFIX + "/copy";
    private static final String STAT_PATH = API_PREFIX + "/stat";
    private static final String EXISTS_PATH = API_PREFIX + "/exists";
    private static final String CHMOD_PATH = API_PREFIX + "/chmod";
    private static final String CHECKSUM_PATH = API_PREFIX + "/checksum";
    private static final String SEARCH_PATH = API_PREFIX + "/search";
    private static final String DISK_PATH = API_PREFIX + "/disk";
    private static final String CHUNK_INIT_PATH = API_PREFIX + "/chunk/init";
    private static final String CHUNK_UPLOAD_PATH = API_PREFIX + "/chunk/upload";
    private static final String CHUNK_MERGE_PATH = API_PREFIX + "/chunk/merge";
    private static final String CHUNK_STATUS_PATH = API_PREFIX + "/chunk/status";
    private static final String CHUNK_CANCEL_PATH = API_PREFIX + "/chunk/cancel";
    private static final String CHUNK_SESSIONS_PATH = API_PREFIX + "/chunk/sessions";
    private static final String CHUNK_DOWNLOAD_INFO_PATH = API_PREFIX + "/chunk/download/info";
    private static final String CHUNK_DOWNLOAD_RANGE_PATH = API_PREFIX + "/chunk/download";

    private final FileService fileService;
    private final ChunkedTransferService chunkedTransferService;

    public FileHandler(FileService fileService, ChunkedTransferService chunkedTransferService) {
        this.fileService = fileService;
        this.chunkedTransferService = chunkedTransferService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess()) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid request"));
            return;
        }

        String uri = request.uri();
        // Remove query string for path matching
        int queryIndex = uri.indexOf('?');
        String path = queryIndex > 0 ? uri.substring(0, queryIndex) : uri;
        HttpMethod method = request.method();

        logger.debug("File request: {} {}", method, uri);

        try {
            switch (path) {
                case SYST_PATH -> handleSyst(ctx);
                case FEAT_PATH -> handleFeat(ctx);
                case LIST_PATH -> handleList(ctx, request);
                case NLST_PATH -> handleNlst(ctx, request);
                case RETR_PATH -> handleRetr(ctx, request);
                case RETR_RAW_PATH -> handleRetrRaw(ctx, request);
                case STOR_PATH -> handleStor(ctx, request);
                case STOU_PATH -> handleStou(ctx, request);
                case APPE_PATH -> handleAppe(ctx, request);
                case DELE_PATH -> handleDele(ctx, request);
                case MKD_PATH -> handleMkd(ctx, request);
                case RMD_PATH -> handleRmd(ctx, request);
                case PWD_PATH -> handlePwd(ctx, request);
                case SIZE_PATH -> handleSize(ctx, request);
                case MDTM_PATH -> handleMdtm(ctx, request);
                case MFMT_PATH -> handleMfmt(ctx, request);
                case RENAME_PATH -> handleRename(ctx, request);
                case COPY_PATH -> handleCopy(ctx, request);
                case STAT_PATH -> handleStat(ctx, request);
                case EXISTS_PATH -> handleExists(ctx, request);
                case CHMOD_PATH -> handleChmod(ctx, request);
                case CHECKSUM_PATH -> handleChecksum(ctx, request);
                case SEARCH_PATH -> handleSearch(ctx, request);
                case DISK_PATH -> handleDisk(ctx, request);
                case CHUNK_INIT_PATH -> handleChunkInit(ctx, request);
                case CHUNK_UPLOAD_PATH -> handleChunkUpload(ctx, request);
                case CHUNK_MERGE_PATH -> handleChunkMerge(ctx, request);
                case CHUNK_STATUS_PATH -> handleChunkStatus(ctx, request);
                case CHUNK_CANCEL_PATH -> handleChunkCancel(ctx, request);
                case CHUNK_SESSIONS_PATH -> handleChunkSessions(ctx, request);
                case CHUNK_DOWNLOAD_INFO_PATH -> handleChunkDownloadInfo(ctx, request);
                case CHUNK_DOWNLOAD_RANGE_PATH -> handleChunkDownload(ctx, request);
                default -> ctx.fireChannelRead(request.retain());
            }
        } catch (Exception e) {
            logger.error("Error processing file request", e);
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse("Internal error: " + e.getMessage()));
        }
    }

    private void handleSyst(ChannelHandlerContext ctx) {
        Map<String, Object> info = fileService.getSystemInfo();
        sendSuccessResponse(ctx, info);
    }

    private void handleFeat(ChannelHandlerContext ctx) {
        List<String> features = fileService.getFeatures();
        sendSuccessResponse(ctx, features);
    }

    private void handleList(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", ".");
        ServiceResult<List<FileInfo>> result = fileService.list(path);
        sendServiceResult(ctx, result);
    }

    private void handleNlst(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", ".");
        ServiceResult<List<String>> result = fileService.nameList(path);
        sendServiceResult(ctx, result);
    }

    private void handleRetr(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }

        String mode = getQueryParam(request, "mode", "binary");
        String charset = getQueryParam(request, "charset", "UTF-8");

        if ("text".equals(mode)) {
            ServiceResult<String> result = fileService.retrieveText(path, charset);
            sendServiceResult(ctx, result);
        } else {
            ServiceResult<byte[]> result = fileService.retrieve(path);
            if (result.isSuccess()) {
                // Return as base64 encoded string for JSON response
                String base64 = Base64.getEncoder().encodeToString(result.getData());
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("data", base64);
                response.addProperty("size", result.getData().length);
                response.addProperty("encoding", "base64");
                sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(response));
            } else {
                sendResponse(ctx, HttpResponseStatus.OK, createErrorResponse(result.getError()));
            }
        }
    }

    private void handleRetrRaw(ChannelHandlerContext ctx, FullHttpRequest request) {
        String pathParam = getQueryParam(request, "path", null);
        if (pathParam == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }

        ServiceResult<FileInfo> statResult = fileService.stat(pathParam);
        if (!statResult.isSuccess()) {
            sendResponse(ctx, HttpResponseStatus.OK, createErrorResponse(statResult.getError()));
            return;
        }

        FileInfo info = statResult.getData();
        long fileSize = info.getSize();

        String startParam = getQueryParam(request, "start", null);
        String lengthParam = getQueryParam(request, "length", null);

        long start = 0;
        if (startParam != null && !startParam.isEmpty()) {
            try {
                start = Long.parseLong(startParam);
            } catch (NumberFormatException e) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid 'start' parameter"));
                return;
            }
        }

        if (start < 0 || start >= fileSize) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid 'start' parameter range"));
            return;
        }

        long length;
        if (lengthParam != null && !lengthParam.isEmpty()) {
            try {
                length = Long.parseLong(lengthParam);
            } catch (NumberFormatException e) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid 'length' parameter"));
                return;
            }
        } else {
            length = fileSize - start;
        }

        if (length <= 0 || start + length > fileSize) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid range"));
            return;
        }

        Path path = Path.of(info.getPath());

        try {
            RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r");
            raf.seek(start);
            long contentLength = length;

            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, start == 0 && length == fileSize
                    ? HttpResponseStatus.OK
                    : HttpResponseStatus.PARTIAL_CONTENT);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + info.getName() + "\"");
            if (start != 0 || length != fileSize) {
                String contentRange = "bytes " + start + "-" + (start + length - 1) + "/" + fileSize;
                response.headers().set(HttpHeaderNames.CONTENT_RANGE, contentRange);
            }
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

            ctx.write(response);

            DefaultFileRegion region = new DefaultFileRegion(raf.getChannel(), start, contentLength);
            ChannelFuture sendFileFuture = ctx.write(region);
            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

            lastContentFuture.addListener((ChannelFutureListener) future -> {
                try {
                    region.release();
                } finally {
                    try {
                        raf.close();
                    } catch (IOException ignored) {
                    }
                    ctx.close();
                }
            });
        } catch (IOException e) {
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, createErrorResponse("Failed to read file: " + e.getMessage()));
        }
    }

    private void handleStor(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String path = getJsonString(body, "path");
            if (path == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' field is required"));
                return;
            }

            byte[] content;
            if (body.has("content")) {
                String contentStr = body.get("content").getAsString();
                String encoding = getJsonString(body, "encoding");
                if ("base64".equals(encoding)) {
                    content = Base64.getDecoder().decode(contentStr);
                } else {
                    content = contentStr.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'content' field is required"));
                return;
            }

            ServiceResult<FileInfo> result = fileService.store(path, content);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleStou(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String directory = getJsonString(body, "directory");
            if (directory == null) {
                directory = ".";
            }

            String prefix = getJsonString(body, "prefix");

            byte[] content;
            if (body.has("content")) {
                String contentStr = body.get("content").getAsString();
                String encoding = getJsonString(body, "encoding");
                if ("base64".equals(encoding)) {
                    content = Base64.getDecoder().decode(contentStr);
                } else {
                    content = contentStr.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'content' field is required"));
                return;
            }

            ServiceResult<FileInfo> result = fileService.storeUnique(directory, content, prefix);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleAppe(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String path = getJsonString(body, "path");
            if (path == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' field is required"));
                return;
            }

            byte[] content;
            if (body.has("content")) {
                String contentStr = body.get("content").getAsString();
                String encoding = getJsonString(body, "encoding");
                if ("base64".equals(encoding)) {
                    content = Base64.getDecoder().decode(contentStr);
                } else {
                    content = contentStr.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'content' field is required"));
                return;
            }

            ServiceResult<FileInfo> result = fileService.append(path, content);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleDele(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        ServiceResult<Void> result = fileService.delete(path);
        sendServiceResult(ctx, result);
    }

    private void handleMkd(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        ServiceResult<FileInfo> result = fileService.makeDirectory(path);
        sendServiceResult(ctx, result);
    }

    private void handleRmd(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        boolean recursive = "true".equals(getQueryParam(request, "recursive", "false"));
        ServiceResult<Void> result = fileService.removeDirectory(path, recursive);
        sendServiceResult(ctx, result);
    }

    private void handlePwd(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", ".");
        ServiceResult<FileInfo> result = fileService.printWorkingDirectory(path);
        sendServiceResult(ctx, result);
    }

    private void handleSize(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        ServiceResult<Long> result = fileService.getSize(path);
        sendServiceResult(ctx, result);
    }

    private void handleMdtm(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        ServiceResult<Long> result = fileService.getModificationTime(path);
        sendServiceResult(ctx, result);
    }

    private void handleMfmt(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String path = getJsonString(body, "path");
            if (path == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' field is required"));
                return;
            }
            if (!body.has("timestamp")) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'timestamp' field is required"));
                return;
            }
            long timestamp = body.get("timestamp").getAsLong();
            ServiceResult<FileInfo> result = fileService.setModificationTime(path, timestamp);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleRename(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String from = getJsonString(body, "from");
            String to = getJsonString(body, "to");
            if (from == null || to == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'from' and 'to' fields are required"));
                return;
            }
            ServiceResult<FileInfo> result = fileService.rename(from, to);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleCopy(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String from = getJsonString(body, "from");
            String to = getJsonString(body, "to");
            if (from == null || to == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'from' and 'to' fields are required"));
                return;
            }
            ServiceResult<FileInfo> result = fileService.copy(from, to);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleStat(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        ServiceResult<FileInfo> result = fileService.stat(path);
        sendServiceResult(ctx, result);
    }

    private void handleExists(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        ServiceResult<Boolean> result = fileService.exists(path);
        sendServiceResult(ctx, result);
    }

    private void handleChmod(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String path = getJsonString(body, "path");
            String permissions = getJsonString(body, "permissions");
            if (path == null || permissions == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' and 'permissions' fields are required"));
                return;
            }
            ServiceResult<FileInfo> result = fileService.chmod(path, permissions);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleChecksum(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        String algorithm = getQueryParam(request, "algorithm", "MD5");
        ServiceResult<String> result = fileService.checksum(path, algorithm);
        sendServiceResult(ctx, result);
    }

    private void handleSearch(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", ".");
        String pattern = getQueryParam(request, "pattern", "*");
        int maxDepth = Integer.parseInt(getQueryParam(request, "maxDepth", "10"));
        ServiceResult<List<FileInfo>> result = fileService.search(path, pattern, maxDepth);
        sendServiceResult(ctx, result);
    }

    private void handleDisk(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", ".");
        ServiceResult<Map<String, Long>> result = fileService.getDiskSpace(path);
        sendServiceResult(ctx, result);
    }

    private void handleChunkInit(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String targetPath = getJsonString(body, "targetPath");
            String fileName = getJsonString(body, "fileName");
            String transferId = getJsonString(body, "transferId"); // Optional
            if (targetPath == null || fileName == null || !body.has("totalSize")) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'targetPath', 'fileName' and 'totalSize' fields are required"));
                return;
            }
            long totalSize = body.get("totalSize").getAsLong();
            ServiceResult<UploadSession> result = chunkedTransferService.initUpload(transferId, targetPath, fileName, totalSize);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleChunkUpload(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String transferId = getJsonString(body, "transferId");
            if (transferId == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'transferId' field is required"));
                return;
            }
            if (!body.has("chunkIndex")) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'chunkIndex' field is required"));
                return;
            }
            int chunkIndex = body.get("chunkIndex").getAsInt();

            byte[] content;
            if (body.has("content")) {
                String contentStr = body.get("content").getAsString();
                String encoding = getJsonString(body, "encoding");
                if ("base64".equals(encoding)) {
                    content = Base64.getDecoder().decode(contentStr);
                } else {
                    content = contentStr.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'content' field is required"));
                return;
            }

            ServiceResult<Map<String, Object>> result = chunkedTransferService.uploadChunk(transferId, chunkIndex, content);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleChunkMerge(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String transferId = getJsonString(body, "transferId");
            if (transferId == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'transferId' field is required"));
                return;
            }
            ServiceResult<FileInfo> result = chunkedTransferService.mergeChunks(transferId);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleChunkStatus(ChannelHandlerContext ctx, FullHttpRequest request) {
        String transferId = getQueryParam(request, "transferId", null);
        if (transferId == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'transferId' parameter is required"));
            return;
        }
        ServiceResult<Map<String, Object>> result = chunkedTransferService.getUploadStatus(transferId);
        sendServiceResult(ctx, result);
    }

    private void handleChunkCancel(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            JsonObject body = parseJsonBody(request);
            String transferId = getJsonString(body, "transferId");
            if (transferId == null) {
                sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'transferId' field is required"));
                return;
            }
            ServiceResult<Void> result = chunkedTransferService.cancelUpload(transferId);
            sendServiceResult(ctx, result);
        } catch (JsonSyntaxException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid JSON format"));
        }
    }

    private void handleChunkSessions(ChannelHandlerContext ctx, FullHttpRequest request) {
        List<Map<String, Object>> sessions = chunkedTransferService.listUploadSessions();
        sendSuccessResponse(ctx, sessions);
    }

    private void handleChunkDownloadInfo(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        ServiceResult<Map<String, Object>> result = chunkedTransferService.getDownloadInfo(path);
        sendServiceResult(ctx, result);
    }

    private void handleChunkDownload(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = getQueryParam(request, "path", null);
        if (path == null) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("'path' parameter is required"));
            return;
        }
        String startParam = getQueryParam(request, "start", null);
        String endParam = getQueryParam(request, "end", null);
        long start = 0;
        long end = -1;
        try {
            if (startParam != null && !startParam.isEmpty()) {
                start = Long.parseLong(startParam);
            }
            if (endParam != null && !endParam.isEmpty()) {
                end = Long.parseLong(endParam);
            }
        } catch (NumberFormatException e) {
            sendResponse(ctx, HttpResponseStatus.BAD_REQUEST, createErrorResponse("Invalid 'start' or 'end' parameter"));
            return;
        }

        ChunkedTransferService.ChunkedDownloadResult dataResult;
        ServiceResult<ChunkedTransferService.ChunkedDownloadResult> result = chunkedTransferService.downloadRange(path, start, end);
        if (!result.isSuccess()) {
            sendResponse(ctx, HttpResponseStatus.OK, createErrorResponse(result.getError()));
            return;
        }
        dataResult = result.getData();

        String base64 = Base64.getEncoder().encodeToString(dataResult.getData());
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("data", base64);
        response.addProperty("encoding", "base64");
        response.addProperty("rangeStart", dataResult.getRangeStart());
        response.addProperty("rangeEnd", dataResult.getRangeEnd());
        response.addProperty("totalSize", dataResult.getTotalSize());
        response.addProperty("fileName", dataResult.getFileName());
        sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(response));
    }

    // Helper methods
    private String getQueryParam(FullHttpRequest request, String name, String defaultValue) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        List<String> values = decoder.parameters().get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return defaultValue;
    }

    private JsonObject parseJsonBody(FullHttpRequest request) {
        String body = request.content().toString(StandardCharsets.UTF_8);
        if (body.isEmpty()) {
            return new JsonObject();
        }
        return gson.fromJson(body, JsonObject.class);
    }

    private String getJsonString(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return null;
    }

    private <T> void sendServiceResult(ChannelHandlerContext ctx, ServiceResult<T> result) {
        if (result.isSuccess()) {
            sendSuccessResponse(ctx, result.getData());
        } else {
            sendResponse(ctx, HttpResponseStatus.OK, createErrorResponse(result.getError()));
        }
    }

    private void sendSuccessResponse(ChannelHandlerContext ctx, Object data) {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.add("data", gson.toJsonTree(data));
        sendResponse(ctx, HttpResponseStatus.OK, gson.toJson(response));
    }

    private String createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        return gson.toJson(error);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, String content) {
        ByteBuf buffer = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught in file handler", cause);
        if (ctx.channel().isActive()) {
            sendResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    createErrorResponse("Internal server error"));
        }
    }
}
