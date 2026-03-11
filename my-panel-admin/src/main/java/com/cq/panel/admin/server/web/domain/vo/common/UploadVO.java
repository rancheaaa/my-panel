package com.cq.panel.admin.server.web.domain.vo.common;

import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(description = "上传响应对象")
public class UploadVO {
    @Schema(description = "文件地址", example = "http://localhost:8080/profile/upload/2024/01/01/uuid.png")
    private String url;
    
    @Schema(description = "文件名称", example = "2024/01/01/uuid.png")
    private String fileName;
    
    @Schema(description = "新文件名称", example = "uuid.png")
    private String newFileName;
    
    @Schema(description = "原始文件名称", example = "avatar.png")
    private String originalFilename;

    public UploadVO(String url, String fileName, String newFileName, String originalFilename) {
        this.url = url;
        this.fileName = fileName;
        this.newFileName = newFileName;
        this.originalFilename = originalFilename;
    }
}

