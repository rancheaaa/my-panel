import React, { useState, useRef, useEffect } from 'react';
import { Form, Input, Button, message, Row, Col } from 'antd';
import { UserOutlined, LockOutlined, RocketOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { login, getCodeImg, getUserInfo, getRouters } from '../../api/auth';
import './Login.scss';

const Login = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [codeUrl, setCodeUrl] = useState('');
  const [uuid, setUuid] = useState('');
  const [isDaytime, setIsDaytime] = useState(true);
  const usernameRef = useRef(null);
  const passwordRef = useRef(null);
  const codeRef = useRef(null);

  useEffect(() => {
    fetchCode();
    updateTimeBasedTheme();
    const interval = setInterval(updateTimeBasedTheme, 60000); // Check every minute
    return () => clearInterval(interval);
  }, []);

  const updateTimeBasedTheme = () => {
    const hour = new Date().getHours();
    setIsDaytime(hour >= 6 && hour < 18);
  };

  const fetchCode = async () => {
    try {
      const res = await getCodeImg();
      if (res.data && res.data.img) {
        setCodeUrl('data:image/gif;base64,' + res.data.img);
        setUuid(res.data.uuid);
      }
    } catch (error) {
      console.error("Fetch code error", error);
    }
  };

  const onFinish = async (values) => {
    setLoading(true);
    try {
      const loginData = {
        username: values.username,
        password: values.password,
        code: values.code,
        uuid: uuid
      };
      const res = await login(loginData);
      
      message.success('登录成功');
      localStorage.setItem('token', res.data.token);
      
      const infoRes = await getUserInfo();
      if (infoRes.data) {
        localStorage.setItem('userInfo', JSON.stringify(infoRes.data.user));
        localStorage.setItem('permissions', JSON.stringify(infoRes.data.permissions || []));
        localStorage.setItem('roles', JSON.stringify(infoRes.data.roles || []));
      }
      
      // Fetch dynamic routes
      try {
        const routersRes = await getRouters();
        if (routersRes.code === 200) {
          localStorage.setItem('userRouters', JSON.stringify(routersRes.data));
          
          // Find first accessible route
          const findFirstRoute = (routes, basePath = '') => {
            for (const route of routes) {
              if (route.hidden) continue;
              
              let currentPath = route.path;
              // Handle external links
              if (currentPath.startsWith('http')) continue;
              
              // Ensure path starts with / if it's root or combine with base
              if (!currentPath.startsWith('/')) {
                currentPath = basePath + (basePath === '/' ? '' : '/') + currentPath;
              }
              
              if (route.children && route.children.length > 0) {
                const childPath = findFirstRoute(route.children, currentPath);
                if (childPath) return childPath;
              } else if (route.component && route.component !== 'Layout') {
                return currentPath;
              }
            }
            return null;
          };
          
          const firstRoute = findFirstRoute(routersRes.data);
          // Use window.location.href to trigger full page reload, ensuring routes are re-generated
          window.location.href = firstRoute || '/';
        } else {
          window.location.href = '/';
        }
      } catch (error) {
        console.error("Failed to load routers", error);
        window.location.href = '/';
      }
    } catch (error) {
      console.error(error);
      fetchCode();
    } finally {
      setLoading(false);
    }
  };

  // 生成粒子元素
  const renderParticles = () => {
    return Array.from({ length: 50 }).map((_, index) => (
      <div key={index} className={`particle-${index + 1}`} />
    ));
  };

  // 生成云元素
  const renderClouds = () => {
    return Array.from({ length: 5 }).map((_, index) => (
      <div key={index} className={`cloud cloud-${index + 1}`} />
    ));
  };

  return (
    <div className={`login-container ${isDaytime ? 'daytime' : 'nighttime'}`}>
      {isDaytime ? (
        <div className="clouds">
          {renderClouds()}
        </div>
      ) : (
        <div className="particles">
          {renderParticles()}
        </div>
      )}

      <div className="login-box">
        <div className="login-header">
          <RocketOutlined className="logo-icon" />
          <div className="login-title">My Panel</div>
          <div className="login-subtitle">后台管理系统</div>
        </div>
        <Form
          name="login"
          initialValues={{ remember: true }}
          onFinish={onFinish}
          layout="vertical"
          size="large"
          autoComplete="on"
        >
          <div onClick={() => usernameRef.current && usernameRef.current.focus()}>
            <Form.Item
              name="username"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input 
                ref={usernameRef}
                prefix={<UserOutlined />} 
                placeholder="用户名" 
                autoComplete="username"
              />
            </Form.Item>
          </div>

          <div onClick={() => {
            if (passwordRef.current) {
              // AntD Input.Password forwards ref to the underlying input element
              passwordRef.current.focus();
            }
          }}>
            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password 
                ref={passwordRef}
                prefix={<LockOutlined />} 
                placeholder="密码" 
                autoComplete="current-password"
              />
            </Form.Item>
          </div>

          <div onClick={() => codeRef.current && codeRef.current.focus()}>
            <Form.Item
              name="code"
              rules={[{ required: true, message: '请输入验证码' }]}
            >
              <Row gutter={8} align="middle">
                <Col span={16}>
                  <Input 
                    ref={codeRef}
                    prefix={<SafetyCertificateOutlined />} 
                    placeholder="验证码" 
                  />
                </Col>
                <Col span={8}>
                  <img 
                    src={codeUrl} 
                    alt="验证码" 
                    onClick={fetchCode}
                    style={{ width: '100%', height: '48px', cursor: 'pointer', borderRadius: '8px', border: '1px solid rgba(255, 255, 255, 0.1)', background: '#1e222d' }}
                  />
                </Col>
              </Row>
            </Form.Item>
          </div>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登 录
            </Button>
          </Form.Item>
        </Form>
      </div>
    </div>
  );
};

export default Login;
