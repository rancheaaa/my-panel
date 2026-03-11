import Mock from 'mockjs';

// ---------------------------------------------------------
// Mock Data: Roles & Posts (Shared Source of Truth)
// ---------------------------------------------------------

const rolesData = [
  { roleId: 1, roleName: '超级管理员', roleKey: 'admin', roleSort: 1, status: '0', flag: false },
  { roleId: 2, roleName: '普通角色', roleKey: 'common', roleSort: 2, status: '0', flag: false },
  { roleId: 3, roleName: '游客', roleKey: 'guest', roleSort: 3, status: '0', flag: false }
];

const postsData = [
  { postId: 1, postCode: 'ceo', postName: '董事长', postSort: 1, status: '0' },
  { postId: 2, postCode: 'se', postName: '项目经理', postSort: 2, status: '0' },
  { postId: 3, postCode: 'hr', postName: '人力资源', postSort: 3, status: '0' },
  { postId: 4, postCode: 'user', postName: '普通员工', postSort: 4, status: '0' }
];

// ---------------------------------------------------------
// Mock Data: Users
// ---------------------------------------------------------

let userList = Mock.mock({
  'data|100': [{
    'userId|+1': 1,
    userName: '@first',
    nickName: '@cname',
    'dept': {
        deptName: '@ctitle(3, 5)'
    },
    'sex|1': ['0', '1'], // 0: male, 1: female
    phonenumber: /^1[3-9]\d{9}$/,
    email: '@email',
    createTime: '@datetime',
    'status|1': ['0', '1'], // 0: normal, 1: disabled
    remark: '@cparagraph',
    // Randomly assign roles from rolesData
    'roles': function() {
        const count = Mock.Random.integer(1, 2);
        const shuffled = [...rolesData].sort(() => 0.5 - Math.random());
        return shuffled.slice(0, count).map(r => ({ ...r, flag: true }));
    }
  }]
}).data;

// ---------------------------------------------------------
// Standard Routes (/system/user/...)
// ---------------------------------------------------------

// 获取用户列表
Mock.mock(/\/api\/system\/user\/list(\?.*)?$/, 'get', (options) => {
  console.log('Mock: listUser', options.url);
  // In a real app, we would filter by query params (userName, phonenumber, etc.)
  return {
    code: 200,
    msg: 'success',
    rows: userList,
    total: userList.length,
  };
});

// 获取个人信息
Mock.mock(/\/api\/system\/user\/profile$/, 'get', (options) => {
    return {
        code: 200,
        msg: '操作成功',
        data: {
            userName: 'admin',
            nickName: '若依',
            phonenumber: '15888888888',
            email: 'ry@163.com',
            sex: '1',
            deptName: '研发部门',
            roleGroup: '超级管理员',
            createTime: '2023-01-01 12:00:00',
            avatar: 'https://gw.alipayobjects.com/zos/rmsportal/BiazfanxmamNRoxxVxka.png',
        },
        roleGroup: '超级管理员',
        postGroup: '董事长'
    };
});

// 修改个人信息
Mock.mock(/\/api\/system\/user\/profile$/, 'put', (options) => {
    return {
        code: 200,
        msg: '修改成功'
    };
});

// 修改密码
Mock.mock(/\/api\/system\/user\/profile\/updatePwd/, 'put', (options) => {
    return {
        code: 200,
        msg: '修改成功'
    };
});

// 新增用户
Mock.mock(/\/api\/system\/user$/, 'post', (options) => {
  const body = JSON.parse(options.body);
  
  // Construct roles from roleIds if present
  let assignedRoles = [];
  if (body.roleIds && Array.isArray(body.roleIds)) {
      assignedRoles = rolesData.filter(r => body.roleIds.includes(r.roleId));
  }

  const newUser = {
    userId: userList.length > 0 ? userList[userList.length - 1].userId + 1 : 1,
    userName: body.userName,
    nickName: body.nickName,
    dept: { deptName: '新部门' }, // Mock dept
    sex: body.sex || '0',
    phonenumber: body.phonenumber,
    email: body.email,
    roles: assignedRoles,
    status: body.status || '0',
    createTime: new Date().toISOString().replace('T', ' ').split('.')[0],
    remark: body.remark
  };
  userList.unshift(newUser); // Add to top
  return {
    code: 200,
    msg: '新增成功',
    data: newUser
  };
});

// 修改用户
Mock.mock(/\/api\/system\/user$/, 'put', (options) => {
  const body = JSON.parse(options.body);
  const id = body.userId;
  
  const index = userList.findIndex(item => item.userId === id);
  if (index !== -1) {
    userList[index] = { ...userList[index], ...body };
    // Update roles if roleIds provided
    if (body.roleIds) {
         userList[index].roles = rolesData.filter(r => body.roleIds.includes(r.roleId));
    }
    return {
      code: 200,
      msg: '修改成功',
    };
  }
  return {
    code: 404,
    msg: '用户不存在',
  };
});

// 删除用户
Mock.mock(/\/api\/system\/user\/.*/, 'delete', (options) => {
  const url = options.url;
  const lastPart = url.substring(url.lastIndexOf('/') + 1);
  const ids = lastPart.split(',').map(id => parseInt(id));
  
  if (ids.length > 0) {
      userList = userList.filter(item => !ids.includes(item.userId));
      return {
          code: 200,
          msg: '删除成功'
      };
  }
  return {
      code: 404,
      msg: '用户不存在'
  };
});

// 获取用户详细 (For Edit)
Mock.mock(/\/api\/system\/user\/\d+/, 'get', (options) => {
    console.log('Mock: getUser detail', options.url);
    const url = options.url;
    const id = parseInt(url.match(/\/api\/system\/user\/(\d+)/)[1]);
    const user = userList.find(u => u.userId === id);
    
    if (user) {
        // But for react-ant-design usually just needs roleIds separately or checks
        
        // Prepare roleIds for the form
        const userRoleIds = user.roles ? user.roles.map(r => r.roleId) : [];
        
        return {
            code: 200,
            msg: '操作成功',
            data: user,
            roleIds: userRoleIds,
            postIds: [], // Mock empty posts for now or add logic
            roles: rolesData, // Return ALL available roles
            posts: postsData
        };
    }
    return {
        code: 404,
        msg: '用户不存在'
    };
});

// 获取用户初始化数据 (Add User modal)
Mock.mock(/\/api\/system\/user\/?$/, 'get', () => {
    console.log('Mock: getUser init data');
    return {
        code: 200,
        msg: '操作成功',
        roles: rolesData, // Return ALL available roles
        posts: postsData
    };
});

// 授权角色 (Simulated)
Mock.mock(/\/api\/system\/user\/authRole\/\d+/, 'get', (options) => {
    const url = options.url;
    const id = parseInt(url.match(/\/api\/system\/user\/authRole\/(\d+)/)[1]);
    const user = userList.find(u => u.userId === id);
    
    if (user) {
        const userRoleIds = user.roles ? user.roles.map(r => r.roleId) : [];
        const rolesWithFlag = rolesData.map(r => ({
            ...r,
            flag: userRoleIds.includes(r.roleId)
        }));
        
        return {
            code: 200,
            msg: '操作成功',
            user: user,
            roles: rolesWithFlag
        };
    }
     return {
        code: 404,
        msg: '用户不存在'
    };
});

Mock.mock(/\/api\/system\/user\/authRole/, 'put', (options) => {
     // Simplified: Just assume success
     return {
         code: 200,
         msg: '授权成功'
     };
});
