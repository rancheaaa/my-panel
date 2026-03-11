import React, { useState, useEffect } from 'react';
import { Layout, Menu, Breadcrumb, Button, message, Avatar, Dropdown, theme } from 'antd';
import { Outlet, useLocation, useNavigate, useOutlet } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import TagsView from '../components/TagsView';
import { RocketOutlined, LogoutOutlined, UserOutlined, MenuUnfoldOutlined, MenuFoldOutlined, BellOutlined, FullscreenOutlined, FullscreenExitOutlined } from '@ant-design/icons';
import { logout as apiLogout } from '../api/auth';
import screenfull from 'screenfull';
import { getMenuData } from '../utils/menuUtils';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import './MainLayout.css';

NProgress.configure({ showSpinner: false });

const { Header, Content, Sider, Footer } = Layout;

const MainLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const currentOutlet = useOutlet();
  const [collapsed, setCollapsed] = useState(false);
  
  // Use Ant Design theme token
  const {
    token: { colorBgContainer },
  } = theme.useToken();
  
  // Get User Info
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
  const username = userInfo.nickName || userInfo.userName || 'Admin';
  const avatar = userInfo.avatar || '';
  
  // State for top menu items (Level 3)
  const [topMenuItems, setTopMenuItems] = useState([]);
  const [sidebarSelectedKeys, setSidebarSelectedKeys] = useState([]);
  const [headerSelectedKeys, setHeaderSelectedKeys] = useState([]);
  const [openKeys, setOpenKeys] = useState([]);
  const [menuData, setMenuData] = useState([]);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [breadcrumbItems, setBreadcrumbItems] = useState([]);

  useEffect(() => {
      const data = getMenuData();
      setMenuData(data);

      const handleMenuRefresh = () => {
        const newData = getMenuData();
        setMenuData(newData);
      };
      
      window.addEventListener('sys:menu:refresh', handleMenuRefresh);
      
      return () => {
        window.removeEventListener('sys:menu:refresh', handleMenuRefresh);
      };
  }, []);

  useEffect(() => {
    NProgress.start();
    // 模拟加载完成，配合页面过渡动画
    const timer = setTimeout(() => {
      NProgress.done();
    }, 400); // 对应动画时长

    return () => {
      clearTimeout(timer);
      NProgress.done();
    };
  }, [location.pathname]);

  const handleLogout = async () => {
    try {
      await apiLogout();
    } catch (error) {
      console.error('Logout failed:', error);
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      message.success('退出登录成功');
      window.location.href = '/login';
    }
  };

  const userMenuItems = [
    {
      key: 'profile',
      label: '个人中心',
      icon: <UserOutlined />,
      onClick: () => navigate('/user/profile'),
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      label: '退出登录',
      icon: <LogoutOutlined />,
      danger: true,
      onClick: handleLogout,
    },
  ];

  // Helper to find path in menu tree
  const findPath = (data, path, parents = []) => {
      for (const item of data) {
          if (item.key === path) return [...parents, item];
          if (item.children) {
              const res = findPath(item.children, path, [...parents, item]);
              if (res) return res;
          }
      }
      return null;
  };

  const handleMenuClick = ({ key }) => {
      const pathItems = findPath(menuData, key);
      if (!pathItems || pathItems.length === 0) {
          navigate(key);
          return;
      }
      
      const item = pathItems[pathItems.length - 1];
      
      // Helper to find first leaf
      const findFirstLeaf = (node) => {
          if (!node.children || node.children.length === 0) {
              return node.key;
          }
          return findFirstLeaf(node.children[0]);
      };
      
      const targetKey = findFirstLeaf(item);
      navigate(targetKey);
  };

  useEffect(() => {
      const pathname = location.pathname;
      
      if (pathname === '/user/profile') {
          setBreadcrumbItems([
              { title: <span style={{ cursor: 'pointer' }} onClick={() => navigate('/')}>首页</span> },
              { title: '个人中心' }
          ]);
          setSidebarSelectedKeys([]);
          setHeaderSelectedKeys([]);
          setTopMenuItems([]);
          return;
      }

      const pathItems = findPath(menuData, pathname);
      
      if (pathItems) {
          setBreadcrumbItems(pathItems.map((item, index) => {
              const isLast = index === pathItems.length - 1;
              return {
                  title: isLast ? (
                      item.label
                  ) : (
                      <span 
                        style={{ cursor: 'pointer', color: 'rgba(0, 0, 0, 0.45)', transition: 'color 0.3s' }} 
                        className="breadcrumb-link"
                        onClick={() => handleMenuClick({ key: item.key })}
                        onMouseEnter={(e) => e.target.style.color = '#1890ff'}
                        onMouseLeave={(e) => e.target.style.color = 'rgba(0, 0, 0, 0.45)'}
                      >
                          {item.label}
                      </span>
                  )
              };
          }));
          
          // Set selected keys for sidebar
          // If depth >= 2 (e.g. System -> Log -> OperLog), sidebar shows up to L2 (Log).
          // So we should highlight the item at index 1.
          // If depth == 1, highlight index 0.
          if (pathItems.length >= 2) {
              setSidebarSelectedKeys([pathItems[1].key]);
          } else if (pathItems.length === 1) {
              setSidebarSelectedKeys([pathItems[0].key]);
          } else {
              setSidebarSelectedKeys([pathname]);
          }
          
          // Set selected keys for header (top menu)
          // Always highlight the current pathname (L3 item)
          setHeaderSelectedKeys([pathname]);
          
          if (pathItems.length > 0) {
             const l1 = pathItems[0];
             setOpenKeys((prev) => {
                 if (!prev.includes(l1.key)) return [...prev, l1.key];
                 return prev;
             });
          }
          
          if (pathItems.length >= 2) {
             const activeL2 = pathItems[1];
             if (activeL2 && activeL2.children && activeL2.children.length > 0) {
                 setTopMenuItems(activeL2.children.map(child => ({
                     key: child.key,
                     label: child.label,
                     icon: child.icon,
                 })));
             } else {
                 setTopMenuItems([]);
             }
          } else {
              setTopMenuItems([]);
          }
      } else {
          setBreadcrumbItems([]);
          setSidebarSelectedKeys([pathname]);
          setHeaderSelectedKeys([]);
      }
  }, [location.pathname, menuData]);

  useEffect(() => {
      if (screenfull.isEnabled) {
          screenfull.on('change', () => {
              setIsFullscreen(screenfull.isFullscreen);
          });
      }
      return () => {
          if (screenfull.isEnabled) {
              screenfull.off('change');
          }
      };
  }, []);

  const toggleFullscreen = () => {
      if (screenfull.isEnabled) {
          screenfull.toggle();
      }
  };

  const filterLevel12 = (items, level = 1) => {
      if (!items || !Array.isArray(items)) return [];
      return items.map(item => {
          if (item.children && level < 2) {
              return {
                  ...item,
                  children: filterLevel12(item.children, level + 1)
              };
          } else {
              const { children, ...rest } = item;
              return rest;
          }
      });
  };

  const sidebarItems = filterLevel12(menuData);

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider 
        width={240}
        trigger={null}
        collapsible 
        collapsed={collapsed} 
        className="main-sider"
      >
        <div className="logo-container">
            <RocketOutlined className="logo-icon" />
            {!collapsed && <span className="logo-text">控制面板</span>}
        </div>
        <Menu 
            theme="dark" 
            mode="inline" 
            items={sidebarItems} 
            selectedKeys={sidebarSelectedKeys}
            openKeys={openKeys}
            onOpenChange={setOpenKeys}
            onClick={handleMenuClick}
            className="sider-menu"
        />
      </Sider>
      <Layout style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>
        <Header style={{ padding: 0, background: colorBgContainer }} className="main-header">
            <div className="header-left">
                <Button
                    type="text"
                    icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                    onClick={() => setCollapsed(!collapsed)}
                    style={{ fontSize: '16px', width: 64, height: 64 }}
                />
                
                <div style={{ display: 'flex', alignItems: 'center', height: '100%' }}>
                    <Breadcrumb items={breadcrumbItems} style={{ marginLeft: 16 }} />
                </div>
            </div>

            {/* 顶部菜单居中显示 */}
            <div className="header-center">
                {topMenuItems.length > 0 && (
                    <Menu
                        mode="horizontal"
                        items={topMenuItems}
                        selectedKeys={headerSelectedKeys}
                        onClick={({ key }) => navigate(key)}
                        className="top-menu"
                    />
                )}
            </div>
            
            <div className="header-right">
                <Button 
                    type="text" 
                    icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />} 
                    onClick={toggleFullscreen}
                    style={{ fontSize: '18px', marginRight: 8 }} 
                />
                <Button type="text" icon={<BellOutlined />} style={{ fontSize: '18px', marginRight: 16 }} />
                <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" arrow>
                    <div className="user-dropdown">
                        <Avatar 
                            style={{ backgroundColor: '#1890ff' }} 
                            src={avatar ? (import.meta.env.VITE_API_BASE_URL + avatar) : null}
                            icon={!avatar && <UserOutlined />} 
                        />
                        <span className="username">{username}</span>
                    </div>
                </Dropdown>
            </div>
        </Header>
        <TagsView />
        <Content style={{ margin: '16px 16px 0 16px', flex: 1, minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <AnimatePresence mode="wait">
                <motion.div
                    key={location.pathname}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 20 }}
                    transition={{ duration: 0.4, ease: "easeInOut" }}
                    style={{ 
                        height: '100%', 
                        width: '100%', 
                        display: 'flex', 
                        flexDirection: 'column', 
                        overflow: 'auto'
                    }}
                >
                    {currentOutlet}
                </motion.div>
            </AnimatePresence>
        </Content>
        <div style={{ padding: '16px' }}>
          <div className="copyright-card">
            Copyright MIT © {new Date().getFullYear()} My-Panel
          </div>
        </div>
      </Layout>
    </Layout>
  );
};

export default MainLayout;