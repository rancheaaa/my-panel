package com.cq.panel.admin.server.web.controller.system;

import com.cq.panel.admin.server.config.AppConfig;
import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页
 *
 * @author cq
 */
@Tag(name = "首页")
@RestController
public class SysIndexController
{
    /** 系统基础配置 */
    private final AppConfig cqConfig;

    public SysIndexController(AppConfig cqConfig) {
        this.cqConfig = cqConfig;
    }

    /**
     * 访问首页，提示语
     */
    @Operation(summary = "访问首页，提示语")
    @RequestMapping("/")
    public Result<String> index()
    {
        return Result.success(MyStringUtils.format("欢迎使用{}后台管理框架，当前版本：v{}，请通过前端地址访问。", cqConfig.getName(), cqConfig.getVersion()), null);
    }
}

