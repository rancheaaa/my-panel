import { createBrowserRouter, Navigate } from 'react-router-dom';
import MainLayout from '../layout/MainLayout';
import AuthRoute from './AuthRoute';
import Login from '../pages/login/index';
import NotFound from '../pages/error/NotFound';
import { generateRoutes, loadComponent } from './utils';

// Static routes that are always available
const staticRoutes = [
  {
    path: '/login',
    element: <Login />,
  },
];

// Get dynamic routes from localStorage
let dynamicRoutes = [];
try {
  const userRouters = localStorage.getItem('userRouters');
  if (userRouters) {
    const parsed = JSON.parse(userRouters);
    dynamicRoutes = generateRoutes(parsed);
  }
} catch (error) {
  console.error('Failed to parse user routers', error);
}

// Add catch-all 404 at the end of MainLayout children
const mainLayoutChildren = [
  {
    path: '/',
    element: <Navigate to="/index" replace />,
  },
  ...dynamicRoutes,
  {
      path: '/system/dict-data/:dictType',
      element: loadComponent('system/dict/data')
  },
  {
      path: '/user/profile',
      element: loadComponent('system/user/profile')
  },
  {
      path: '/arch/architectureEdit/:id',
      element: loadComponent('arch/architectureEdit/index')
  },
  {
      path: '/batch/taskList',
      element: loadComponent('batch/taskList/index')
  },
  {
      path: '/batch/taskDetail',
      element: loadComponent('batch/taskDetail/index')
  },
  {
    path: '*',
    element: <NotFound />,
  },
];

const router = createBrowserRouter([
  ...staticRoutes,
  {
    path: '/',
    element: (
      <AuthRoute>
        <MainLayout />
      </AuthRoute>
    ),
    children: mainLayoutChildren,
  },
]);

export default router;
