import axios from 'axios';
import { message } from 'antd';

// 创建 axios 实例
const service = axios.create({
  // 根据环境读取 Base URL
  // .env.development: VITE_API_BASE_URL=/api
  // .env.production: VITE_API_BASE_URL=https://api.example.com
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api', 
  timeout: 10000, // 请求超时时间
});

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 在发送请求之前做些什么
    const token = localStorage.getItem('token');
    if (token) {
      // 让每个请求携带 token
      // ['Authorization'] 是自定义 headers key
      // 请根据实际情况修改
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    // 对请求错误做些什么
    console.error('Request Error:', error);
    return Promise.reject(error);
  }
);

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    // 如果是二进制数据则直接返回
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response.data;
    }

    const res = response.data;
    
    // 这里的 code === 200 是根据 Mock 数据约定的
    // 真实后端可能返回不同的状态码结构，请根据实际情况修改
    if (res.code !== 200) {
      message.error(res.msg || res.message || 'Error');

      // 401: 未登录或 Token 过期
      if (res.code === 401) {
        // 清除 Token 并跳转登录页
        localStorage.removeItem('token');
        window.location.href = '/login';
      }
      return Promise.reject(new Error(res.msg || res.message || 'Error'));
    } else {
      return res;
    }
  },
  (error) => {
    console.error('Response Error:', error);
    const { response } = error || {};
    
    if (response && response.status === 401) {
       message.error('登录过期，请重新登录');
       localStorage.removeItem('token');
       window.location.href = '/login';
    } else {
       message.error(error.message || '请求失败');
    }
    return Promise.reject(error);
  }
);

export default service;