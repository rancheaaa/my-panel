package com.cq.panel.admin.server.web.domain.vo.common;

import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(description = "多文件上传响应对象")
public class UploadsVO {
    @Schema(description = "文件地址列表，逗号分隔", example = "http://.../1.png,http://.../2.png")
    private String urls;
    
    @Schema(description = "文件名称列表，逗号分隔", example = "path/1.png,path/2.png")
    private String fileNames;
    
    @Schema(description = "新文件名称列表，逗号分隔", example = "1.png,2.png")
    private String newFileNames;
    
    @Schema(description = "原始文件名称列表，逗号分隔", example = "a.png,b.png")
    private String originalFilenames;

    public UploadsVO(String urls, String fileNames, String newFileNames, String originalFilenames) {
        this.urls = urls;
        this.fileNames = fileNames;
        this.newFileNames = newFileNames;
        this.originalFilenames = originalFilenames;
    }
}

