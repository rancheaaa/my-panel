import request from '../utils/request';

/**
 * 登录
 */
export function login(data) {
  return request({
    url: '/login',
    method: 'post',
    data,
  });
}

/**
 * 获取用户信息
 */
export function getUserInfo() {
  return request({
    url: '/getInfo',
    method: 'get',
  });
}

/**
 * 退出登录
 */
export function logout() {
  return request({
    url: '/logout',
    method: 'post',
  });
}

/**
 * 获取验证码
 */
export function getCodeImg() {
  return request({
    url: '/captchaImage',
    method: 'get',
    timeout: 20000
  });
}

/**
 * 获取路由信息
 */
export function getRouters() {
  return request({
    url: '/getRouters',
    method: 'get',
  });
}
