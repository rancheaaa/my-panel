package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.dto.system.RegisterDTO;
import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.repository.service.ISysConfigService;
import com.cq.panel.admin.server.web.service.SysRegisterService;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册验证
 * 
 * @author cq
 */
@Tag(name = "注册验证")
@RestController
public class SysRegisterController extends BaseController
{
    private final SysRegisterService registerService;

    private final ISysConfigService configService;

    public SysRegisterController(SysRegisterService registerService, ISysConfigService configService) {
        this.registerService = registerService;
        this.configService = configService;
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO registerDTO)
    {
        if (!("true".equals(configService.selectConfigByKey("sys.account.registerUser"))))
        {
            return Result.error("当前系统没有开启注册功能！");
        }
        String msg = registerService.register(registerDTO);
        if (StringUtils.isNotEmpty(msg))
        {
            return Result.error(msg);
        }
        return Result.success();
    }
}


