package com.cq.panel.admin.server.web.controller.tool;

import com.cq.panel.admin.server.common.utils.MyStringUtils;
import com.cq.panel.admin.server.web.controller.base.BaseController;
import com.cq.panel.admin.server.web.domain.dto.tool.TestUserDTO;
import com.cq.panel.admin.server.web.domain.vo.base.Result;
import com.cq.panel.admin.server.web.domain.vo.tool.TestUserVO;
import com.cq.panel.admin.server.web.converter.tool.TestUserConverter;
import com.cq.panel.admin.server.web.exception.ServiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * swagger 用户测试方法
 *
 * @author cq
 */
@Tag(name = "用户信息管理", description = "测试用户CRUD接口")
@RestController
@RequestMapping("/test/user")
public class TestController extends BaseController
{
    private final TestUserConverter testUserConverter;

    private final static Map<Integer, TestUserVO> users = new LinkedHashMap<>();
    {
        TestUserVO user1 = new TestUserVO();
        user1.setUserId(1);
        user1.setUsername("admin");
        user1.setPassword("admin123");
        user1.setMobile("15888888888");
        users.put(1, user1);

        TestUserVO user2 = new TestUserVO();
        user2.setUserId(2);
        user2.setUsername("ry");
        user2.setPassword("admin123");
        user2.setMobile("15666666666");
        users.put(2, user2);
    }

    public TestController(TestUserConverter testUserConverter) {
        this.testUserConverter = testUserConverter;
    }

    @Operation(summary = "获取用户列表", description = "获取所有测试用户列表")
    @GetMapping("/list")
    public Result<List<TestUserVO>> userList()
    {
        return Result.success(new ArrayList<>(users.values()));
    }
    
    @Operation(summary = "获取用户详细", description = "根据用户ID获取详细信息")
    @GetMapping("/{userId}")
    public Result<TestUserVO> getUser(@Parameter(name = "userId", description = "用户ID", required = true, example = "1") @PathVariable(name = "userId") Integer userId)
    {
        if (!users.isEmpty() && users.containsKey(userId))
        {
            return Result.success(users.get(userId));
        }
        else
        {
            throw new ServiceException("用户不存在");
        }
    }
    
    @Operation(summary = "新增用户", description = "新增测试用户")
    @PostMapping("/save")
    public Result<Map<Integer, TestUserVO>> save(@RequestBody TestUserDTO user)
    {
        if (MyStringUtils.isNull(user) || MyStringUtils.isNull(user.getUserId()))
        {
            throw new ServiceException("用户ID不能为空");
        }
        TestUserVO userVO = testUserConverter.toVO(user);
        users.put(user.getUserId(), userVO);
        return Result.success(users);
    }
    
    @Operation(summary = "更新用户", description = "更新测试用户")
    @PutMapping("/update")
    public Result<Map<Integer, TestUserVO>> update(@RequestBody TestUserDTO user)
    {
        if (MyStringUtils.isNull(user) || MyStringUtils.isNull(user.getUserId()))
        {
            throw new ServiceException("用户ID不能为空");
        }
        if (users.isEmpty() || !users.containsKey(user.getUserId()))
        {
            throw new ServiceException("用户不存在");
        }
        users.remove(user.getUserId());
        TestUserVO userVO = testUserConverter.toVO(user);
        users.put(user.getUserId(), userVO);
        return Result.success(users);
    }
    
    @Operation(summary = "删除用户信息", description = "根据用户ID删除测试用户")
    @DeleteMapping("/{userId}")
    public Result<Void> delete(@Parameter(name = "userId", description = "用户ID", required = true, example = "1") @PathVariable(name = "userId") Integer userId)
    {
        if (!users.isEmpty() && users.containsKey(userId))
        {
            users.remove(userId);
            return Result.success();
        }
        else
        {
            throw new ServiceException("用户不存在");
        }
    }
}




