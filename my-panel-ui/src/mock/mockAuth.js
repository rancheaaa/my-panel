import Mock from 'mockjs';

// 登录
Mock.mock(/\/api\/login/, 'post', (options) => {
  const { username, password } = JSON.parse(options.body);
  if (username === 'admin' && password === '123456') {
    return {
      code: 200,
      message: '登录成功',
      data: {
        token: 'mock-token-admin',
        username: 'admin',
        role: 'admin',
      },
    };
  }
  return {
    code: 401,
    message: '用户名或密码错误',
  };
});

// 获取当前用户信息
Mock.mock(/\/api\/user\/info/, 'get', () => {
  const token = localStorage.getItem('token');
  if (token === 'mock-token-admin') {
    return {
      code: 200,
      message: 'success',
      data: {
        username: 'admin',
        role: 'admin',
        avatar: 'https://gw.alipayobjects.com/zos/rmsportal/BiazfanxmamNRoxxVxka.png',
        roles: ['admin'],
        permissions: ['*:*:*'],
      },
    };
  }
  return {
    code: 401,
    message: 'Token无效或已过期',
  };
});

// 退出登录
Mock.mock(/\/api\/logout/, 'post', {
  code: 200,
  message: '退出成功',
});

// 获取路由信息
Mock.mock(/\/api\/getRouters/, 'get', () => {
  const token = localStorage.getItem('token');
  if (token === 'mock-token-admin') {
    return {
      code: 200,
      message: 'success',
      data: [
        {
          name: 'Index',
          path: '/index',
          hidden: false,
          component: 'index/index',
          meta: { title: '首页', icon: 'dashboard', noCache: false, link: null }
        },
        {
          name: 'System',
          path: '/system',
          hidden: false,
          redirect: 'noRedirect',
          component: 'Layout',
          alwaysShow: true,
          meta: { title: '系统管理', icon: 'system', noCache: false, link: null },
          children: [
            {
              name: 'User',
              path: 'user',
              hidden: false,
              component: 'system/user/index',
              meta: { title: '用户管理', icon: 'user', noCache: false, link: null }
            },
            {
              name: 'Role',
              path: 'role',
              hidden: false,
              component: 'system/role/index',
              meta: { title: '角色管理', icon: 'peoples', noCache: false, link: null }
            },
            {
              name: 'Menu',
              path: 'menu',
              hidden: false,
              component: 'system/menu/index',
              meta: { title: '菜单管理', icon: 'tree-table', noCache: false, link: null }
            },
            {
              name: 'Dept',
              path: 'dept',
              hidden: false,
              component: 'system/dept/index',
              meta: { title: '部门管理', icon: 'tree', noCache: false, link: null }
            },
            {
              name: 'Post',
              path: 'post',
              hidden: false,
              component: 'system/post/index',
              meta: { title: '岗位管理', icon: 'post', noCache: false, link: null }
            },
            {
              name: 'Dict',
              path: 'dict',
              hidden: false,
              component: 'system/dict/index',
              meta: { title: '字典管理', icon: 'dict', noCache: false, link: null }
            },
            {
              name: 'Config',
              path: 'config',
              hidden: false,
              component: 'system/config/index',
              meta: { title: '参数设置', icon: 'edit', noCache: false, link: null }
            },
            {
              name: 'Notice',
              path: 'notice',
              hidden: false,
              component: 'system/notice/index',
              meta: { title: '通知公告', icon: 'message', noCache: false, link: null }
            },
            {
              name: 'Log',
              path: 'log',
              hidden: false,
              component: 'ParentView',
              meta: { title: '日志管理', icon: 'log', noCache: false, link: null },
              children: [
                {
                  name: 'Operlog',
                  path: 'operlog',
                  hidden: false,
                  component: 'system/operlog/index',
                  meta: { title: '操作日志', icon: 'form', noCache: false, link: null }
                },
                {
                  name: 'Logininfor',
                  path: 'logininfor',
                  hidden: false,
                  component: 'system/logininfor/index',
                  meta: { title: '登录日志', icon: 'logininfor', noCache: false, link: null }
                }
              ]
            }
          ]
        },
        {
          name: 'Monitor',
          path: '/monitor',
          hidden: false,
          redirect: 'noRedirect',
          component: 'Layout',
          alwaysShow: true,
          meta: { title: '系统监控', icon: 'monitor', noCache: false, link: null },
          children: [
            {
              name: 'Online',
              path: 'online',
              hidden: false,
              component: 'monitor/online/index',
              meta: { title: '在线用户', icon: 'online', noCache: false, link: null }
            },
            {
              name: 'Job',
              path: 'job',
              hidden: false,
              component: 'monitor/job/index',
              meta: { title: '定时任务', icon: 'job', noCache: false, link: null }
            },
            {
              name: 'Server',
              path: 'server',
              hidden: false,
              component: 'monitor/server/index',
              meta: { title: '服务器监控', icon: 'server', noCache: false, link: null }
            },
            {
              name: 'Cache',
              path: 'cache',
              hidden: false,
              component: 'monitor/cache/index',
              meta: { title: '缓存监控', icon: 'redis', noCache: false, link: null }
            },
            {
              name: 'CacheList',
              path: 'cacheList',
              hidden: false,
              component: 'monitor/cacheList/index',
              meta: { title: '缓存列表', icon: 'list', noCache: false, link: null }
            },
            {
              name: 'Druid',
              path: 'druid',
              hidden: false,
              component: 'monitor/druid/index',
              meta: { title: '数据监控', icon: 'druid', noCache: false, link: null }
            }
          ]
        },
        {
            name: 'Tool',
            path: '/tool',
            hidden: true, // Assuming no tools for now, or keep it visible if there are pages
            redirect: 'noRedirect',
            component: 'Layout',
            alwaysShow: true,
            meta: { title: '系统工具', icon: 'tool', noCache: false, link: null },
            children: []
        }
      ]
    };
  }
  return {
    code: 401,
    message: 'Token无效或已过期',
  };
});
