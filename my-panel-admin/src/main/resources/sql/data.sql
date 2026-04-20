INSERT IGNORE INTO `sys_config` VALUES (1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', '2026-02-07 14:13:10.0', '', NULL, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),
       (2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', '2026-02-07 14:13:10.0', '', NULL, '初始化密码 123456'),
       (3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', '2026-02-07 14:13:10.0', '', NULL, '深色主题theme-dark，浅色主题theme-light'),
       (4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', 'admin', '2026-02-07 14:13:10.0', '', NULL, '是否开启验证码功能（true开启，false关闭）'),
       (5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', 'admin', '2026-02-07 14:13:10.0', '', NULL, '是否开启注册用户功能（true开启，false关闭）'),
       (6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', 'admin', '2026-02-07 14:13:10.0', '', NULL, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）'),
       (7, '系统监控-是否显示Swagger文档', 'sys.monitor.showSwagger', 'false', 'Y', 'admin', '2026-02-12 14:13:10.0', '', NULL, '是否在服务监控页面显示Swagger文档按钮（true显示，false隐藏）'),
       (8, '系统监控-是否显示Actuator监控', 'sys.monitor.showActuator', 'false', 'Y', 'admin', '2026-02-12 14:13:10.0', '', NULL, '是否在服务监控页面显示Actuator监控按钮（true显示，false隐藏）');

INSERT IGNORE INTO `sys_dept` VALUES (100, 0, '0', '总部', 0, '刘邦', '15888888888', 'liubang@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-07 18:18:01.0'),
       (101, 100, '0,100', '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (102, 100, '0,100', '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (103, 101, '0,100,101', '研发部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (104, 101, '0,100,101', '市场部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (105, 101, '0,100,101', '测试部门', 3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (106, 101, '0,100,101', '财务部门', 4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (107, 101, '0,100,101', '运维部门', 5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (108, 102, '0,100,102', '市场部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (109, 102, '0,100,102', '财务部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL),
       (200, 100, '0,100', '上海分公司', 5, NULL, NULL, NULL, '0', '0', 'admin', '2026-02-07 18:18:26.0', '', NULL);

INSERT IGNORE INTO `sys_dict_data` VALUES (1, 1, '男', '0', 'sys_user_sex', '', '', 'Y', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '性别男'),
       (2, 2, '女', '1', 'sys_user_sex', '', '', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '性别女'),
       (3, 3, '未知', '2', 'sys_user_sex', '', '', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '性别未知'),
       (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '显示菜单'),
       (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '隐藏菜单'),
       (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '正常状态'),
       (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '停用状态'),
       (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '正常状态'),
       (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '停用状态'),
       (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '默认分组'),
       (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '系统分组'),
       (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '系统默认是'),
       (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '系统默认否'),
       (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '通知'),
       (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '公告'),
       (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '正常状态'),
       (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '关闭状态'),
       (18, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '其他操作'),
       (19, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '新增操作'),
       (20, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '修改操作'),
       (21, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '删除操作'),
       (22, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '授权操作'),
       (23, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '导出操作'),
       (24, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '导入操作'),
       (25, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '强退操作'),
       (26, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '生成操作'),
       (27, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '清空操作'),
       (28, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '正常状态'),
       (29, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '停用状态'),
       (100, 1, 'Windows', '0', 'agent_os_type', NULL, NULL, 'N', '0', 'admin', '2026-03-27 16:28:10.339291', '', NULL, NULL),
       (101, 2, 'Linux', '1', 'agent_os_type', NULL, NULL, 'N', '0', 'admin', '2026-03-27 16:28:29.980307', '', NULL, NULL),
       (102, 3, 'Mac', '2', 'agent_os_type', NULL, NULL, 'N', '0', 'admin', '2026-03-27 16:28:53.705331', '', NULL, NULL),
       (103, 4, '其他系统', '3', 'agent_os_type', NULL, NULL, 'N', '0', 'admin', '2026-03-27 16:29:44.779131', 'admin', '2026-03-27 16:33:34.82214', NULL),
       (104, 1, '启用', '0', 'agent_node_switch', NULL, NULL, 'N', '0', 'admin', '2026-03-27 16:35:33.036305', '', NULL, NULL),
       (105, 2, '临时关闭', '1', 'agent_node_switch', NULL, NULL, 'N', '0', 'admin', '2026-03-27 16:36:50.903384', 'admin', '2026-03-27 16:36:58.52115', NULL),
       (106, 3, '注销', '2', 'agent_node_switch', NULL, NULL, 'N', '0', 'admin', '2026-03-27 16:37:18.98585', '', NULL, NULL);

INSERT IGNORE INTO `sys_dict_type` VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '用户性别列表'),
       (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '菜单状态列表'),
       (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '系统开关列表'),
       (4, '任务状态', 'sys_job_status', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '任务状态列表'),
       (5, '任务分组', 'sys_job_group', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '任务分组列表'),
       (6, '系统是否', 'sys_yes_no', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '系统是否列表'),
       (7, '通知类型', 'sys_notice_type', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '通知类型列表'),
       (8, '通知状态', 'sys_notice_status', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '通知状态列表'),
       (9, '操作类型', 'sys_oper_type', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '操作类型列表'),
       (10, '系统状态', 'sys_common_status', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '登录状态列表'),
       (100, 'Agent所在操作系统', 'agent_os_type', '0', 'admin', '2026-03-27 16:07:18.471015', '', NULL, 'Agent所在操作系统列表'),
       (132, 'Agent节点开关', 'agent_node_switch', '0', 'admin', '2026-03-27 16:34:36.442403', '', NULL, 'Agent节点开关列表');

INSERT IGNORE INTO `sys_job` (
    `job_id`,
    `job_name`,
    `job_group`,
    `invoke_target`,
    `job_type`,
    `method_name`,
    `http_url`,
    `http_method`,
    `http_headers`,
    `http_body`,
    `load_balance_strategy`,
    `script_name`,
    `script_type`,
    `script_content`,
    `cron_expression`,
    `misfire_policy`,
    `concurrent`,
    `status`,
    `create_by`,
    `create_time`,
    `update_by`,
    `update_time`,
    `remark`
) VALUES
       (1, '备份系统初始化数据表', 'SYSTEM', 'appTask.backupSystemAllTable', 1, 'appTask.backupSystemAllTable', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '0/10 * * * * ?', '3', '1', '1', 'admin', '2026-02-07 14:13:10.0', 'admin', '2026-02-13 10:13:34.873309', '备份系统初始化数据表'),
       (2, '调用内置方法（有参）', 'DEFAULT', 'appTask.runSingleParam("hello")', 1, 'appTask.runSingleParam("hello")', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '0/15 * * * * ?', '3', '1', '1', 'admin', '2026-02-07 14:13:10.0', 'admin', '2026-02-08 21:09:32.0', '调用内置方法（有参）'),
       (3, '调用内置方法（多参）', 'DEFAULT', 'appTask.runMultipleParams("hello", true, 2000, 316.50, 100)', 1, 'appTask.runMultipleParams("hello", true, 2000, 316.50, 100)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '0/20 * * * * ?', '3', '1', '1', 'admin', '2026-02-07 14:13:10.0', 'admin', '2026-02-08 21:09:14.0', '调用内置方法（有参）'),
       (4, '扫描并下线超时节点', 'SYSTEM', 'rcNodeTask.scanOfflineNodes(60)', 1, 'rcNodeTask.scanOfflineNodes(60)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '0/15 * * * * ?', '3', '1', '1', 'admin', '2026-02-13 11:00:00.0', 'admin', '2026-02-13 11:00:00.0', '扫描注册中心表中超时未刷新的节点并设为下线状态'),
       (5, '扫描并下线Agent超时节点', 'SYSTEM', 'agentTask.scanOfflineAgents(60)', 1, 'agentTask.scanOfflineAgents(60)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '0/15 * * * * ?', '3', '1', '1', 'admin', '2026-03-30 00:00:00.0', 'admin', '2026-03-30 00:00:00.0', '扫描Agent注册表中超时未更新的节点并设为下线状态'),
       (6, 'HTTP接口调度示例', 'TEST', 'http://localhost:8888/test/user/1', 2, NULL, 'http://localhost:8888/test/user/1', 'GET', '{"Content-Type": "application/json"}', '{"id": "{jobId}", "name": "test"}', 'roundRobin', NULL, NULL, NULL, '0/15 * * * * ?', '3', '1', '1', 'admin', '2026-02-07 14:13:10.0', 'admin', '2026-02-08 21:09:32.0', '演示HTTP接口调度'),
       (7, '测试用户列表', 'TEST', 'http://localhost:8888/test/user/list', 2, NULL, 'http://localhost:8888/test/user/list', 'GET', '{"Content-Type": "application/json"}', NULL, 'roundRobin', NULL, NULL, NULL, '0/30 * * * * ?', '3', '1', '1', 'admin', '2026-04-04 10:00:00.0', 'admin', '2026-04-04 10:00:00.0', '测试获取用户列表接口'),
       (8, '测试用户详情', 'TEST', 'http://localhost:8888/test/user/2', 2, NULL, 'http://localhost:8888/test/user/2', 'GET', '{"Content-Type": "application/json"}', '{"userId": "{jobId}"}', 'roundRobin', NULL, NULL, NULL, '0/30 * * * * ?', '3', '1', '1', 'admin', '2026-04-04 10:00:00.0', 'admin', '2026-04-04 10:00:00.0', '测试获取用户详情接口'),
       (9, '测试用户新增', 'TEST', 'http://localhost:8888/test/user/save', 2, NULL, 'http://localhost:8888/test/user/save', 'POST', '{"Content-Type": "application/json"}', '{"userId": "{jobId}", "username": "test", "password": "123456", "mobile": "13800138000"}', 'roundRobin', NULL, NULL, NULL, '0/30 * * * * ?', '3', '1', '1', 'admin', '2026-04-04 10:00:00.0', 'admin', '2026-04-04 10:00:00.0', '测试新增用户接口'),
       (10, '测试用户更新', 'TEST', 'http://localhost:8888/test/user/update', 2, NULL, 'http://localhost:8888/test/user/update', 'PUT', '{"Content-Type": "application/json"}', '{"userId": "{jobId}", "username": "test", "password": "123456", "mobile": "13800138000"}', 'roundRobin', NULL, NULL, NULL, '0/30 * * * * ?', '3', '1', '1', 'admin', '2026-04-04 10:00:00.0', 'admin', '2026-04-04 10:00:00.0', '测试更新用户接口'),
       (11, '测试用户删除', 'TEST', 'http://localhost:8888/test/user/3', 2, NULL, 'http://localhost:8888/test/user/3', 'DELETE', '{"Content-Type": "application/json"}', '{"userId": "{jobId}"}', 'roundRobin', NULL, NULL, NULL, '0/30 * * * * ?', '3', '1', '1', 'admin', '2026-04-04 10:00:00.0', 'admin', '2026-04-04 10:00:00.0', '测试删除用户接口'),
       (12, 'Linux系统测试脚本', 'TEST', 'Linux系统测试脚本', 3, NULL, NULL, NULL, NULL, NULL, NULL, 'Linux系统测试脚本', 'shell', 'echo "Linux backup task started at $(date)" && read -p "按Enter键继续..."', '0 0 2 * * ?', '3', '1', '1', 'admin', '2026-04-05 10:00:00.0', 'admin', '2026-04-05 10:00:00.0', 'Linux测试脚本示例'),
       (13, 'Windows系统测试脚本', 'TEST', 'Windows系统测试脚本', 3, NULL, NULL, NULL, NULL, NULL, NULL, 'Windows系统测试脚本', 'powershell', 'Add-Type -AssemblyName PresentationFramework; [System.Windows.MessageBox]::Show("Windows cleanup task started", "Task Script", "OK", "Information")', '0 0 3 * * ?', '3', '1', '1', 'admin', '2026-04-05 10:00:00.0', 'admin', '2026-04-05 10:00:00.0', 'Windows测试脚本示例'),
       (14, 'python测试脚本', 'TEST', 'python测试脚本', 3, NULL, NULL, NULL, NULL, NULL, NULL, 'python测试脚本', 'python', 'import tkinter.messagebox as msgbox; msgbox.showinfo("测试弹框", "Python测试任务已启动")', '0 0 4 * * ?', '3', '1', '1', 'admin', '2026-04-05 10:00:00.0', 'admin', '2026-04-05 10:00:00.0', 'python测试脚本示例');

INSERT IGNORE INTO `sys_menu` VALUES
        (1, '系统管理', 0, 2, 'system', NULL, '', '', 1, 0, 'M', '0', '0', '', 'SettingOutlined', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-07 18:11:52.0', '系统管理目录'),
        (2, '系统监控', 0, 3, 'monitor', NULL, '', '', 1, 0, 'M', '0', '0', '', 'DashboardOutlined', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-07 18:12:03.0', '系统监控目录'),
        (3, '注册配置中心', 0, 4, 'registry-config-center', NULL, '', '', 1, 0, 'M', '0', '0', '', 'ToolOutlined', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-13 09:48:49.545433', '注册配置中心目录'),
        (4, '首页', 0, 1, 'index', 'home/index', '', '', 1, 0, 'M', '0', '0', '', 'HomeTwoTone', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-08 22:58:26.0', '若依官网地址'),
        (100, '用户管理', 1, 1, 'user', 'system/user/index', '', '', 1, 0, 'C', '0', '0', 'system:user:list', 'user', 'admin', '2026-02-07 14:13:09.0', '', NULL, '用户管理菜单'),
        (101, '角色管理', 1, 2, 'role', 'system/role/index', '', '', 1, 0, 'C', '0', '0', 'system:role:list', 'peoples', 'admin', '2026-02-07 14:13:09.0', '', NULL, '角色管理菜单'),
        (102, '菜单管理', 1, 3, 'menu', 'system/menu/index', '', '', 1, 0, 'C', '0', '0', 'system:menu:list', 'tree-table', 'admin', '2026-02-07 14:13:09.0', '', NULL, '菜单管理菜单'),
        (103, '部门管理', 1, 4, 'dept', 'system/dept/index', '', '', 1, 0, 'C', '0', '0', 'system:dept:list', 'tree', 'admin', '2026-02-07 14:13:09.0', '', NULL, '部门管理菜单'),
        (104, '岗位管理', 1, 5, 'post', 'system/post/index', '', '', 1, 0, 'C', '0', '0', 'system:post:list', 'post', 'admin', '2026-02-07 14:13:09.0', '', NULL, '岗位管理菜单'),
        (105, '字典管理', 1, 6, 'dict', 'system/dict/index', '', '', 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', '2026-02-07 14:13:09.0', '', NULL, '字典管理菜单'),
        (106, '参数设置', 1, 7, 'config', 'system/config/index', '', '', 1, 0, 'C', '0', '0', 'system:config:list', 'edit', 'admin', '2026-02-07 14:13:09.0', '', NULL, '参数设置菜单'),
        (107, '通知公告', 1, 8, 'notice', 'system/notice/index', '', '', 1, 0, 'C', '0', '0', 'system:notice:list', 'NotificationOutlined', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-08 22:58:16.0', '通知公告菜单'),
        (108, '日志管理', 2, 9, 'log', '', '', '', 1, 0, 'M', '0', '0', '', 'log', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-07 17:42:08.0', '日志管理菜单'),
        (109, '在线用户', 2, 1, 'online', 'monitor/online/index', '', '', 1, 0, 'C', '0', '0', 'monitor:online:list', 'online', 'admin', '2026-02-07 14:13:09.0', '', NULL, '在线用户菜单'),
        (110, '定时任务', 2, 2, 'job', 'monitor/job/index', '', '', 1, 0, 'C', '0', '0', 'monitor:job:list', 'job', 'admin', '2026-02-07 14:13:09.0', '', NULL, '定时任务菜单'),
        (111, '数据监控', 2, 3, 'druid', 'monitor/druid/index', '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list', 'druid', 'admin', '2026-02-07 14:13:09.0', '', NULL, '数据监控菜单'),
        (112, '服务监控', 2, 4, 'server', 'monitor/server/index', '', '', 1, 0, 'C', '0', '0', 'monitor:server:list', 'server', 'admin', '2026-02-07 14:13:09.0', '', NULL, '服务监控菜单'),
        (113, '缓存监控', 2, 5, 'cache', 'monitor/cache/index', '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis', 'admin', '2026-02-07 14:13:09.0', '', NULL, '缓存监控菜单'),
        (114, '缓存列表', 2, 6, 'cacheList', 'monitor/cacheList/index', '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis-list', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-08 16:46:53.0', '缓存列表菜单'),
        (115, '配置中心', 3, 2, 'config-center', 'rc/configCenter/index', '', '', 1, 0, 'C', '0', '0', 'rc:configCenter:list', 'FileFilled', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-03-27 18:46:31.008615', '表单构建菜单'),
        (116, '应用管理', 3, 4, 'app-manage', 'rc/appManage/index', '', '', 1, 0, 'C', '0', '0', 'rc:appManage:list', 'code', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-03-27 18:46:31.011183', '代码生成菜单'),
        (117, '注册中心', 3, 1, 'swagger', 'rc/registryCenter/index', '', '', 1, 0, 'C', '0', '0', 'rc:registryCenter:list', 'PhoneTwoTone', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-03-27 18:46:31.007147', '系统接口菜单'),
        (500, '操作日志', 108, 2, 'operlog', 'monitor/operlog/index', '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list', 'form', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-03-27 18:47:07.34962', '操作日志菜单'),
        (501, '登录日志', 108, 1, 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-03-27 18:47:07.34759', '登录日志菜单'),
        (1000, '用户查询', 100, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1001, '用户新增', 100, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1002, '用户修改', 100, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1003, '用户删除', 100, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1004, '用户导出', 100, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1005, '用户导入', 100, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1006, '重置密码', 100, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1007, '角色查询', 101, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1008, '角色新增', 101, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1009, '角色修改', 101, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1010, '角色删除', 101, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1011, '角色导出', 101, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1012, '菜单查询', 102, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1013, '菜单新增', 102, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1014, '菜单修改', 102, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1015, '菜单删除', 102, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1016, '部门查询', 103, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1017, '部门新增', 103, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1018, '部门修改', 103, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1019, '部门删除', 103, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1020, '岗位查询', 104, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1021, '岗位新增', 104, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1022, '岗位修改', 104, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1023, '岗位删除', 104, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1024, '岗位导出', 104, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1025, '字典查询', 105, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1026, '字典新增', 105, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1027, '字典修改', 105, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1028, '字典删除', 105, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1029, '字典导出', 105, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1030, '参数查询', 106, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1031, '参数新增', 106, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1032, '参数修改', 106, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1033, '参数删除', 106, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1034, '参数导出', 106, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1035, '公告查询', 107, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1036, '公告新增', 107, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1037, '公告修改', 107, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1038, '公告删除', 107, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1039, '操作查询', 500, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1040, '操作删除', 500, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1041, '日志导出', 500, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1042, '登录查询', 501, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1043, '登录删除', 501, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1044, '日志导出', 501, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1045, '账户解锁', 501, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1046, '在线查询', 109, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1047, '批量强退', 109, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1048, '单条强退', 109, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1049, '任务查询', 110, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1050, '任务新增', 110, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1051, '任务修改', 110, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1052, '任务删除', 110, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1053, '状态修改', 110, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1054, '任务导出', 110, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export', '#', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
        (1500, '运维管理', 0, 5, 'op', '', '', '', 1, 0, 'M', '0', '0', '', 'ToolOutlined', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-03-26 15:32:27.562145', '运维管理目录'),
        (2000, '环境管理', 3, 3, 'env-manage', 'rc/envManage/index', NULL, '', 1, 0, 'C', '0', '0', 'rc:envManage:list', 'EnvironmentTwoTone', 'admin', '2026-02-13 09:56:15.041545', 'admin', '2026-03-27 18:46:31.008615', ''),
        (2001, 'AccessToken', 3, 5, 'access-token', 'rc/accessToken/index', NULL, '', 1, 0, 'C', '0', '0', 'rc:accessToken:list', 'KeyOutlined', 'admin', '2026-02-13 10:12:09.691179', 'admin', '2026-03-27 18:46:31.01306', ''),
        (2002, 'Agent管理', 1500, 1, 'agentManage', 'op/agentManage/index', NULL, '', 1, 0, 'C', '0', '0', 'op:agentManage:list', 'CreditCardOutlined', 'admin', '2026-03-26 15:34:13.105463', '', NULL, ''),
        (2003, 'Agent新增', 2002, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'op:agentManage:add', '#', 'admin', '2026-03-26 15:34:13.105463', '', NULL, ''),
        (2004, 'Agent修改', 2002, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'op:agentManage:edit', '#', 'admin', '2026-03-26 15:34:13.105463', '', NULL, ''),
        (2005, 'Agent删除', 2002, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'op:agentManage:remove', '#', 'admin', '2026-03-26 15:34:13.105463', '', NULL, ''),
        (2006, 'Agent导出', 2002, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'op:agentManage:export', '#', 'admin', '2026-03-26 15:34:13.105463', '', NULL, ''),
        (2007, 'Agent下线', 2002, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'op:agentManage:offline', '#', 'admin', '2026-03-26 15:34:13.105463', '', NULL, ''),
        (2008, 'Agent命令历史', 1500, 2, 'command-history', 'op/commandHistory/index', NULL, '', 1, 0, 'C', '0', '0', 'agent:registry:list', 'HistoryOutlined', 'admin', NOW(), '', NULL, 'Agent命令执行历史'),
        (2009, '命令历史查询', 2008, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'agent:registry:query', '#', 'admin', NOW(), '', NULL, ''),
        (2010, '命令历史删除', 2008, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'agent:registry:remove', '#', 'admin', NOW(), '', NULL, ''),
        (2011, '命令历史导出', 2008, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'agent:registry:export', '#', 'admin', NOW(), '', NULL, ''),
        (2100, '架构编排', 0, 6, 'architecture', NULL, '', '', 1, 0, 'M', '0', '0', '', 'ApartmentOutlined', 'admin', NOW(), '', NULL, '架构编排目录'),
        (2101, '架构图管理', 2100, 1, 'arch-diagram', 'arch/architectureList/index', '', '', 1, 0, 'C', '0', '0', 'arch:diagram:list', 'diagram', 'admin', NOW(), '', NULL, '架构图管理菜单'),
        (2102, '节点类型管理', 2100, 2, 'arch-node-type', 'arch/architectureNodeType/index', '', '', 1, 0, 'C', '0', '0', 'arch:nodeType:list', 'nodeType', 'admin', NOW(), '', NULL, '节点类型管理菜单'),
        (2104, '架构图查询', 2101, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:diagram:query', '#', 'admin', NOW(), '', NULL, ''),
        (2105, '架构图新增', 2101, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:diagram:add', '#', 'admin', NOW(), '', NULL, ''),
        (2106, '架构图修改', 2101, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:diagram:edit', '#', 'admin', NOW(), '', NULL, ''),
        (2107, '架构图删除', 2101, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:diagram:remove', '#', 'admin', NOW(), '', NULL, ''),
        (2108, '架构图发布', 2101, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:diagram:publish', '#', 'admin', NOW(), '', NULL, ''),
        (2109, '架构图导出', 2101, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:diagram:export', '#', 'admin', NOW(), '', NULL, ''),
        (2110, '架构图导入', 2101, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:diagram:import', '#', 'admin', NOW(), '', NULL, ''),
        (2111, '节点类型查询', 2102, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:nodeType:query', '#', 'admin', NOW(), '', NULL, ''),
        (2112, '节点类型新增', 2102, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:nodeType:add', '#', 'admin', NOW(), '', NULL, ''),
        (2113, '节点类型修改', 2102, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:nodeType:edit', '#', 'admin', NOW(), '', NULL, ''),
        (2114, '节点类型删除', 2102, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:nodeType:remove', '#', 'admin', NOW(), '', NULL, ''),
        (2103, '节点标签管理', 2100, 3, 'arch-tag', 'arch/architectureTag/index', '', '', 1, 0, 'C', '0', '0', 'arch:tag:list', 'tag', 'admin', NOW(), '', NULL, '节点标签管理菜单'),
        (2115, '标签查询', 2103, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:tag:query', '#', 'admin', NOW(), '', NULL, ''),
        (2116, '标签新增', 2103, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:tag:add', '#', 'admin', NOW(), '', NULL, ''),
        (2117, '标签修改', 2103, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:tag:edit', '#', 'admin', NOW(), '', NULL, ''),
        (2118, '标签删除', 2103, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'arch:tag:remove', '#', 'admin', NOW(), '', NULL, '');


INSERT IGNORE INTO `sys_notice` VALUES (1, '温馨提醒：2018-07-01 若依新版本发布啦', '2', X'e696b0e78988e69cace58685e5aeb9', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '管理员'),
       (2, '维护通知：2018-07-01 若依系统凌晨维护', '1', X'e7bbb4e68aa4e58685e5aeb9', '0', 'admin', '2026-02-07 14:13:10.0', '', NULL, '管理员');

INSERT IGNORE INTO `sys_post` VALUES (1, 'ceo', '董事长', 1, '0', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
       (2, 'se', '项目经理', 2, '0', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
       (3, 'hr', '人力资源', 3, '0', 'admin', '2026-02-07 14:13:09.0', '', NULL, ''),
       (4, 'user', '普通员工', 4, '0', 'admin', '2026-02-07 14:13:09.0', '', NULL, '');

INSERT IGNORE INTO `sys_role` VALUES (1, '超级管理员', 'admin', 1, '1', 1, 1, '0', '0', 'admin', '2026-02-07 14:13:09.0', '', NULL, '超级管理员'),
       (2, '普通角色', 'common', 2, '2', 0, 0, '0', '0', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-07 18:45:51.0', '普通角色');

INSERT IGNORE INTO `sys_role_dept` VALUES (2, 100),
       (2, 101),
       (2, 105);

INSERT IGNORE INTO `sys_role_menu` VALUES (2, 1),
       (2, 4),
       (2, 100),
       (2, 101),
       (2, 102),
       (2, 103),
       (2, 104),
       (2, 105),
       (2, 106),
       (2, 107),
       (2, 1000),
       (2, 1001),
       (2, 1002),
       (2, 1003),
       (2, 1004),
       (2, 1005),
       (2, 1006),
       (2, 1007),
       (2, 1008),
       (2, 1009),
       (2, 1010),
       (2, 1011),
       (2, 1012),
       (2, 1013),
       (2, 1014),
       (2, 1015),
       (2, 1016),
       (2, 1020),
       (2, 1022),
       (2, 1023),
       (2, 1024),
       (2, 1025),
       (2, 1026),
       (2, 1027),
       (2, 1028),
       (2, 1029),
       (2, 1030),
       (2, 1031),
       (2, 1032),
       (2, 1033),
       (2, 1034),
       (2, 1035),
       (2, 1036),
       (2, 1037),
       (2, 1038);

INSERT IGNORE INTO `sys_user` VALUES (1, 103, 'admin', 'sa', '00', 'sa666@163.com', '15888888888', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', '2026-02-13 10:07:13.628', 'admin', '2026-02-07 14:13:09.0', '', '2026-02-13 10:07:13.639739', '管理员'),
       (2, 105, 'guest', 'guest', '00', 'guest@qq.com', '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', '2026-02-07 14:13:09.0', 'admin', '2026-02-07 14:13:09.0', 'admin', '2026-02-07 18:25:59.0', '游客'),
       (100, 109, 'cq', 'cq', '00', '1111@qq.com', '18325567876', '0', '', '$2a$10$I2yU3XQi/00Dea2MTx9DReoLAJ.2cS.Seao30cd9y2CQMYjBG4ja2', '0', '0', '127.0.0.1', '2026-02-07 18:47:01.0', 'admin', '2026-02-07 14:29:00.0', 'admin', '2026-02-07 18:47:00.0', '测试员');

INSERT IGNORE INTO `sys_user_post` VALUES (1, 1),
       (2, 2),
       (100, 2);

INSERT IGNORE INTO `sys_user_role` VALUES (1, 1),
       (2, 2),
       (100, 2);

-- ----------------------------
-- XXL-Conf 初始化数据
-- ----------------------------
-- 1. 默认环境
INSERT IGNORE INTO `rc_env` (`id`, `env_name`, `env_desc`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES (1, 'default', '默认环境', 'admin', NOW(), 'admin', NOW());

-- 2. 默认AccessToken
INSERT IGNORE INTO `rc_access_token` (`id`, `token_value`, `token_desc`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES (1, 'default_token_123456', '系统内置默认应用my-panel-agent的token', '0', 'admin', NOW(), 'admin', NOW());

-- 3. 默认应用
INSERT IGNORE INTO `rc_project` (`id`, `project_name`, `project_desc`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
(1, 'my-panel-agent', '系统内置默认应用，my-panel-agent', 'admin', NOW(), 'admin', NOW()),
(2, 'proxy-service', '系统内置默认应用，proxy-service', 'admin', NOW(), 'admin', NOW());

-- ----------------------------
-- 架构编排页面预置数据
-- ----------------------------

-- 1. 字典类型：架构图状态
INSERT IGNORE INTO `sys_dict_type` VALUES (200, '架构图状态', 'arch_diagram_status', '0', 'admin', NOW(), '', NULL, '架构图状态列表');

-- 2. 字典数据：架构图状态
INSERT IGNORE INTO `sys_dict_data` VALUES (200, 1, '草稿', '0', 'arch_diagram_status', '', 'default', 'N', '0', 'admin', NOW(), '', NULL, '草稿状态'),
       (201, 2, '已发布', '1', 'arch_diagram_status', '', 'success', 'N', '0', 'admin', NOW(), '', NULL, '已发布状态'),
       (202, 3, '已归档', '2', 'arch_diagram_status', '', 'info', 'N', '0', 'admin', NOW(), '', NULL, '已归档状态');

-- 3. 字典类型：节点分类
INSERT IGNORE INTO `sys_dict_type` VALUES (201, '节点分类', 'arch_node_category', '0', 'admin', NOW(), '', NULL, '节点分类列表');

-- 4. 字典数据：节点分类
INSERT IGNORE INTO `sys_dict_data` VALUES (203, 1, '基础设施', 'infrastructure', 'arch_node_category', '', 'primary', 'N', '0', 'admin', NOW(), '', NULL, '基础设施分类'),
       (204, 2, '中间件', 'middleware', 'arch_node_category', '', 'warning', 'N', '0', 'admin', NOW(), '', NULL, '中间件分类'),
       (205, 3, '应用服务', 'application', 'arch_node_category', '', 'success', 'N', '0', 'admin', NOW(), '', NULL, '应用服务分类'),
       (206, 4, '数据库', 'database', 'arch_node_category', '', 'danger', 'N', '0', 'admin', NOW(), '', NULL, '数据库分类'),
       (207, 5, '缓存', 'cache', 'arch_node_category', '', 'info', 'N', '0', 'admin', NOW(), '', NULL, '缓存分类'),
       (208, 6, '消息队列', 'mq', 'arch_node_category', '', 'purple', 'N', '0', 'admin', NOW(), '', NULL, '消息队列分类'),
       (209, 7, '存储', 'storage', 'arch_node_category', '', 'cyan', 'N', '0', 'admin', NOW(), '', NULL, '存储分类'),
       (210, 8, '网络', 'network', 'arch_node_category', '', 'orange', 'N', '0', 'admin', NOW(), '', NULL, '网络分类'),
       (211, 9, '安全', 'security', 'arch_node_category', '', 'red', 'N', '0', 'admin', NOW(), '', NULL, '安全分类'),
       (212, 10, '监控', 'monitor', 'arch_node_category', '', 'blue', 'N', '0', 'admin', NOW(), '', NULL, '监控分类'),
       (223, 11, '逻辑', 'logic', 'arch_node_category', '', 'geekblue', 'N', '0', 'admin', NOW(), '', NULL, '逻辑形状分类'),
       (224, 12, '数学运算', 'math', 'arch_node_category', '', 'lime', 'N', '0', 'admin', NOW(), '', NULL, '数学运算分类');

-- 5. 字典类型：边缘类型
INSERT IGNORE INTO `sys_dict_type` VALUES (202, '边缘类型', 'arch_edge_type', '0', 'admin', NOW(), '', NULL, '边缘类型列表');

-- 6. 字典数据：边缘类型
INSERT IGNORE INTO `sys_dict_data` VALUES (213, 1, '默认实线', 'default', 'arch_edge_type', '', 'primary', 'Y', '0', 'admin', NOW(), '', NULL, '默认实线'),
       (214, 2, '虚线', 'dashed', 'arch_edge_type', '', 'warning', 'N', '0', 'admin', NOW(), '', NULL, '虚线'),
       (215, 3, '点线', 'dotted', 'arch_edge_type', '', 'info', 'N', '0', 'admin', NOW(), '', NULL, '点线'),
       (216, 4, '双向箭头', 'bidirectional', 'arch_edge_type', '', 'success', 'N', '0', 'admin', NOW(), '', NULL, '双向箭头'),
       (217, 5, '曲线', 'curved', 'arch_edge_type', '', 'purple', 'N', '0', 'admin', NOW(), '', NULL, '曲线');



-- 8. 架构图标签预置数据
INSERT IGNORE INTO `arch_diagram_tag` (`id`, `tag_name`, `tag_default_value`, `tag_color`, `tag_type`, `create_by`, `create_time`, `del_flag`, `remark`) VALUES
(1, '状态', '待运行',  '#1890ff', 'system', 'admin', NOW(), '0', '系统内置状态标签，例如 待运行 初始化 运行中 成功 失败 暂停等'),
(2, '进度', '0%',  '#52c41a', 'system', 'admin', NOW(), '0', '系统内置进度标签，例如0% 33% 78% 100%等');

-- 9. 节点类型预置数据
INSERT IGNORE INTO `arch_node_type` (`id`, `type_code`, `type_name`, `icon`, `category`, `default_width`, `default_height`, `default_style`, `default_properties`, `is_system`, `is_active`, `sort_order`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `remark`) VALUES
(1, 'server', '服务器', 'server', 'infrastructure', 180, 180, '{"backgroundColor":"#f0f5ff","borderColor":"#2f54eb","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"cpu":"4核","memory":"8GB","disk":"100GB","rotation":0,"width":180,"height":180}', '1', '1', 1, 'admin', NOW(), 'admin', NOW(), '0', '基础设施-服务器'),
(2, 'vm', '虚拟机', 'vm', 'infrastructure', 180, 180, '{"backgroundColor":"#f6ffed","borderColor":"#52c41a","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"cpu":"2核","memory":"4GB","disk":"50GB","rotation":0,"width":180,"height":180}', '1', '1', 2, 'admin', NOW(), 'admin', NOW(), '0', '基础设施-虚拟机'),
(3, 'container', '容器', 'container', 'infrastructure', 180, 180, '{"backgroundColor":"#e6fffb","borderColor":"#13c2c2","borderWidth":2,"borderRadius":50,"color":"#262626"}', '{"image":"nginx:latest","port":80,"rotation":0,"width":180,"height":180}', '1', '1', 3, 'admin', NOW(), 'admin', NOW(), '0', '基础设施-容器'),
(4, 'nginx', 'Nginx', 'nginx', 'middleware', 180, 180, '{"backgroundColor":"#fff7e6","borderColor":"#fa8c16","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"1.18","port":80,"workers":4,"rotation":0,"width":180,"height":180}', '1', '1', 4, 'admin', NOW(), 'admin', NOW(), '0', '中间件-Nginx'),
(5, 'redis', 'Redis', 'redis', 'cache', 180, 180, '{"backgroundColor":"#fff1f0","borderColor":"#f5222d","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"cylinder"}', '{"version":"6.0","port":6379,"maxMemory":"1GB","rotation":0,"width":180,"height":180}', '1', '1', 5, 'admin', NOW(), 'admin', NOW(), '0', '缓存-Redis'),
(6, 'mysql', 'MySQL', 'mysql', 'database', 180, 180, '{"backgroundColor":"#f0f5ff","borderColor":"#2f54eb","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"cylinder"}', '{"version":"8.0","port":3306,"charset":"utf8mb4","rotation":0,"width":180,"height":180}', '1', '1', 6, 'admin', NOW(), 'admin', NOW(), '0', '数据库-MySQL'),
(7, 'mongodb', 'MongoDB', 'mongodb', 'database', 180, 180, '{"backgroundColor":"#f6ffed","borderColor":"#52c41a","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"cylinder"}', '{"version":"4.4","port":27017,"replicas":3,"rotation":0,"width":180,"height":180}', '1', '1', 7, 'admin', NOW(), 'admin', NOW(), '0', '数据库-MongoDB'),
(8, 'rabbitmq', 'RabbitMQ', 'rabbitmq', 'mq', 180, 180, '{"backgroundColor":"#fff0f6","borderColor":"#eb2f96","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"3.8","port":5672,"managementPort":15672,"rotation":0,"width":180,"height":180}', '1', '1', 8, 'admin', NOW(), 'admin', NOW(), '0', '消息队列-RabbitMQ'),
(9, 'kafka', 'Kafka', 'kafka', 'mq', 180, 180, '{"backgroundColor":"#f9f0ff","borderColor":"#722ed1","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"2.8","port":9092,"partitions":3,"rotation":0,"width":180,"height":180}', '1', '1', 9, 'admin', NOW(), 'admin', NOW(), '0', '消息队列-Kafka'),
(10, 'springboot', 'SpringBoot', 'springboot', 'application', 180, 180, '{"backgroundColor":"#f6ffed","borderColor":"#52c41a","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"2.7","port":8080,"jvm":"-Xmx512m","rotation":0,"width":180,"height":180}', '1', '1', 10, 'admin', NOW(), 'admin', NOW(), '0', '应用服务-SpringBoot'),
(11, 'nodejs', 'Node.js', 'nodejs', 'application', 180, 180, '{"backgroundColor":"#fffbe6","borderColor":"#faad14","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"16.x","port":3000,"framework":"Express","rotation":0,"width":180,"height":180}', '1', '1', 11, 'admin', NOW(), 'admin', NOW(), '0', '应用服务-Node.js'),
(12, 'python', 'Python', 'python', 'application', 180, 180, '{"backgroundColor":"#e6fffb","borderColor":"#13c2c2","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"3.9","port":5000,"framework":"Flask","rotation":0,"width":180,"height":180}', '1', '1', 12, 'admin', NOW(), 'admin', NOW(), '0', '应用服务-Python'),
(13, 'elasticsearch', 'Elasticsearch', 'elasticsearch', 'middleware', 180, 180, '{"backgroundColor":"#fff2e8","borderColor":"#fa541c","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"7.10","port":9200,"nodes":3,"rotation":0,"width":180,"height":180}', '1', '1', 13, 'admin', NOW(), 'admin', NOW(), '0', '中间件-Elasticsearch'),
(14, 'prometheus', 'Prometheus', 'prometheus', 'monitor', 180, 180, '{"backgroundColor":"#e6f7ff","borderColor":"#1890ff","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"2.30","port":9090,"retention":"15d","rotation":0,"width":180,"height":180}', '1', '1', 14, 'admin', NOW(), 'admin', NOW(), '0', '监控-Prometheus'),
(15, 'grafana', 'Grafana', 'grafana', 'monitor', 180, 180, '{"backgroundColor":"#fff1f0","borderColor":"#f5222d","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"8.0","port":3000,"datasources":5,"rotation":0,"width":180,"height":180}', '1', '1', 15, 'admin', NOW(), 'admin', NOW(), '0', '监控-Grafana'),
(16, 'kubernetes', 'Kubernetes', 'kubernetes', 'infrastructure', 180, 180, '{"backgroundColor":"#f0f5ff","borderColor":"#2f54eb","borderWidth":2,"borderRadius":12,"color":"#262626"}', '{"version":"1.22","nodes":5,"pods":100,"rotation":0,"width":180,"height":180}', '1', '1', 16, 'admin', NOW(), 'admin', NOW(), '0', '基础设施-Kubernetes'),
(17, 'docker', 'Docker', 'docker', 'infrastructure', 180, 180, '{"backgroundColor":"#e6f7ff","borderColor":"#1890ff","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"version":"20.10","containers":10,"images":20,"rotation":0,"width":180,"height":180}', '1', '1', 17, 'admin', NOW(), 'admin', NOW(), '0', '基础设施-Docker'),
(18, 'firewall', '防火墙', 'SafetyCertificateOutlined', 'security', 180, 180, '{"backgroundColor":"#fff1f0","borderColor":"#f5222d","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"type":"iptables","rules":50,"rotation":0,"width":180,"height":180}', '1', '1', 18, 'admin', NOW(), 'admin', NOW(), '0', '安全-防火墙'),
(19, 'loadbalancer', '负载均衡', 'ControlOutlined', 'network', 180, 180, '{"backgroundColor":"#f9f0ff","borderColor":"#722ed1","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"algorithm":"roundRobin","backends":3,"rotation":0,"width":180,"height":180}', '1', '1', 19, 'admin', NOW(), 'admin', NOW(), '0', '网络-负载均衡'),
(20, 'oss', '对象存储', 'FolderOpenOutlined', 'storage', 180, 180, '{"backgroundColor":"#e6fffb","borderColor":"#13c2c2","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"type":"MinIO","capacity":"1TB","buckets":10,"rotation":0,"width":180,"height":180}', '1', '1', 20, 'admin', NOW(), 'admin', NOW(), '0', '存储-对象存储');

-- ----------------------------
-- 13. 架构图节点类型扩展
-- ----------------------------
INSERT IGNORE INTO `arch_node_type` (`id`, `type_code`, `type_name`, `icon`, `category`, `default_width`, `default_height`, `default_style`, `default_properties`, `is_system`, `is_active`, `sort_order`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `remark`) VALUES
(21, 'my-panel', 'My-Panel后端服务', 'my-panel', 'application', 180, 180, '{"backgroundColor":"#f6ffed","borderColor":"#52c41a","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"java_version":"17","max_memory":"2G","rotation":0,"width":180,"height":180}', '1', '1', 21, 'admin', NOW(), 'admin', NOW(), '0', '应用服务-MyPanel'),
(22, 'proxy', 'Proxy代理服务', 'DeploymentUnitOutlined', 'middleware', 180, 180, '{"backgroundColor":"#fff7e6","borderColor":"#fa8c16","borderWidth":2,"borderRadius":8,"color":"#262626"}', '{"max_connections":1000,"rotation":0,"width":180,"height":180}', '1', '1', 22, 'admin', NOW(), 'admin', NOW(), '0', '中间件-Proxy'),
(23, 'logic-and', '逻辑与 (AND)', 'ApartmentOutlined', 'logic', 180, 180, '{"backgroundColor":"#f5f5f5","borderColor":"#8c8c8c","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"logic-and"}', '{"rotation":0,"width":180,"height":180}', '1', '1', 23, 'admin', NOW(), 'admin', NOW(), '0', '逻辑形状-与门'),
(24, 'logic-or', '逻辑或 (OR)', 'ApartmentOutlined', 'logic', 180, 180, '{"backgroundColor":"#f5f5f5","borderColor":"#8c8c8c","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"logic-or"}', '{"rotation":0,"width":180,"height":180}', '1', '1', 24, 'admin', NOW(), 'admin', NOW(), '0', '逻辑形状-或门'),
(25, 'logic-not', '逻辑非 (NOT)', 'ApartmentOutlined', 'logic', 180, 180, '{"backgroundColor":"#f5f5f5","borderColor":"#8c8c8c","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"logic-not"}', '{"rotation":0,"width":180,"height":180}', '1', '1', 25, 'admin', NOW(), 'admin', NOW(), '0', '逻辑形状-非门'),
(26, 'math-add', '加法运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#f6ffed","borderColor":"#52c41a","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"circle"}', '{"operation":"+","rotation":0,"width":180,"height":180}', '1', '1', 26, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-加法'),
(27, 'math-subtract', '减法运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#fff7e6","borderColor":"#fa8c16","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"circle"}', '{"operation":"-","rotation":0,"width":180,"height":180}', '1', '1', 27, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-减法'),
(28, 'math-multiply', '乘法运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#e6f7ff","borderColor":"#1890ff","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"circle"}', '{"operation":"×","rotation":0,"width":180,"height":180}', '1', '1', 28, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-乘法'),
(29, 'math-divide', '除法运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#f9f0ff","borderColor":"#722ed1","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"circle"}', '{"operation":"÷","rotation":0,"width":180,"height":180}', '1', '1', 29, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-除法'),
(30, 'math-average', '求平均数', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#e6fffb","borderColor":"#13c2c2","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"hexagon"}', '{"operation":"avg","rotation":0,"width":180,"height":180}', '1', '1', 30, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-求平均数'),
(31, 'math-max', '求最大值', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#fff1f0","borderColor":"#f5222d","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"hexagon"}', '{"operation":"max","rotation":0,"width":180,"height":180}', '1', '1', 31, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-求最大值'),
(32, 'math-min', '求最小值', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#fffbe6","borderColor":"#faad14","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"hexagon"}', '{"operation":"min","rotation":0,"width":180,"height":180}', '1', '1', 32, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-求最小值'),
(33, 'math-sum', '求和运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#f0f5ff","borderColor":"#2f54eb","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"triangle"}', '{"operation":"sum","rotation":0,"width":180,"height":180}', '1', '1', 33, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-求和'),
(34, 'math-sqrt', '平方根运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#fff0f6","borderColor":"#eb2f96","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"triangle"}', '{"operation":"sqrt","rotation":0,"width":180,"height":180}', '1', '1', 34, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-平方根'),
(35, 'math-abs', '绝对值运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#f6ffed","borderColor":"#52c41a","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"triangle"}', '{"operation":"abs","rotation":0,"width":180,"height":180}', '1', '1', 35, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-绝对值'),
(36, 'math-power', '幂运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#e6f7ff","borderColor":"#1890ff","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"diamond"}', '{"operation":"^","rotation":0,"width":180,"height":180}', '1', '1', 36, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-幂运算'),
(37, 'math-mod', '取余运算', 'LineChartOutlined', 'math', 180, 180, '{"backgroundColor":"#f9f0ff","borderColor":"#722ed1","borderWidth":2,"borderRadius":8,"color":"#262626","shape":"diamond"}', '{"operation":"%","rotation":0,"width":180,"height":180}', '1', '1', 37, 'admin', NOW(), 'admin', NOW(), '0', '数学运算-取余');

-- ----------------------------
-- 14. 默认架构初始化数据
-- ----------------------------

-- 14.1 架构图主表
INSERT IGNORE INTO `arch_diagram` (`id`, `diagram_name`, `diagram_description`, `diagram_version`, `status`, `is_published`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`) VALUES
(1, '默认架构', '系统初始化的默认架构拓扑图', '1.0', '1', '1', 'admin', NOW(), 'admin', NOW(), '0');