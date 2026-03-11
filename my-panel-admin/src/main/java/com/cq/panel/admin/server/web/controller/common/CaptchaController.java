package com.cq.panel.admin.server.web.controller.common;

import com.cq.panel.admin.server.config.AppConfig;
import com.cq.panel.admin.server.common.constant.CacheConstants;
import com.cq.panel.admin.server.common.constant.Constants;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.common.CaptchaVO;
import com.cq.panel.admin.server.web.service.cache.CacheService;
import java.util.Base64;
import com.cq.panel.admin.server.common.utils.uuid.IdUtils;
import com.cq.panel.admin.server.repository.service.ISysConfigService;
import com.cq.panel.admin.server.web.exception.ServiceException;
import com.google.code.kaptcha.Producer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 验证码操作处理
 * 
 * @author cq
 */
@Tag(name = "验证码管理")
@RestController
public class CaptchaController
{
    private static final Logger log = LoggerFactory.getLogger(CaptchaController.class);

    @Resource(name = "captchaProducer")
    private Producer captchaProducer;

    @Resource(name = "captchaProducerMath")
    private Producer captchaProducerMath;

    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private ISysConfigService configService;

    @Autowired
    private AppConfig appConfig;

    /**
     * 生成验证码
     */
    @Operation(summary = "生成验证码")
    @GetMapping("/captchaImage")
    public Result<CaptchaVO> getCode(HttpServletResponse response) throws IOException
    {
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (!captchaEnabled)
        {
            return Result.success(new CaptchaVO(captchaEnabled, null, null));
        }

        // 保存验证码信息
        String uuid = IdUtils.simpleUUID();
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;

        String capStr = null, code = null;
        BufferedImage image = null;

        // 生成验证码
        String captchaType = this.appConfig.getCaptchaType();
        if ("math".equals(captchaType))
        {
            String capText = captchaProducerMath.createText();
            capStr = capText.substring(0, capText.lastIndexOf("@"));
            code = capText.substring(capText.lastIndexOf("@") + 1);
            image = captchaProducerMath.createImage(capStr);
        }
        else if ("char".equals(captchaType))
        {
            capStr = code = captchaProducer.createText();
            image = captchaProducer.createImage(capStr);
        }

        cacheService.set(verifyKey, code, Constants.CAPTCHA_EXPIRATION, TimeUnit.MINUTES);
        // 转换流信息写出
        FastByteArrayOutputStream os = new FastByteArrayOutputStream();
        try
        {
            ImageIO.write(image, "jpg", os);
        }
        catch (IOException e)
        {
            throw new ServiceException(e.getMessage());
        }

        return Result.success(new CaptchaVO(captchaEnabled, uuid, Base64.getEncoder().encodeToString(os.toByteArray())));
    }
}


