package com.cq.panel.admin.server.web.domain.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户头像上传响应对象")
public class UserAvatarUpdateVO {
    @Schema(description = "头像路径", example = "/profile/avatar/2024/01/01/uuid.jpg")
    private String imgUrl;
    
    public UserAvatarUpdateVO(String imgUrl) {
        this.imgUrl = imgUrl;
    }
}
