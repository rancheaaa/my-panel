import React, { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { getUserInfo } from '../api/auth';

const AuthRoute = ({ children }) => {
  const location = useLocation();
  const [loading, setLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    const checkAuth = async () => {
      const token = localStorage.getItem('token');
      
      // 基础校验：如果没有 Token，直接认为未认证
      if (!token || token === 'null' || token === 'undefined') {
        setIsAuthenticated(false);
        setLoading(false);
        return;
      }

      try {
        // 请求后端接口验证 Token
        await getUserInfo();
        setIsAuthenticated(true);
      } catch (error) {
        console.error('Token verification failed:', error);
        localStorage.removeItem('token');
        setIsAuthenticated(false);
      } finally {
        setLoading(false);
      }
    };

    checkAuth();
  }, []); // 只在组件挂载时执行一次

  if (loading) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        background: '#f0f2f5'
      }}>
        <Spin size="large" tip="正在验证身份..." />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
};

export default AuthRoute;