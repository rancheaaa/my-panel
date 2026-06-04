package com.cq.panel.admin.server.web.controller.batch;

import com.cq.panel.admin.server.annotation.Log;
import com.cq.panel.admin.server.common.enums.BusinessType;
import com.cq.panel.admin.server.service.batch.BatchConfigVerifyService;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.dto.batch.ConfigVerifyRequestDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.ConfigVerifyResultVO;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "配置校验", description = "Agent配置校验与推送相关接口")
@RestController
@RequestMapping("/batch/config-verify")
@Validated
public class BatchConfigVerifyController extends BaseController {

    private final BatchConfigVerifyService batchConfigVerifyService;

    public BatchConfigVerifyController(BatchConfigVerifyService batchConfigVerifyService) {
        this.batchConfigVerifyService = batchConfigVerifyService;
    }

    @Operation(summary = "校验Agent配置", description = "对比Agent端持久化的配置与服务端生成的配置是否一致")
    @Log(title = "配置校验", businessType = BusinessType.OTHER)
    @RequirePermission("batch:config:verify")
    @PostMapping("/verify")
    public Result<ConfigVerifyResultVO> verifyConfig(@Valid @RequestBody ConfigVerifyRequestDTO dto) {
        ConfigVerifyResultVO result = batchConfigVerifyService.verifyConfig(dto.getSourceAgentId());
        return Result.success(result);
    }

    @Operation(summary = "强制推送配置", description = "将服务端生成的配置强制推送到Agent端")
    @Log(title = "配置推送", businessType = BusinessType.UPDATE)
    @RequirePermission("batch:config:push")
    @PostMapping("/push")
    public Result<Integer> pushConfig(@Valid @RequestBody ConfigVerifyRequestDTO dto) {
        int filesCount = batchConfigVerifyService.pushConfig(dto.getSourceAgentId());
        return Result.success(filesCount);
    }
}
