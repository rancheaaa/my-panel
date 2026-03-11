import Mock from 'mockjs';

// ---------------------
// 监控中心 Mock
// ---------------------

// 操作日志
let operLogList = Mock.mock({
    'rows|20': [{
        'operId|+1': 1,
        title: '@ctitle(2, 4)',
        'businessType|1': [1, 2, 3, 4, 5, 6, 7, 8, 9],
        method: 'com.cq.project.system.controller.SysUserController.add()',
        requestMethod: 'POST',
        operatorType: 1,
        operName: '@cname',
        deptName: '@ctitle(4, 6)',
        operUrl: '/system/user',
        operIp: '@ip',
        operLocation: '@city',
        operParam: '{}',
        jsonResult: '{}',
        'status|1': [0, 1],
        errorMsg: '',
        operTime: '@datetime'
    }],
    total: 20
});

// 登录日志
let loginInforList = Mock.mock({
    'rows|20': [{
        'infoId|+1': 1,
        userName: '@cname',
        ipaddr: '@ip',
        loginLocation: '@city',
        browser: 'Chrome',
        os: 'Windows 10',
        'status|1': ['0', '1'],
        msg: '登录成功',
        loginTime: '@datetime'
    }],
    total: 20
});

// 操作日志列表
Mock.mock(/\/api\/monitor\/operlog\/list(\?.*)?$/, 'get', (options) => {
    return {
        code: 200,
        message: 'success',
        rows: operLogList.rows,
        total: operLogList.total
    };
});

// 删除操作日志
Mock.mock(/\/api\/monitor\/operlog\/(\d+)/, 'delete', (options) => {
    return { code: 200, message: '删除成功' };
});

// 清空操作日志
Mock.mock(/\/api\/monitor\/operlog\/clean$/, 'delete', () => {
    operLogList.rows = [];
    operLogList.total = 0;
    return { code: 200, message: '清空成功' };
});

// 导出操作日志
Mock.mock(/\/api\/monitor\/operlog\/export(\?.*)?$/, 'get', () => {
    return { code: 200, message: '导出成功', msg: '导出成功' };
});

// 登录日志列表
Mock.mock(/\/api\/monitor\/logininfor\/list(\?.*)?$/, 'get', (options) => {
    return {
        code: 200,
        message: 'success',
        rows: loginInforList.rows,
        total: loginInforList.total
    };
});

// 删除登录日志
Mock.mock(/\/api\/monitor\/logininfor\/(\d+)/, 'delete', (options) => {
    return { code: 200, message: '删除成功' };
});

// 清空登录日志
Mock.mock(/\/api\/monitor\/logininfor\/clean$/, 'delete', () => {
    loginInforList.rows = [];
    loginInforList.total = 0;
    return { code: 200, message: '清空成功' };
});

// 解锁登录账号
Mock.mock(/\/api\/monitor\/logininfor\/unlock\/.*/, 'get', () => {
    return { code: 200, message: '解锁成功' };
});

// 导出登录日志
Mock.mock(/\/api\/monitor\/logininfor\/export(\?.*)?$/, 'get', () => {
    return { code: 200, message: '导出成功', msg: '导出成功' };
});

// ---------------------
// 在线用户 Mock
// ---------------------
let onlineList = Mock.mock({
    'rows|10': [{
        'tokenId': '@guid',
        userName: '@cname',
        ipaddr: '@ip',
        loginLocation: '@city',
        browser: 'Chrome',
        os: 'Windows 10',
        loginTime: '@datetime'
    }],
    total: 10
});

Mock.mock(/\/api\/monitor\/online\/list(\?.*)?$/, 'get', (options) => {
    return {
        code: 200,
        rows: onlineList.rows,
        total: onlineList.total
    };
});

Mock.mock(/\/api\/monitor\/online\/.*/, 'delete', () => {
    return { code: 200, message: '强退成功' };
});

// ---------------------
// 定时任务 Mock
// ---------------------
let jobList = Mock.mock({
    'rows|5': [{
        'jobId|+1': 1,
        jobName: '@ctitle(4, 8)',
        'jobGroup|1': ['DEFAULT', 'SYSTEM'],
        invokeTarget: 'ryTask.ryParams(\'ry\')',
        cronExpression: '0/10 * * * * ?',
        'misfirePolicy|1': ['1', '2', '3'],
        'concurrent|1': ['0', '1'],
        'status|1': ['0', '1'],
        createTime: '@datetime'
    }],
    total: 5
});

Mock.mock(/\/api\/monitor\/job\/list(\?.*)?$/, 'get', (options) => {
    return {
        code: 200,
        rows: jobList.rows,
        total: jobList.total
    };
});

Mock.mock(/\/api\/monitor\/job\/changeStatus/, 'put', () => {
    return { code: 200, message: '状态修改成功' };
});

Mock.mock(/\/api\/monitor\/job\/run/, 'put', () => {
    return { code: 200, message: '执行成功' };
});

Mock.mock(/\/api\/monitor\/job$/, 'post', () => {
    return { code: 200, message: '新增成功' };
});

Mock.mock(/\/api\/monitor\/job$/, 'put', () => {
    return { code: 200, message: '修改成功' };
});

Mock.mock(/\/api\/monitor\/job\/\d+$/, 'delete', () => {
    return { code: 200, message: '删除成功' };
});

Mock.mock(/\/api\/monitor\/job\/\d+$/, 'get', (options) => {
    return {
        code: 200,
        data: jobList.rows[0] // 简单返回第一条作为详情
    };
});

// ---------------------
// 服务监控 Mock
// ---------------------
Mock.mock(/\/api\/monitor\/server$/, 'get', () => {
    return {
        code: 200,
        data: {
            cpu: {
                cpuNum: 4,
                total: 100,
                sys: 10.5,
                used: 20.2,
                wait: 0,
                free: 69.3
            },
            mem: {
                total: 16,
                used: 8,
                free: 8,
                usage: 50.0
            },
            jvm: {
                total: 1024,
                max: 2048,
                free: 512,
                version: '1.8.0_211',
                home: '/usr/local/java',
                name: 'Java HotSpot(TM) 64-Bit Server VM',
                startTime: '2023-10-27 10:00:00',
                runTime: '10天5小时',
                inputArgs: '-Xms512m -Xmx1024m',
                usage: 60.5
            },
            sys: {
                computerName: 'My-Panel-Server',
                computerIp: '127.0.0.1',
                userDir: '/home/cq/projects',
                osName: 'Linux',
                osArch: 'amd64'
            },
            sysFiles: [
                {
                    dirName: '/',
                    sysTypeName: 'ext4',
                    typeName: 'Local Disk',
                    total: '50GB',
                    free: '20GB',
                    used: '30GB',
                    usage: 60.0
                },
                {
                    dirName: '/home',
                    sysTypeName: 'ext4',
                    typeName: 'Local Disk',
                    total: '100GB',
                    free: '80GB',
                    used: '20GB',
                    usage: 20.0
                }
            ]
        }
    };
});

// ---------------------
// 缓存监控 Mock
// ---------------------
Mock.mock(/\/api\/monitor\/cache$/, 'get', () => {
    return {
        code: 200,
        data: {
            info: {
                redis_version: '5.0.7',
                redis_mode: 'standalone',
                tcp_port: 6379,
                uptime_in_days: 10,
                connected_clients: 5,
                used_memory_human: '1.5M',
                maxmemory_human: '100M',
                aof_enabled: '0',
                rdb_last_bgsave_status: 'ok',
                instantaneous_input_kbps: '0.00',
                instantaneous_output_kbps: '0.00'
            },
            dbSize: 15,
            commandStats: [
                { name: 'get', value: 100 },
                { name: 'set', value: 50 },
                { name: 'del', value: 20 },
                { name: 'keys', value: 10 },
                { name: 'expire', value: 5 }
            ]
        }
    };
});

// ---------------------
// 缓存列表 Mock
// ---------------------
const cacheNames = [
    { cacheName: 'sys_config', remark: '系统配置' },
    { cacheName: 'sys_dict', remark: '数据字典' },
    { cacheName: 'sys_user', remark: '用户信息' },
    { cacheName: 'sys_role', remark: '角色信息' }
];

Mock.mock(/\/api\/monitor\/cache\/getNames$/, 'get', () => {
    return {
        code: 200,
        data: cacheNames
    };
});

Mock.mock(/\/api\/monitor\/cache\/getKeys\/.*$/, 'get', (options) => {
    const url = options.url;
    // 处理可能存在的 URL 参数
    const path = url.split('?')[0];
    const cacheName = path.substring(path.lastIndexOf('/') + 1);
    const keys = [];
    for (let i = 0; i < 10; i++) {
        keys.push(cacheName + ':' + i);
    }
    return {
        code: 200,
        data: keys
    };
});

Mock.mock(/\/api\/monitor\/cache\/getValue\/.*$/, 'get', (options) => {
    return {
        code: 200,
        data: {
            cacheName: 'sys_config',
            cacheKey: 'sys_config:1',
            cacheValue: '{"configKey":"sys.index.skinName","configValue":"skin-blue"}',
            remark: '系统配置详情'
        }
    };
});

Mock.mock(/\/api\/monitor\/cache\/clearCacheName\/.*$/, 'delete', () => {
    return { code: 200, message: '清理缓存成功' };
});

Mock.mock(/\/api\/monitor\/cache\/clearCacheKey\/.*$/, 'delete', () => {
    return { code: 200, message: '清理缓存成功' };
});

Mock.mock(/\/api\/monitor\/cache\/clearCacheAll$/, 'delete', () => {
    return { code: 200, message: '清理全部缓存成功' };
});


