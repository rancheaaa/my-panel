package com.cq.panel.admin.server.service;

import com.cq.panel.admin.server.web.domain.vo.monitor.MethodInfoVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.MethodValidationVO;

import java.util.List;

/**
 * 内置方法扫描和验证服务
 * 
 * @author cq
 */
public interface IMethodScannerService {

    /**
     * 扫描task包下所有组件的public方法
     * 
     * @return 可调用的内置方法列表
     */
    List<MethodInfoVO> scanTaskMethods();

    /**
     * 验证内置方法字符串是否正确
     * 
     * @param methodName 方法全限定名
     * @return 验证结果
     */
    MethodValidationVO validateMethod(String methodName);

    /**
     * 验证内置方法及其参数
     * 
     * @param methodName 方法全限定名
     * @param parameterValues 参数值列表（JSON数组格式）
     * @return 验证结果
     */
    MethodValidationVO validateMethodWithParameters(String methodName, String parameterValues);
}