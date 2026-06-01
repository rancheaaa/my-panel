package com.cq.panel.admin.server.web.controller.op;

import com.cq.panel.admin.server.repository.service.IDirectoryCheckService;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.batch.DirectoryCheckVO;
import com.cq.panel.authlite.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "目录检测", description = "检测指定Agent节点上指定目录的存在性、读写执行权限、磁盘空间等")
@RestController
@RequestMapping("/op/dir-check")
public class OpDirCheckController extends BaseController {

    @Autowired
    private IDirectoryCheckService directoryCheckService;

    @Operation(summary = "单节点目录检测", description = "检测指定Agent节点上指定目录的存在性、读写执行权限、磁盘空间等")
    @RequirePermission("op:dirCheck:query")
    @GetMapping
    public Result<DirectoryCheckVO> checkDirectory(
            @Parameter(description = "Agent节点ID", required = true) @RequestParam String agentId,
            @Parameter(description = "要检测的目录路径", required = true) @RequestParam String dirPath) {
        return Result.success(directoryCheckService.checkSingleDirectory(agentId, dirPath));
    }
}
