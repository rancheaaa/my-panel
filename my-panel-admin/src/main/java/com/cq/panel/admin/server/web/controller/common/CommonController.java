package com.cq.panel.admin.server.web.controller.common;

import com.cq.panel.admin.server.config.AppConfig;
import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.common.UploadVO;
import com.cq.panel.admin.server.web.domain.vo.common.UploadsVO;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.common.utils.file.FileUploadUtils;
import com.cq.panel.admin.server.common.utils.file.FileUtils;
import com.cq.panel.admin.server.config.ServerConfig;
import com.cq.panel.admin.server.web.exception.ServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用请求处理
 *
 * @author cq
 */
@Tag(name = "通用请求处理", description = "文件上传下载等通用接口")
@RestController
@RequestMapping("/common")
public class CommonController {
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    private final ServerConfig serverConfig;

    private final AppConfig appConfig;

    private static final String FILE_DELIMiTER = ",";

    public CommonController(ServerConfig serverConfig, AppConfig appConfig) {
        this.serverConfig = serverConfig;
        this.appConfig = appConfig;
    }

    /**
     * 通用下载请求
     *
     * @param fileName 文件名称
     * @param delete   是否删除
     */
    @Operation(summary = "通用下载请求", description = "下载服务器上的文件")
    @GetMapping("/download")
    public void fileDownload(@Parameter(description = "文件名称", required = true) @RequestParam String fileName,
                             @Parameter(description = "是否删除", example = "false") @RequestParam(required = false) Boolean delete,
                             HttpServletResponse response) {
        try {
            if (!FileUtils.checkAllowDownload(fileName)) {
                throw new Exception(MyStringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            String filePath = this.appConfig.getDownloadPath() + fileName;

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
            if (delete) {
                FileUtils.deleteFile(filePath);
            }
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 通用上传请求（单个）
     */
    @Operation(summary = "通用上传请求（单个）", description = "上传单个文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadVO> uploadFile(@Parameter(description = "文件", required = true) @RequestPart MultipartFile file) {
        try {
            // 上传文件路径
            String filePath = this.appConfig.getUploadPath();
            // 上传并返回新文件名称
            String fileName = FileUploadUtils.upload(filePath, file);
            String url = serverConfig.getUrl() + fileName;
            return Result.success(new UploadVO(url, fileName, FileUtils.getName(fileName), file.getOriginalFilename()));
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 通用上传请求（多个）
     */
    @Operation(summary = "通用上传请求（多个）", description = "上传多个文件")
    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadsVO> uploadFiles(@Parameter(description = "文件列表", required = true) @RequestPart List<MultipartFile> files) {
        try {
            // 上传文件路径
            String filePath = this.appConfig.getUploadPath();
            List<String> urls = new ArrayList<>();
            List<String> fileNames = new ArrayList<>();
            List<String> newFileNames = new ArrayList<>();
            List<String> originalFilenames = new ArrayList<>();
            for (MultipartFile file : files) {
                // 上传并返回新文件名称
                String fileName = FileUploadUtils.upload(filePath, file);
                String url = serverConfig.getUrl() + fileName;
                urls.add(url);
                fileNames.add(fileName);
                newFileNames.add(FileUtils.getName(fileName));
                originalFilenames.add(file.getOriginalFilename());
            }
            return Result.success(new UploadsVO(
                    MyStringUtils.join(urls, FILE_DELIMiTER),
                    MyStringUtils.join(fileNames, FILE_DELIMiTER),
                    MyStringUtils.join(newFileNames, FILE_DELIMiTER),
                    MyStringUtils.join(originalFilenames, FILE_DELIMiTER)
            ));
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 本地资源通用下载
     */
    @Operation(summary = "本地资源通用下载", description = "下载本地资源文件")
    @GetMapping("/download/resource")
    public void resourceDownload(@Parameter(description = "资源名称", required = true) @RequestParam String resource, HttpServletResponse response) {
        try {
            if (!FileUtils.checkAllowDownload(resource)) {
                throw new Exception(MyStringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            // 本地资源路径
            String localPath = this.appConfig.getProfile();
            // 数据库资源地址
            String downloadPath = localPath + MyStringUtils.substringAfter(resource, Constants.RESOURCE_PREFIX);
            // 下载名称
            String downloadName = MyStringUtils.substringAfterLast(downloadPath, "/");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(downloadPath, response.getOutputStream());
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }
}



