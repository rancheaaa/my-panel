CREATE TABLE IF NOT EXISTS `gen_table`
(
    `table_id`          bigint(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
    `table_name`        varchar(200)  DEFAULT '' COMMENT '表名称',
    `table_comment`     varchar(500)  DEFAULT '' COMMENT '表描述',
    `sub_table_name`    varchar(64)   DEFAULT NULL COMMENT '关联子表的表名',
    `sub_table_fk_name` varchar(64)   DEFAULT NULL COMMENT '子表关联的外键名',
    `class_name`        varchar(100)  DEFAULT '' COMMENT '实体类名称',
    `tpl_category`      varchar(200)  DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
    `tpl_web_type`      varchar(30)   DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
    `package_name`      varchar(100)  DEFAULT NULL COMMENT '生成包路径',
    `module_name`       varchar(30)   DEFAULT NULL COMMENT '生成模块名',
    `business_name`     varchar(30)   DEFAULT NULL COMMENT '生成业务名',
    `function_name`     varchar(50)   DEFAULT NULL COMMENT '生成功能名',
    `function_author`   varchar(50)   DEFAULT NULL COMMENT '生成功能作者',
    `gen_type`          char(1)       DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
    `gen_path`          varchar(200)  DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
    `options`           varchar(1000) DEFAULT NULL COMMENT '其它生成选项',
    `create_by`         varchar(64)   DEFAULT '' COMMENT '创建者',
    `create_time`       datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`         varchar(64)   DEFAULT '' COMMENT '更新者',
    `update_time`       datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`            varchar(500)  DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成业务表';

CREATE TABLE IF NOT EXISTS `gen_table_column`
(
    `column_id`      bigint(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
    `table_id`       bigint(20) DEFAULT NULL COMMENT '归属表编号',
    `column_name`    varchar(200) DEFAULT NULL COMMENT '列名称',
    `column_comment` varchar(500) DEFAULT NULL COMMENT '列描述',
    `column_type`    varchar(100) DEFAULT NULL COMMENT '列类型',
    `java_type`      varchar(500) DEFAULT NULL COMMENT 'JAVA类型',
    `java_field`     varchar(200) DEFAULT NULL COMMENT 'JAVA字段名',
    `is_pk`          char(1)      DEFAULT NULL COMMENT '是否主键（1是）',
    `is_increment`   char(1)      DEFAULT NULL COMMENT '是否自增（1是）',
    `is_required`    char(1)      DEFAULT NULL COMMENT '是否必填（1是）',
    `is_insert`      char(1)      DEFAULT NULL COMMENT '是否为插入字段（1是）',
    `is_edit`        char(1)      DEFAULT NULL COMMENT '是否编辑字段（1是）',
    `is_list`        char(1)      DEFAULT NULL COMMENT '是否列表字段（1是）',
    `is_query`       char(1)      DEFAULT NULL COMMENT '是否查询字段（1是）',
    `query_type`     varchar(200) DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
    `html_type`      varchar(200) DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
    `dict_type`      varchar(200) DEFAULT '' COMMENT '字典类型',
    `sort`           int(11) DEFAULT NULL COMMENT '排序',
    `create_by`      varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`      varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    datetime     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`column_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成业务表字段';

CREATE TABLE IF NOT EXISTS `sys_config`
(
    `config_id`    int(5) NOT NULL AUTO_INCREMENT COMMENT '参数主键',
    `config_name`  varchar(100) DEFAULT '' COMMENT '参数名称',
    `config_key`   varchar(100) DEFAULT '' COMMENT '参数键名',
    `config_value` varchar(500) DEFAULT '' COMMENT '参数键值',
    `config_type`  char(1)      DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
    `create_by`    varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`  datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`  datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`config_id`)
)  AUTO_INCREMENT=100   COMMENT='参数配置表';

CREATE TABLE IF NOT EXISTS `sys_dept`
(
    `dept_id`     bigint(20) NOT NULL AUTO_INCREMENT COMMENT '部门id',
    `parent_id`   bigint(20) DEFAULT '0' COMMENT '父部门id',
    `ancestors`   varchar(50) DEFAULT '' COMMENT '祖级列表',
    `dept_name`   varchar(30) DEFAULT '' COMMENT '部门名称',
    `order_num`   int(4) DEFAULT '0' COMMENT '显示顺序',
    `leader`      varchar(20) DEFAULT NULL COMMENT '负责人',
    `phone`       varchar(11) DEFAULT NULL COMMENT '联系电话',
    `email`       varchar(50) DEFAULT NULL COMMENT '邮箱',
    `status`      char(1)     DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
    `del_flag`    char(1)     DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    `create_by`   varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`dept_id`)
)  AUTO_INCREMENT=201   COMMENT='部门表';

CREATE TABLE IF NOT EXISTS `sys_dict_data`
(
    `dict_code`   bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典编码',
    `dict_sort`   int(4) DEFAULT '0' COMMENT '字典排序',
    `dict_label`  varchar(100) DEFAULT '' COMMENT '字典标签',
    `dict_value`  varchar(100) DEFAULT '' COMMENT '字典键值',
    `dict_type`   varchar(100) DEFAULT '' COMMENT '字典类型',
    `css_class`   varchar(100) DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
    `list_class`  varchar(100) DEFAULT NULL COMMENT '表格回显样式',
    `is_default`  char(1)      DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
    `status`      char(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`dict_code`)
)  AUTO_INCREMENT=100   COMMENT='字典数据表';

CREATE TABLE IF NOT EXISTS `sys_dict_type`
(
    `dict_id`     bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典主键',
    `dict_name`   varchar(100) DEFAULT '' COMMENT '字典名称',
    `dict_type`   varchar(100) DEFAULT '' COMMENT '字典类型',
    `status`      char(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`dict_id`),
    UNIQUE KEY `dict_type` (`dict_type`)
)  AUTO_INCREMENT=100   COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS `sys_job`
(
    `job_id`          bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `job_name`        varchar(64)  NOT NULL DEFAULT '' COMMENT '任务名称',
    `job_group`       varchar(64)  NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
    `invoke_target`   varchar(500) NOT NULL DEFAULT '' COMMENT '调用目标字符串',
    `job_type`        tinyint(1)   NOT NULL DEFAULT 1 COMMENT '1-内置方法 2-HTTP接口 3-脚本',
    `method_name`     varchar(255)          DEFAULT NULL COMMENT '内置方法全限定名',
    `http_url`        varchar(500)          DEFAULT NULL COMMENT 'HTTP接口URL',
    `http_method`     varchar(10)           DEFAULT NULL COMMENT 'HTTP请求方式',
    `http_headers`    text                  DEFAULT NULL COMMENT 'HTTP请求头(JSON)',
    `http_body`       text                  DEFAULT NULL COMMENT 'HTTP请求体',
    `load_balance_strategy` varchar(50)      DEFAULT 'roundRobin' COMMENT '负载均衡策略（roundRobin轮询 random随机 weightedRoundRobin加权轮询 weightedRandom加权随机 leastConnections最少连接 fastestResponse最快响应 consistentHash一致性哈希 zoneAware区域感知）',
    `script_name`     varchar(255)          DEFAULT NULL COMMENT '脚本名称',
    `script_type`     varchar(20)           DEFAULT NULL COMMENT '脚本类型（python shell cmd powershell sql）',
    `script_content`   longtext              DEFAULT NULL COMMENT '脚本内容',
    `cron_expression` varchar(255)          DEFAULT '' COMMENT 'cron执行表达式',
    `misfire_policy`  varchar(20)           DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
    `concurrent`      char(1)               DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
    `status`          char(1)               DEFAULT '0' COMMENT '状态（0正常 1暂停）',
    `create_by`       varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`     datetime              DEFAULT NULL COMMENT '创建时间',
    `update_by`       varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`     datetime              DEFAULT NULL COMMENT '更新时间',
    `remark`          varchar(500)          DEFAULT '' COMMENT '备注信息',
    PRIMARY KEY (`job_id`, `job_name`, `job_group`),
    CONSTRAINT `check_job_type_fields` CHECK (
        (job_type = 1 AND method_name IS NOT NULL AND http_url IS NULL AND script_name IS NULL) OR
        (job_type = 2 AND http_url IS NOT NULL AND method_name IS NULL AND script_name IS NULL) OR
        (job_type = 3 AND script_name IS NOT NULL AND script_type IS NOT NULL AND script_content IS NOT NULL AND method_name IS NULL AND http_url IS NULL)
    )
)  AUTO_INCREMENT=100   COMMENT='定时任务调度表';

CREATE TABLE IF NOT EXISTS `sys_job_log`
(
    `job_log_id`     bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
    `job_name`       varchar(64)  NOT NULL COMMENT '任务名称',
    `job_group`      varchar(64)  NOT NULL COMMENT '任务组名',
    `invoke_target`  varchar(500) DEFAULT '' COMMENT '调用目标字符串',
    `job_message`    varchar(500)  DEFAULT NULL COMMENT '日志信息',
    `trigger_type`   char(1)       DEFAULT '0' COMMENT '触发类型（0定时触发 1手动触发）',
    `status`         char(1)       DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
    `exception_info` varchar(2000) DEFAULT '' COMMENT '异常信息',
    `start_time`     datetime      DEFAULT NULL COMMENT '开始时间',
    `end_time`       datetime      DEFAULT NULL COMMENT '结束时间',
    PRIMARY KEY (`job_log_id`)
)  AUTO_INCREMENT=1077   COMMENT='定时任务调度日志表';

CREATE TABLE IF NOT EXISTS `sys_logininfor`
(
    `info_id`        bigint(20) NOT NULL AUTO_INCREMENT COMMENT '访问ID',
    `user_name`      varchar(50)  DEFAULT '' COMMENT '用户账号',
    `ipaddr`         varchar(128) DEFAULT '' COMMENT '登录IP地址',
    `login_location` varchar(255) DEFAULT '' COMMENT '登录地点',
    `browser`        varchar(50)  DEFAULT '' COMMENT '浏览器类型',
    `os`             varchar(50)  DEFAULT '' COMMENT '操作系统',
    `status`         char(1)      DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
    `msg`            varchar(255) DEFAULT '' COMMENT '提示消息',
    `login_time`     datetime     DEFAULT NULL COMMENT '访问时间',
    PRIMARY KEY (`info_id`),
    KEY              `idx_sys_logininfor_s` (`status`),
    KEY              `idx_sys_logininfor_lt` (`login_time`)
)  AUTO_INCREMENT=181   COMMENT='系统访问记录';

CREATE TABLE IF NOT EXISTS `sys_menu`
(
    `menu_id`     bigint(20) NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `menu_name`   varchar(50) NOT NULL COMMENT '菜单名称',
    `parent_id`   bigint(20) DEFAULT '0' COMMENT '父菜单ID',
    `order_num`   int(4) DEFAULT '0' COMMENT '显示顺序',
    `path`        varchar(200) DEFAULT '' COMMENT '路由地址',
    `component`   varchar(255) DEFAULT NULL COMMENT '组件路径',
    `query`       varchar(255) DEFAULT NULL COMMENT '路由参数',
    `route_name`  varchar(50)  DEFAULT '' COMMENT '路由名称',
    `is_frame`    int(1) DEFAULT '1' COMMENT '是否为外链（0是 1否）',
    `is_cache`    int(1) DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
    `menu_type`   char(1)      DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
    `visible`     char(1)      DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
    `status`      char(1)      DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
    `perms`       varchar(100) DEFAULT NULL COMMENT '权限标识',
    `icon`        varchar(100) DEFAULT '#' COMMENT '菜单图标',
    `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`menu_id`)
)  AUTO_INCREMENT=2000   COMMENT='菜单权限表';

CREATE TABLE IF NOT EXISTS `sys_notice`
(
    `notice_id`      int(4) NOT NULL AUTO_INCREMENT COMMENT '公告ID',
    `notice_title`   varchar(50) NOT NULL COMMENT '公告标题',
    `notice_type`    char(1)     NOT NULL COMMENT '公告类型（1通知 2公告）',
    `notice_content` longblob COMMENT '公告内容',
    `status`         char(1)      DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
    `create_by`      varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`      varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`         varchar(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`notice_id`)
)  AUTO_INCREMENT=10   COMMENT='通知公告表';

CREATE TABLE IF NOT EXISTS `sys_oper_log`
(
    `oper_id`        bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    `title`          varchar(50)   DEFAULT '' COMMENT '模块标题',
    `business_type`  int(2) DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
    `method`         varchar(200)  DEFAULT '' COMMENT '方法名称',
    `request_method` varchar(10)   DEFAULT '' COMMENT '请求方式',
    `operator_type`  int(1) DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
    `oper_name`      varchar(50)   DEFAULT '' COMMENT '操作人员',
    `dept_name`      varchar(50)   DEFAULT '' COMMENT '部门名称',
    `oper_url`       varchar(255)  DEFAULT '' COMMENT '请求URL',
    `oper_ip`        varchar(128)  DEFAULT '' COMMENT '主机地址',
    `oper_location`  varchar(255)  DEFAULT '' COMMENT '操作地点',
    `oper_param`     varchar(2000) DEFAULT '' COMMENT '请求参数',
    `json_result`    varchar(2000) DEFAULT '' COMMENT '返回参数',
    `status`         int(1) DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
    `error_msg`      varchar(2000) DEFAULT '' COMMENT '错误消息',
    `oper_time`      datetime      DEFAULT NULL COMMENT '操作时间',
    `cost_time`      bigint(20) DEFAULT '0' COMMENT '消耗时间',
    PRIMARY KEY (`oper_id`),
    KEY              `idx_sys_oper_log_bt` (`business_type`),
    KEY              `idx_sys_oper_log_s` (`status`),
    KEY              `idx_sys_oper_log_ot` (`oper_time`)
)  AUTO_INCREMENT=151   COMMENT='操作日志记录';

CREATE TABLE IF NOT EXISTS `sys_post`
(
    `post_id`     bigint(20) NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
    `post_code`   varchar(64) NOT NULL COMMENT '岗位编码',
    `post_name`   varchar(50) NOT NULL COMMENT '岗位名称',
    `post_sort`   int(4) NOT NULL COMMENT '显示顺序',
    `status`      char(1)     NOT NULL COMMENT '状态（0正常 1停用）',
    `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`post_id`)
)  AUTO_INCREMENT=8   COMMENT='岗位信息表';

CREATE TABLE IF NOT EXISTS `sys_role`
(
    `role_id`             bigint(20) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name`           varchar(30)  NOT NULL COMMENT '角色名称',
    `role_key`            varchar(100) NOT NULL COMMENT '角色权限字符串',
    `role_sort`           int(4) NOT NULL COMMENT '显示顺序',
    `data_scope`          char(1)      DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
    `menu_check_strictly` tinyint(1) DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
    `dept_check_strictly` tinyint(1) DEFAULT '1' COMMENT '部门树选择项是否关联显示',
    `status`              char(1)      NOT NULL COMMENT '角色状态（0正常 1停用）',
    `del_flag`            char(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    `create_by`           varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`         datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`           varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`         datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`              varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`role_id`)
)  AUTO_INCREMENT=100   COMMENT='角色信息表';


CREATE TABLE IF NOT EXISTS `sys_role_dept`
(
    `role_id` bigint(20) NOT NULL COMMENT '角色ID',
    `dept_id` bigint(20) NOT NULL COMMENT '部门ID',
    PRIMARY KEY (`role_id`, `dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色和部门关联表';


CREATE TABLE IF NOT EXISTS `sys_role_menu`
(
    `role_id` bigint(20) NOT NULL COMMENT '角色ID',
    `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色和菜单关联表';


CREATE TABLE IF NOT EXISTS `sys_user`
(
    `user_id`     bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `dept_id`     bigint(20) DEFAULT NULL COMMENT '部门ID',
    `user_name`   varchar(30) NOT NULL COMMENT '用户账号',
    `nick_name`   varchar(30) NOT NULL COMMENT '用户昵称',
    `user_type`   varchar(2)   DEFAULT '00' COMMENT '用户类型（00系统用户）',
    `email`       varchar(50)  DEFAULT '' COMMENT '用户邮箱',
    `phonenumber` varchar(11)  DEFAULT '' COMMENT '手机号码',
    `sex`         char(1)      DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
    `avatar`      varchar(100) DEFAULT '' COMMENT '头像地址',
    `password`    varchar(100) DEFAULT '' COMMENT '密码',
    `status`      char(1)      DEFAULT '0' COMMENT '帐号状态（0正常 1停用）',
    `del_flag`    char(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    `login_ip`    varchar(128) DEFAULT '' COMMENT '最后登录IP',
    `login_date`  datetime     DEFAULT NULL COMMENT '最后登录时间',
    `create_by`   varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`user_id`)
)  AUTO_INCREMENT=101   COMMENT='用户信息表';


CREATE TABLE IF NOT EXISTS `sys_user_post`
(
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `post_id` bigint(20) NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与岗位关联表';


CREATE TABLE IF NOT EXISTS `sys_user_role`
(
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `role_id` bigint(20) NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户和角色关联表';


-- ----------------------------
-- 1. 环境管理表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rc_env` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '环境ID',
  `env_name` varchar(100) NOT NULL COMMENT '环境名称',
  `env_desc` varchar(200) DEFAULT NULL COMMENT '环境描述',
  `create_by` varchar(64) DEFAULT 'auto' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT 'auto' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境管理表';

-- ----------------------------
-- 2. 应用管理表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rc_project` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '应用ID',
  `project_name` varchar(100) NOT NULL COMMENT '应用名称',
  `project_desc` varchar(200) DEFAULT NULL COMMENT '应用描述',
  `create_by` varchar(64) DEFAULT 'auto' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT 'auto' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用管理表';

-- ----------------------------
-- 3. 配置中心表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rc_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `env_id` int(11) NOT NULL COMMENT '环境ID',
  `project_id` int(11) NOT NULL COMMENT '应用ID',
  `config_key` varchar(200) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `config_desc` varchar(200) DEFAULT NULL COMMENT '配置描述',
  `source` char(1) DEFAULT '0' COMMENT '来源（0手工新增 1批量导入）',
  `create_by` varchar(64) DEFAULT 'auto' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT 'auto' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_env_project_key` (`env_id`,`project_id`,`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置中心表';

-- ----------------------------
-- 4. AccessToken管理表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rc_access_token` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `token_value` varchar(200) NOT NULL COMMENT 'Token值',
  `token_desc` varchar(200) DEFAULT NULL COMMENT 'Token描述',
  `status` char(1) DEFAULT '0' COMMENT '状态（0启用 1禁用）',
  `create_by` varchar(64) DEFAULT 'auto' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT 'auto' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AccessToken管理表';

-- ----------------------------
-- 5. 注册中心（节点管理）表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `rc_node` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `env_id` int(11) NOT NULL COMMENT '环境ID',
  `project_id` int(11) NOT NULL COMMENT '项目ID',
  `node_ip` varchar(50) NOT NULL COMMENT '节点IP',
  `node_port` int(11) NOT NULL COMMENT '节点端口',
  `status` char(1) DEFAULT '0' COMMENT '状态（0在线 1离线）',
  `zone` varchar(50) DEFAULT 'default' COMMENT '服务所在区域',
  `last_refresh_time` datetime DEFAULT NULL COMMENT '最后刷新时间',
  `create_by` varchar(64) DEFAULT 'auto' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT 'auto' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_env_project_ip_port` (`env_id`,`project_id`,`node_ip`,`node_port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册中心（节点管理）表';

-- 创建 agent 客户端注册表
CREATE TABLE IF NOT EXISTS `agent_registry` (
    `id` varchar(64) NOT NULL COMMENT '节点ID（UUID字符串，主键）',
    `node_name` varchar(128) NOT NULL COMMENT '节点名称',
    `os_type` varchar(64) DEFAULT NULL COMMENT '所属操作系统（Linux/Windows/Mac等）',
    `app_id` varchar(64) DEFAULT NULL COMMENT '所属应用ID',
    `agent_ip` varchar(64) NOT NULL COMMENT 'Agent IP地址',
    `agent_port` int NOT NULL COMMENT 'Agent端口',
    `node_enabled` tinyint NOT NULL DEFAULt '0' COMMENT '节点是否启用：0-启用 1-临时关闭 2-永久关闭',
    `node_status` tinyint NOT NULL DEFAULT '0' COMMENT '节点状态：0-离线 1-在线 2-未知',
    `remark` varchar(512) DEFAULT NULL COMMENT '备注信息',
    `last_refresh_time` datetime DEFAULT NULL COMMENT '最后刷新时间',
    `create_by` varchar(64) DEFAULT 'auto' COMMENT '创建者',
    `update_by` varchar(64) DEFAULT 'auto' COMMENT '更新者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_node_name` (`node_name`),
    KEY `idx_agent_ip` (`agent_ip`),
    KEY `idx_app_id` (`app_id`),
    KEY `idx_node_status` (`node_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent客户端注册表';

-- ----------------------------
-- 6. 架构编排页面表结构
-- ----------------------------

-- 6.1 架构图主表
CREATE TABLE IF NOT EXISTS `arch_diagram` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '架构图ID',
    `diagram_name` varchar(200) NOT NULL COMMENT '架构图名称',
    `diagram_description` varchar(500) DEFAULT '' COMMENT '架构图描述',
    `diagram_version` varchar(50) DEFAULT '1.0' COMMENT '版本号',
    `thumbnail` longtext COMMENT '缩略图Base64或URL',
    `canvas_config` longtext COMMENT '画布配置（缩放比例、背景等）JSON',
    `status` char(1) DEFAULT '0' COMMENT '状态（0草稿 1已发布 2已归档）',
    `is_published` char(1) DEFAULT '0' COMMENT '是否已发布（0否 1是）',
    `published_at` datetime DEFAULT NULL COMMENT '发布时间',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图主表';

-- 6.2 节点类型表
CREATE TABLE IF NOT EXISTS `arch_node_type` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '类型ID',
    `type_code` varchar(100) NOT NULL COMMENT '类型编码',
    `type_name` varchar(200) NOT NULL COMMENT '类型名称',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标（SVG或图片URL）',
    `category` varchar(100) DEFAULT NULL COMMENT '分类（如：基础设施、中间件、应用服务等）',
    `default_width` int(11) DEFAULT 120 COMMENT '默认宽度',
    `default_height` int(11) DEFAULT 80 COMMENT '默认高度',
    `default_style` longtext COMMENT '默认样式模板（颜色、边框等）JSON',
    `default_properties` longtext COMMENT '默认属性模板JSON',
    `validation_rules` longtext COMMENT '属性校验规则JSON',
    `is_system` char(1) DEFAULT '0' COMMENT '是否系统内置（0否 1是）',
    `is_active` char(1) DEFAULT '1' COMMENT '是否启用（0否 1是）',
    `sort_order` int(11) DEFAULT 0 COMMENT '排序号',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_code` (`type_code`),
    KEY `idx_category` (`category`),
    KEY `idx_is_system` (`is_system`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点类型表';

-- 6.3 节点主表
CREATE TABLE IF NOT EXISTS `arch_node` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '节点ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '所属架构图ID',
    `node_type_id` bigint(20) NOT NULL COMMENT '节点类型ID',
    `node_name` varchar(200) NOT NULL COMMENT '节点名称',
    `node_code` varchar(100) DEFAULT NULL COMMENT '节点编码/标识',
    `x_position` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'X坐标位置',
    `y_position` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT 'Y坐标位置',
    `node_width` int(11) DEFAULT 120 COMMENT '节点宽度',
    `node_height` int(11) DEFAULT 80 COMMENT '节点高度',
    `z_index` int(11) DEFAULT 0 COMMENT 'Z轴层级（用于图层顺序）',
    `rotation` decimal(5,2) DEFAULT '0.00' COMMENT '旋转角度',
    `node_style` longtext COMMENT '节点样式（颜色、边框、阴影等）JSON',
    `node_properties` longtext COMMENT '节点属性（业务属性）JSON',
    `node_meta` longtext COMMENT '节点元数据（扩展属性）JSON',
    `label` varchar(200) DEFAULT NULL COMMENT '节点显示标签',
    `description` varchar(500) DEFAULT NULL COMMENT '节点描述',
    `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1禁用 2异常）',
    `locked` char(1) DEFAULT '0' COMMENT '是否锁定（0否 1是）',
    `visible` char(1) DEFAULT '1' COMMENT '是否可见（0否 1是）',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    PRIMARY KEY (`id`),
    KEY `idx_diagram_id` (`diagram_id`),
    KEY `idx_node_type_id` (`node_type_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点主表';

-- 6.4 边缘关系表
CREATE TABLE IF NOT EXISTS `arch_edge` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '边缘ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '所属架构图ID',
    `edge_type` varchar(100) DEFAULT 'default' COMMENT '边缘类型（如：default、dashed、dotted等）',
    `source_node_id` bigint(20) NOT NULL COMMENT '源节点ID',
    `target_node_id` bigint(20) NOT NULL COMMENT '目标节点ID',
    `source_anchor` varchar(50) DEFAULT 'auto' COMMENT '源锚点位置（top/bottom/left/right/auto）',
    `target_anchor` varchar(50) DEFAULT 'auto' COMMENT '目标锚点位置（top/bottom/left/right/auto）',
    `edge_label` varchar(200) DEFAULT NULL COMMENT '边缘标签',
    `edge_style` longtext COMMENT '边缘样式（颜色、线宽、箭头等）JSON',
    `edge_properties` longtext COMMENT '边缘属性JSON',
    `edge_meta` longtext COMMENT '边缘元数据（扩展属性）JSON',
    `weight` decimal(10,2) DEFAULT '1.00' COMMENT '权重（用于布局算法）',
    `animated` char(1) DEFAULT '0' COMMENT '是否有动画（0否 1是）',
    `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1禁用）',
    `visible` char(1) DEFAULT '1' COMMENT '是否可见（0否 1是）',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    PRIMARY KEY (`id`),
    KEY `idx_diagram_id` (`diagram_id`),
    KEY `idx_source_node_id` (`source_node_id`),
    KEY `idx_target_node_id` (`target_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='边缘关系表';

-- 6.5 架构图版本历史表
CREATE TABLE IF NOT EXISTS `arch_diagram_history` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '架构图ID',
    `version` varchar(50) NOT NULL COMMENT '版本号',
    `version_name` varchar(200) DEFAULT NULL COMMENT '版本名称',
    `version_description` varchar(500) DEFAULT NULL COMMENT '版本描述',
    `diagram_data` longtext COMMENT '完整架构图数据JSON',
    `thumbnail` longtext COMMENT '缩略图Base64或URL',
    `change_summary` varchar(1000) DEFAULT NULL COMMENT '变更摘要',
    `change_type` varchar(50) DEFAULT 'update' COMMENT '变更类型（create/update/delete/restore）',
    `is_current` char(1) DEFAULT '0' COMMENT '是否当前版本（0否 1是）',
    `restore_count` int(11) DEFAULT 0 COMMENT '被恢复次数',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_diagram_id` (`diagram_id`),
    KEY `idx_version` (`version`),
    KEY `idx_is_current` (`is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图版本历史表';

-- 6.6 架构图模板表
CREATE TABLE IF NOT EXISTS `arch_diagram_template` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `template_name` varchar(200) NOT NULL COMMENT '模板名称',
    `template_description` varchar(500) DEFAULT '' COMMENT '模板描述',
    `template_category` varchar(100) DEFAULT NULL COMMENT '模板分类（如：微服务、大数据、传统架构等）',
    `thumbnail` longtext COMMENT '缩略图Base64或URL',
    `template_data` longtext NOT NULL COMMENT '模板数据JSON（包含节点、边缘等）',
    `preview_image` longtext COMMENT '预览图Base64或URL',
    `tags` varchar(500) DEFAULT NULL COMMENT '标签（逗号分隔）',
    `is_system` char(1) DEFAULT '0' COMMENT '是否系统内置（0否 1是）',
    `is_public` char(1) DEFAULT '1' COMMENT '是否公开（0否 1是）',
    `use_count` int(11) DEFAULT 0 COMMENT '使用次数',
    `rating` decimal(3,2) DEFAULT '0.00' COMMENT '评分（0-5分）',
    `rating_count` int(11) DEFAULT 0 COMMENT '评分人数',
    `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1禁用）',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_template_category` (`template_category`),
    KEY `idx_is_system` (`is_system`),
    KEY `idx_is_public` (`is_public`),
    KEY `idx_use_count` (`use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图模板表';

-- 6.7 节点分组表
CREATE TABLE IF NOT EXISTS `arch_node_group` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分组ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '所属架构图ID',
    `group_name` varchar(200) NOT NULL COMMENT '分组名称',
    `group_code` varchar(100) DEFAULT NULL COMMENT '分组编码',
    `group_type` varchar(50) DEFAULT 'custom' COMMENT '分组类型（custom自定义/region区域/layer层级）',
    `group_style` longtext COMMENT '分组样式（背景色、边框等）JSON',
    `group_properties` longtext COMMENT '分组属性JSON',
    `parent_group_id` bigint(20) DEFAULT NULL COMMENT '父分组ID（支持嵌套分组）',
    `description` varchar(500) DEFAULT NULL COMMENT '分组描述',
    `visible` char(1) DEFAULT '1' COMMENT '是否可见（0否 1是）',
    `locked` char(1) DEFAULT '0' COMMENT '是否锁定（0否 1是）',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_diagram_id` (`diagram_id`),
    KEY `idx_parent_group_id` (`parent_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点分组表';

-- 6.8 节点分组关联表
CREATE TABLE IF NOT EXISTS `arch_node_group_rel` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `group_id` bigint(20) NOT NULL COMMENT '分组ID',
    `node_id` bigint(20) NOT NULL COMMENT '节点ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_node` (`group_id`, `node_id`),
    KEY `idx_node_id` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点分组关联表';

-- 6.9 架构图分享表
CREATE TABLE IF NOT EXISTS `arch_diagram_share` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分享ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '架构图ID',
    `share_code` varchar(100) NOT NULL COMMENT '分享码',
    `share_type` varchar(50) DEFAULT 'view' COMMENT '分享类型（view查看/edit编辑/comment评论）',
    `share_password` varchar(100) DEFAULT NULL COMMENT '分享密码（加密存储）',
    `expire_time` datetime DEFAULT NULL COMMENT '过期时间（NULL表示永不过期）',
    `max_view_count` int(11) DEFAULT NULL COMMENT '最大查看次数（NULL表示无限制）',
    `current_view_count` int(11) DEFAULT 0 COMMENT '当前查看次数',
    `allow_download` char(1) DEFAULT '0' COMMENT '是否允许下载（0否 1是）',
    `allow_copy` char(1) DEFAULT '0' COMMENT '是否允许复制（0否 1是）',
    `status` char(1) DEFAULT '0' COMMENT '状态（0有效 1已失效 2已撤销）',
    `share_by` varchar(64) DEFAULT '' COMMENT '分享人',
    `share_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '分享时间',
    `revoke_time` datetime DEFAULT NULL COMMENT '撤销时间',
    `revoke_by` varchar(64) DEFAULT NULL COMMENT '撤销人',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_code` (`share_code`),
    KEY `idx_diagram_id` (`diagram_id`),
    KEY `idx_status` (`status`),
    KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图分享表';

-- 6.10 架构图评论表
CREATE TABLE IF NOT EXISTS `arch_diagram_comment` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '架构图ID',
    `node_id` bigint(20) DEFAULT NULL COMMENT '关联节点ID（NULL表示评论整个架构图）',
    `edge_id` bigint(20) DEFAULT NULL COMMENT '关联边缘ID',
    `parent_comment_id` bigint(20) DEFAULT NULL COMMENT '父评论ID（支持回复）',
    `comment_content` text NOT NULL COMMENT '评论内容',
    `comment_position` longtext COMMENT '评论位置坐标JSON（用于在画布上显示评论标记）',
    `comment_type` varchar(50) DEFAULT 'text' COMMENT '评论类型（text文本/image图片/file文件）',
    `attachments` longtext COMMENT '附件信息JSON',
    `is_resolved` char(1) DEFAULT '0' COMMENT '是否已解决（0否 1是）',
    `resolved_time` datetime DEFAULT NULL COMMENT '解决时间',
    `resolved_by` varchar(64) DEFAULT NULL COMMENT '解决人',
    `like_count` int(11) DEFAULT 0 COMMENT '点赞数',
    `reply_count` int(11) DEFAULT 0 COMMENT '回复数',
    `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1已删除）',
    `create_by` varchar(64) DEFAULT '' COMMENT '评论人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    PRIMARY KEY (`id`),
    KEY `idx_diagram_id` (`diagram_id`),
    KEY `idx_node_id` (`node_id`),
    KEY `idx_edge_id` (`edge_id`),
    KEY `idx_parent_comment_id` (`parent_comment_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图评论表';

-- 6.11 架构图收藏表
CREATE TABLE IF NOT EXISTS `arch_diagram_favorite` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '架构图ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `folder_name` varchar(100) DEFAULT 'default' COMMENT '收藏夹名称',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_diagram` (`user_id`, `diagram_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_folder_name` (`folder_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图收藏表';

-- 6.12 架构图操作日志表
CREATE TABLE IF NOT EXISTS `arch_diagram_log` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '架构图ID',
    `operation_type` varchar(50) NOT NULL COMMENT '操作类型（create创建/update修改/delete删除/publish发布/unpublish取消发布/share分享/export导出/import导入）',
    `operation_detail` longtext COMMENT '操作详情JSON',
    `node_id` bigint(20) DEFAULT NULL COMMENT '关联节点ID',
    `edge_id` bigint(20) DEFAULT NULL COMMENT '关联边缘ID',
    `before_data` longtext COMMENT '操作前数据JSON',
    `after_data` longtext COMMENT '操作后数据JSON',
    `ip_address` varchar(128) DEFAULT NULL COMMENT 'IP地址',
    `browser` varchar(50) DEFAULT NULL COMMENT '浏览器',
    `os` varchar(50) DEFAULT NULL COMMENT '操作系统',
    `create_by` varchar(64) DEFAULT '' COMMENT '操作人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_diagram_id` (`diagram_id`),
    KEY `idx_operation_type` (`operation_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图操作日志表';

-- 6.13 架构图标签表
CREATE TABLE IF NOT EXISTS `arch_diagram_tag` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `tag_name` varchar(100) NOT NULL COMMENT '标签名称',
    `tag_color` varchar(20) DEFAULT '#1890ff' COMMENT '标签颜色',
    `tag_type` varchar(50) DEFAULT 'custom' COMMENT '标签类型（system系统/custom自定义）',
    `use_count` int(11) DEFAULT 0 COMMENT '使用次数',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 1删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`tag_name`),
    KEY `idx_use_count` (`use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图标签表';

-- 6.14 架构图标签关联表
CREATE TABLE IF NOT EXISTS `arch_diagram_tag_rel` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `diagram_id` bigint(20) NOT NULL COMMENT '架构图ID',
    `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diagram_tag` (`diagram_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='架构图标签关联表';