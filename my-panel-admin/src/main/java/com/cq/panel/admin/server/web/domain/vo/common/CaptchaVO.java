package com.cq.panel.admin.server.web.domain.vo.common;

import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(description = "验证码信息")
public class CaptchaVO {
    @Schema(description = "是否开启验证码", example = "true")
    private boolean captchaEnabled;
    
    @Schema(description = "验证码UUID", example = "a1b2c3d4-e5f6-7g8h-9i0j")
    private String uuid;
    
    @Schema(description = "验证码图片Base64", example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...")
    private String img;

    public CaptchaVO(boolean captchaEnabled, String uuid, String img) {
        this.captchaEnabled = captchaEnabled;
        this.uuid = uuid;
        this.img = img;
    }
}

