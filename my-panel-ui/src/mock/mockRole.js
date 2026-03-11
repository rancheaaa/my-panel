import Mock from 'mockjs';

let roleList = Mock.mock({
  'data|100': [{
    'id|+1': 1,
    name: '@title(1, 2)',
    description: '@csentence(5, 10)',
    'status|1': ['active', 'disabled'],
    permissions: ['/']
  }]
}).data;

// 手动设置一些固定的角色以便测试
if (roleList.length > 0) {
    roleList[0].name = 'Admin';
    roleList[0].description = '超级管理员，拥有所有权限';
    roleList[0].permissions = ['/', '/system', '/system/user', '/system/role', '/nested', '/nested/level2', '/nested/level2/level3-1', '/nested/level2/level3-2'];
    
    if (roleList.length > 1) {
        roleList[1].name = 'User';
        roleList[1].description = '普通用户，仅部分权限';
        roleList[1].permissions = ['/'];
    }
}


// 获取角色列表
Mock.mock(/\/api\/roles(\?.*)?$/, 'get', (options) => {
  const url = new URL(options.url, 'http://localhost');
  const name = url.searchParams.get('name');
  const status = url.searchParams.get('status');

  let mockList = roleList.filter(item => {
    if (name && !item.name.toLowerCase().includes(name.toLowerCase())) return false;
    if (status && item.status !== status) return false;
    return true;
  });

  return {
    code: 200,
    message: 'success',
    data: mockList,
  };
});

// 新增角色
Mock.mock('/api/roles', 'post', (options) => {
  const body = JSON.parse(options.body);
  const newRole = {
    id: roleList.length > 0 ? roleList[roleList.length - 1].id + 1 : 1,
    name: body.name,
    description: body.description,
    status: body.status || 'active',
    permissions: body.permissions || []
  };
  roleList.push(newRole);
  return {
    code: 200,
    message: '添加成功',
    data: newRole
  };
});

// 批量删除角色
Mock.mock('/api/roles/batch', 'delete', (options) => {
    const body = JSON.parse(options.body);
    const { ids } = body;
    roleList = roleList.filter(item => !ids.includes(item.id));
    return {
      code: 200,
      message: '批量删除成功'
    };
  });

// 更新角色
Mock.mock(/\/api\/roles\/\d+$/, 'put', (options) => {
  const url = options.url;
  const id = parseInt(url.match(/\/api\/roles\/(\d+)/)[1]);
  const body = JSON.parse(options.body);
  
  const index = roleList.findIndex(item => item.id === id);
  if (index !== -1) {
    roleList[index] = { ...roleList[index], ...body };
    return {
      code: 200,
      message: '更新成功',
      data: roleList[index]
    };
  } else {
    return {
      code: 404,
      message: '角色不存在'
    };
  }
});

// 删除角色
Mock.mock(/\/api\/roles\/\d+$/, 'delete', (options) => {
  const url = options.url;
  const id = parseInt(url.match(/\/api\/roles\/(\d+)/)[1]);
  
  const index = roleList.findIndex(item => item.id === id);
  if (index !== -1) {
    roleList.splice(index, 1);
    return {
      code: 200,
      message: '删除成功'
    };
  } else {
    return {
      code: 404,
      message: '角色不存在'
    };
  }
});

// 保存角色权限
Mock.mock(/\/api\/roles\/\d+\/permissions$/, 'post', (options) => {
    const url = options.url;
    const id = parseInt(url.match(/\/api\/roles\/(\d+)\/permissions/)[1]);
    const body = JSON.parse(options.body);
    
    const index = roleList.findIndex(item => item.id === id);
    if (index !== -1) {
        roleList[index].permissions = body.permissions;
        return {
            code: 200,
            message: '权限保存成功'
        };
    } else {
        return {
            code: 404,
            message: '角色不存在'
        };
    }
});
