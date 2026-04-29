package com.cq.panel.admin.server.service.impl;

import com.cq.panel.admin.server.service.IMethodScannerService;
import com.cq.panel.admin.server.web.domain.vo.monitor.MethodInfoVO;
import com.cq.panel.admin.server.web.domain.vo.monitor.MethodValidationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内置方法扫描和验证服务实现
 * 
 * @author cq
 */
@Service
public class MethodScannerServiceImpl implements IMethodScannerService {

    private static final Logger log = LoggerFactory.getLogger(MethodScannerServiceImpl.class);

    private static final String TASK_PACKAGE = "com.cq.panel.admin.server.task";

    private final ApplicationContext applicationContext;

    private final ObjectMapper objectMapper;

    public MethodScannerServiceImpl(ApplicationContext applicationContext, ObjectMapper objectMapper) {
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MethodInfoVO> scanTaskMethods() {
        List<MethodInfoVO> methodList = new ArrayList<>();

        try {
            Map<String, Object> taskBeans = applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Component.class);

            for (Map.Entry<String, Object> entry : taskBeans.entrySet()) {
                Object bean = entry.getValue();
                Class<?> beanClass = bean.getClass();

                if (!beanClass.getName().startsWith(TASK_PACKAGE)) {
                    continue;
                }

                String componentName = getComponentName(beanClass);
                Method[] methods = beanClass.getMethods();

                for (Method method : methods) {
                    if (isValidTaskMethod(method)) {
                        MethodInfoVO methodInfo = createMethodInfo(componentName, method);
                        methodList.add(methodInfo);
                    }
                }
            }

            log.info("扫描到{}个可用的内置方法", methodList.size());
        } catch (Exception e) {
            log.error("扫描内置方法失败", e);
        }

        return methodList;
    }

    @Override
    public MethodValidationVO validateMethod(String methodName) {
        MethodValidationVO result = new MethodValidationVO();

        try {
            Method method = findMethod(methodName);
            if (method == null) {
                result.setValid(false);
                result.setErrorMessage("方法不存在: " + methodName);
                return result;
            }

            MethodInfoVO methodInfo = createMethodInfo(getComponentName(method.getDeclaringClass()), method);
            result.setValid(true);
            result.setMethodInfo(methodInfo);

        } catch (Exception e) {
            result.setValid(false);
            result.setErrorMessage("验证方法失败: " + e.getMessage());
            log.error("验证方法失败: {}", methodName, e);
        }

        return result;
    }

    @Override
    public MethodValidationVO validateMethodWithParameters(String methodName, String parameterValues) {
        MethodValidationVO result = new MethodValidationVO();

        try {
            Method method = findMethod(methodName);
            if (method == null) {
                result.setValid(false);
                result.setErrorMessage("方法不存在: " + methodName);
                return result;
            }

            MethodInfoVO methodInfo = createMethodInfo(getComponentName(method.getDeclaringClass()), method);
            result.setMethodInfo(methodInfo);

            Class<?>[] parameterTypes = method.getParameterTypes();
            Parameter[] parameters = method.getParameters();

            List<MethodValidationVO.ParameterValidationResult> parameterResults = new ArrayList<>();
            boolean allValid = true;

            if (parameterValues == null || parameterValues.trim().isEmpty()) {
                if (parameterTypes.length > 0) {
                    result.setValid(false);
                    result.setErrorMessage("方法需要参数，但未提供参数值");
                    return result;
                }
            } else {
                try {
                    Object[] paramArray = objectMapper.readValue(parameterValues, Object[].class);
                    
                    if (paramArray.length != parameterTypes.length) {
                        result.setValid(false);
                        result.setErrorMessage(String.format("参数数量不匹配，期望%d个参数，实际提供%d个参数", 
                            parameterTypes.length, paramArray.length));
                        return result;
                    }

                    for (int i = 0; i < parameterTypes.length; i++) {
                        MethodValidationVO.ParameterValidationResult paramResult = 
                            validateParameter(i, parameters[i], parameterTypes[i], paramArray[i]);
                        parameterResults.add(paramResult);
                        
                        if (!paramResult.getValid()) {
                            allValid = false;
                        }
                    }
                } catch (Exception e) {
                    result.setValid(false);
                    result.setErrorMessage("参数值解析失败: " + e.getMessage());
                    return result;
                }
            }

            result.setValid(allValid);
            result.setParameterResults(parameterResults);

        } catch (Exception e) {
            result.setValid(false);
            result.setErrorMessage("验证方法失败: " + e.getMessage());
            log.error("验证方法失败: {}", methodName, e);
        }

        return result;
    }

    private Method findMethod(String methodName) {
        try {
            int lastDotIndex = methodName.lastIndexOf('.');
            if (lastDotIndex == -1) {
                return null;
            }

            String prefix = methodName.substring(0, lastDotIndex);
            String methodNamePart = methodName.substring(lastDotIndex + 1);

            Map<String, Object> taskBeans = applicationContext.getBeansWithAnnotation(org.springframework.stereotype.Component.class);

            for (Object bean : taskBeans.values()) {
                Class<?> beanClass = bean.getClass();
                if (!beanClass.getName().startsWith(TASK_PACKAGE)) {
                    continue;
                }

                // 支持两种格式：
                // 1. 组件名.方法名（如：appTask.ryParams）
                // 2. 完整类名.方法名（如：com.cq.panel.admin.server.task.AppTask.ryParams）
                boolean matches = beanClass.getName().equals(prefix);
                
                // 检查是否为完整类名

                // 检查是否为组件名
                String componentName = getComponentName(beanClass);
                if (componentName.equals(prefix)) {
                    matches = true;
                }

                if (matches) {
                    for (Method method : beanClass.getMethods()) {
                        if (method.getName().equals(methodNamePart) && isValidTaskMethod(method)) {
                            return method;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("查找方法失败: {}", methodName, e);
        }

        return null;
    }

    private boolean isValidTaskMethod(Method method) {
        return method.getDeclaringClass() != Object.class &&
               Modifier.isPublic(method.getModifiers()) &&
               !method.isSynthetic() &&
               !method.isBridge();
    }

    private String getComponentName(Class<?> clazz) {
        org.springframework.stereotype.Component componentAnnotation = 
            clazz.getAnnotation(org.springframework.stereotype.Component.class);
        
        if (componentAnnotation != null && !componentAnnotation.value().isEmpty()) {
            return componentAnnotation.value();
        }

        String simpleName = clazz.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private MethodInfoVO createMethodInfo(String componentName, Method method) {
        MethodInfoVO methodInfo = new MethodInfoVO();
        
        methodInfo.setMethodName(method.getDeclaringClass().getName() + "." + method.getName());
        methodInfo.setDisplayName(method.getName());
        methodInfo.setComponentName(componentName);
        methodInfo.setDescription("内置方法");

        final List<MethodInfoVO.ParameterInfo> parameterList = getParameterInfos(method);

        methodInfo.setParameters(parameterList);
        methodInfo.setHasParameters(!parameterList.isEmpty());

        return methodInfo;
    }

    private List<MethodInfoVO.ParameterInfo> getParameterInfos(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Parameter[] parameters = method.getParameters();

        List<MethodInfoVO.ParameterInfo> parameterList = new ArrayList<>();
        for (int i = 0; i < parameterTypes.length; i++) {
            MethodInfoVO.ParameterInfo paramInfo = new MethodInfoVO.ParameterInfo();
            paramInfo.setName(parameters[i].getName());
            paramInfo.setType(parameterTypes[i].getName());
            paramInfo.setTypeDisplayName(getTypeDisplayName(parameterTypes[i]));
            paramInfo.setIsPrimitive(isPrimitiveType(parameterTypes[i]));
            parameterList.add(paramInfo);
        }
        return parameterList;
    }

    private MethodValidationVO.ParameterValidationResult validateParameter(
            int index, Parameter parameter, Class<?> expectedType, Object providedValue) {
        
        MethodValidationVO.ParameterValidationResult result = new MethodValidationVO.ParameterValidationResult();
        result.setIndex(index);
        result.setName(parameter.getName());
        result.setType(expectedType.getName());
        result.setProvidedValue(providedValue != null ? providedValue.toString() : "null");

        try {
            if (providedValue == null) {
                result.setValid(!expectedType.isPrimitive());
                if (!result.getValid()) {
                    result.setErrorMessage("基本类型参数不能为null");
                }
                return result;
            }

            Class<?> providedType = providedValue.getClass();

            if (expectedType.isAssignableFrom(providedType)) {
                result.setValid(true);
                return result;
            }

            if (isPrimitiveWrapperMatch(expectedType, providedType)) {
                result.setValid(true);
                return result;
            }

            if (providedValue instanceof String strValue) {
                boolean canConvert = tryParseString(strValue, expectedType);
                if (canConvert) {
                    result.setValid(true);
                    return result;
                }
            }

            result.setValid(false);
            result.setErrorMessage(String.format("参数类型不匹配，期望: %s, 实际: %s", 
                getTypeDisplayName(expectedType), getTypeDisplayName(providedType)));

        } catch (Exception e) {
            result.setValid(false);
            result.setErrorMessage("参数验证失败: " + e.getMessage());
        }

        return result;
    }

    private boolean tryParseString(String value, Class<?> targetType) {
        try {
            if (targetType == String.class) {
                return true;
            } else if (targetType == Integer.class || targetType == int.class) {
                Integer.parseInt(value);
                return true;
            } else if (targetType == Long.class || targetType == long.class) {
                Long.parseLong(value.replace("L", "").replace("l", ""));
                return true;
            } else if (targetType == Double.class || targetType == double.class) {
                Double.parseDouble(value.replace("D", "").replace("d", ""));
                return true;
            } else if (targetType == Float.class || targetType == float.class) {
                Float.parseFloat(value.replace("F", "").replace("f", ""));
                return true;
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            } else if (targetType == Short.class || targetType == short.class) {
                Short.parseShort(value);
                return true;
            } else if (targetType == Byte.class || targetType == byte.class) {
                Byte.parseByte(value);
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    private String getTypeDisplayName(Class<?> type) {
        if (type == String.class) {
            return "字符串";
        } else if (type == Integer.class || type == int.class) {
            return "整数";
        } else if (type == Long.class || type == long.class) {
            return "长整数";
        } else if (type == Double.class || type == double.class) {
            return "浮点数";
        } else if (type == Boolean.class || type == boolean.class) {
            return "布尔值";
        } else if (type == Float.class || type == float.class) {
            return "浮点数";
        } else if (type == Short.class || type == short.class) {
            return "短整数";
        } else if (type == Byte.class || type == byte.class) {
            return "字节";
        } else if (type == Character.class || type == char.class) {
            return "字符";
        } else {
            return type.getSimpleName();
        }
    }

    private boolean isPrimitiveType(Class<?> type) {
        return type.isPrimitive() || 
               type == String.class ||
               type == Integer.class || type == Long.class ||
               type == Double.class || type == Float.class ||
               type == Boolean.class || type == Short.class ||
               type == Byte.class || type == Character.class;
    }

    private boolean isPrimitiveWrapperMatch(Class<?> expectedType, Class<?> providedType) {
        if (expectedType.isPrimitive()) {
            if (expectedType == int.class && providedType == Integer.class) return true;
            if (expectedType == long.class && providedType == Long.class) return true;
            if (expectedType == double.class && providedType == Double.class) return true;
            if (expectedType == float.class && providedType == Float.class) return true;
            if (expectedType == boolean.class && providedType == Boolean.class) return true;
            if (expectedType == short.class && providedType == Short.class) return true;
            if (expectedType == byte.class && providedType == Byte.class) return true;
            return expectedType == char.class && providedType == Character.class;
        }
        return false;
    }
}