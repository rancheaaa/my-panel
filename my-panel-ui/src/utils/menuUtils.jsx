import React from 'react';
import * as AntdIcons from '@ant-design/icons';
import { 
  AppstoreOutlined
} from '@ant-design/icons';

// Icon Mapper
export const getIcon = (icon) => {
    if (!icon) return <AppstoreOutlined />;

    // First try to find the icon directly in AntdIcons
    const IconComponent = AntdIcons[icon];
    if (IconComponent) {
        return <IconComponent />;
    }

    const icons = {
        'system': <AntdIcons.SettingOutlined />,
        'user': <AntdIcons.UserOutlined />,
        'role': <AntdIcons.TeamOutlined />,
        'menu': <AntdIcons.AppstoreOutlined />,
        'dept': <AntdIcons.BlockOutlined />,
        'post': <AntdIcons.BlockOutlined />,
        'dict': <AntdIcons.FileTextOutlined />,
        'monitor': <AntdIcons.MonitorOutlined />,
        'online': <AntdIcons.UserSwitchOutlined />,
        'job': <AntdIcons.ScheduleOutlined />,
        'server': <AntdIcons.CloudServerOutlined />,
        'redis': <AntdIcons.DatabaseOutlined />, 
        'cache': <AntdIcons.DatabaseOutlined />,
        'cacheList': <AntdIcons.UnorderedListOutlined />,
        'druid': <AntdIcons.DashboardOutlined />,
        'tool': <AntdIcons.RocketOutlined />,
        'index': <AntdIcons.HomeOutlined />,
        'dashboard': <AntdIcons.DashboardOutlined />,
        'peoples': <AntdIcons.TeamOutlined />,
        'tree-table': <AntdIcons.TableOutlined />
    };
    return icons[icon] || <AppstoreOutlined />;
};

export const transformRoutes = (routes, basePath = '') => {
    if (!routes || !Array.isArray(routes)) return [];
    
    return routes.map(route => {
        if (route.hidden) return null;
        
        let fullPath = route.path;
        if (!fullPath.startsWith('/') && !fullPath.startsWith('http')) {
            fullPath = basePath + (basePath === '/' ? '' : '/') + fullPath;
        }
        
        const item = {
            key: fullPath,
            label: route.meta?.title || route.name,
            icon: getIcon(route.meta?.icon || route.icon),
        };
        
        if (route.children && route.children.length > 0) {
            const children = transformRoutes(route.children, fullPath);
            if (children && children.length > 0) {
                item.children = children;
            }
        }
        
        return item;
    }).filter(Boolean);
};

// Helper to flatten menu tree
export const flattenMenu = (data) => {
    if (!data || !Array.isArray(data)) return [];
    
    let result = [];
    data.forEach(item => {
      result.push({ key: item.key, label: item.label, icon: item.icon });
      if (item.children) {
        result = result.concat(flattenMenu(item.children));
      }
    });
    return result;
};

// Get menu data from localStorage
export const getMenuData = () => {
    const userRouters = localStorage.getItem('userRouters');
    if (userRouters) {
        try {
            const parsed = JSON.parse(userRouters);
            return transformRoutes(parsed);
        } catch (error) {
            console.error("Failed to parse user routers", error);
            return [];
        }
    }
    return [];
};
