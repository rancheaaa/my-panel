import Mock from 'mockjs';

// ---------------------
// 字典数据 Mock
// ---------------------

const dicts = {
    sys_oper_type: [
        { dictLabel: '新增', dictValue: '1' },
        { dictLabel: '修改', dictValue: '2' },
        { dictLabel: '删除', dictValue: '3' },
        { dictLabel: '授权', dictValue: '4' },
        { dictLabel: '导出', dictValue: '5' },
        { dictLabel: '导入', dictValue: '6' },
        { dictLabel: '强退', dictValue: '7' },
        { dictLabel: '生成代码', dictValue: '8' },
        { dictLabel: '清空数据', dictValue: '9' },
    ],
    sys_common_status: [
        { dictLabel: '成功', dictValue: '0' },
        { dictLabel: '失败', dictValue: '1' },
    ],
    sys_job_group: [
        { dictLabel: '默认', dictValue: 'DEFAULT' },
        { dictLabel: '系统', dictValue: 'SYSTEM' },
    ],
    sys_job_status: [
        { dictLabel: '正常', dictValue: '0' },
        { dictLabel: '暂停', dictValue: '1' },
    ]
};

// 获取字典数据
Mock.mock(/\/api\/system\/dict\/data\/type\/.*$/, 'get', (options) => {
    const url = options.url;
    const dictType = url.substring(url.lastIndexOf('/') + 1);
    const data = dicts[dictType] || [];
    return {
        code: 200,
        message: 'success',
        data: data
    };
});
