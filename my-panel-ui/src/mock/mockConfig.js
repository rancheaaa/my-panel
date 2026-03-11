import Mock from 'mockjs';

// 模拟配置数据
let configList = Mock.mock({
  'rows|10': [{
    'id|+1': 1,
    'envId|1': [1, 2, 3],
    'projectId|1': [1, 2, 3],
    'configKey': /config\.[a-z]{5}\.key/,
    'configValue': '@word(5, 10)',
    'configDesc': '@cparagraph(1)',
    'updateTime': '@datetime'
  }],
  'total': 10
});

// 查询配置列表
Mock.mock(/\/rc\/config\/list(\?.*)?$/, 'get', (options) => {
  return {
    code: 200,
    msg: '查询成功',
    rows: configList.rows,
    total: configList.total
  };
});

// 查询配置详细
Mock.mock(/\/rc\/config\/\d+$/, 'get', (options) => {
  const id = options.url.split('/').pop();
  const config = configList.rows.find(item => item.id == id);
  return {
    code: 200,
    msg: '查询成功',
    data: config || {}
  };
});

// 新增配置
Mock.mock(/\/rc\/config$/, 'post', (options) => {
  const body = JSON.parse(options.body);
  const newConfig = {
    ...body,
    id: configList.rows.length + 1,
    updateTime: new Date().toISOString()
  };
  configList.rows.unshift(newConfig);
  configList.total++;
  return {
    code: 200,
    msg: '新增成功'
  };
});

// 修改配置
Mock.mock(/\/rc\/config$/, 'put', (options) => {
  const body = JSON.parse(options.body);
  const index = configList.rows.findIndex(item => item.id == body.id);
  if (index !== -1) {
    configList.rows[index] = { ...configList.rows[index], ...body, updateTime: new Date().toISOString() };
  }
  return {
    code: 200,
    msg: '修改成功'
  };
});

// 删除配置
Mock.mock(/\/rc\/config\/.*/, 'delete', (options) => {
  const ids = options.url.split('/').pop().split(',');
  configList.rows = configList.rows.filter(item => !ids.includes(item.id.toString()));
  configList.total = configList.rows.length;
  return {
    code: 200,
    msg: '删除成功'
  };
});

// 批量导入配置 (新增接口)
Mock.mock(/\/rc\/config\/import$/, 'post', (options) => {
  // 模拟批量导入逻辑
  // 实际开发中，这里会解析 properties/yaml 文件并批量插入数据库
  return {
    code: 200,
    msg: '批量导入成功'
  };
});

export default configList;
