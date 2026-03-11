import React, { lazy, Suspense } from 'react';
import { Spin } from 'antd';
import { Outlet } from 'react-router-dom';

// Auto-import all page components
const modules = import.meta.glob('../pages/**/*.jsx');

/**
 * Loading component
 */
const LoadingComponent = () => (
  <div style={{ padding: 50, textAlign: 'center' }}>
    <Spin size="large" />
  </div>
);

/**
 * Convert component string to React Component
 * @param {string} componentPath - e.g. "system/user/index"
 */
export const loadComponent = (componentPath) => {
  if (!componentPath || componentPath === 'Layout' || componentPath === 'ParentView') {
    return null;
  }

  let path = componentPath;
  // If it doesn't start with /, assume relative to pages
  // But our keys in modules are like "../pages/..."
  
  // Normalize path
  // Try to find exact match
  let importFn = modules[`../pages/${path}.jsx`];
  
  if (!importFn) {
      importFn = modules[`../pages/${path}/index.jsx`];
  }

  if (!importFn) {
    console.warn(`Component not found: ${componentPath}`);
    return <div style={{ padding: 20, color: 'red' }}>Component not found: {componentPath}</div>;
  }

  const Component = lazy(importFn);
  
  return (
    <Suspense fallback={<LoadingComponent />}>
      <Component />
    </Suspense>
  );
};

/**
 * Transform backend routes to React Router DOM routes
 * @param {Array} routes 
 */
export const generateRoutes = (routes) => {
  if (!routes) return [];
  
  return routes.map(route => {
    // Skip hidden routes if needed, but router usually needs them to match URL
    
    const item = {
      path: route.path,
    };

    // Handle component
    if (route.component === 'Layout') {
       // Usually Layout is handled at the root level, but for nested layouts:
       // If we are inside the MainLayout already, we might just render Outlet or similar
       // If we encounter "Layout" inside children, it might mean a nested layout (ParentView)
       // For simplicity, if it has children, we just map children.
       item.element = <Outlet />;
    } else if (route.component === 'ParentView') {
       // Nested router outlet
       item.element = <Outlet />;
    } else if (route.component) {
       item.element = loadComponent(route.component);
    }

    if (route.children && route.children.length > 0) {
      item.children = generateRoutes(route.children);
    }

    return item;
  });
};
