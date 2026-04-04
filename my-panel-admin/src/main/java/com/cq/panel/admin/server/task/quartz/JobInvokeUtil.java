package com.cq.panel.admin.server.task.quartz;

import com.cq.panel.admin.server.common.utils.StringUtils;
import com.cq.panel.admin.server.common.utils.spring.SpringUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.common.loadbalancer.LoadBalancerAlgorithm;
import com.cq.panel.common.loadbalancer.LoadBalancerClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

/**
 * 任务执行工具
 *
 * @author cq
 */
@Slf4j
public class JobInvokeUtil
{
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行方法
     *
     * @param sysJob 系统任务
     */
    public static void invokeMethod(SysJob sysJob) throws Exception
    {
        Integer jobType = sysJob.getJobType();
        if (jobType == null || jobType == 0) // 兼容原有逻辑
        {
            invokeTargetMethod(sysJob);
        }
        else if (jobType == 1)
        {
            invokeInternalMethod(sysJob);
        }
        else if (jobType == 2)
        {
            invokeHttpInterface(sysJob);
        }
    }

    /**
     * 原有 invokeTarget 调度逻辑
     */
    private static void invokeTargetMethod(SysJob sysJob) throws Exception
    {
        String invokeTarget = sysJob.getInvokeTarget();
        String beanName = getBeanName(invokeTarget);
        String methodName = getMethodName(invokeTarget);
        List<Object[]> methodParams = getMethodParams(invokeTarget);

        if (!isValidClassName(beanName))
        {
            Object bean = SpringUtils.getBean(beanName);
            invokeMethod(bean, methodName, methodParams);
        }
        else
        {
            Object bean = Class.forName(beanName).getDeclaredConstructor().newInstance();
            invokeMethod(bean, methodName, methodParams);
        }
    }

    /**
     * 1-内置方法调度
     */
    private static void invokeInternalMethod(SysJob sysJob) throws Exception
    {
        String methodNameFull = sysJob.getMethodName();
        if (StringUtils.isEmpty(methodNameFull)) {
            throw new Exception("内置方法全限定名不能为空");
        }

        if (methodNameFull.contains("(") && methodNameFull.contains(")"))
        {
            SysJob legacy = new SysJob();
            legacy.setInvokeTarget(methodNameFull);
            invokeTargetMethod(legacy);
            return;
        }
        
        int lastDotIndex = methodNameFull.lastIndexOf(".");
        if (lastDotIndex == -1) {
            throw new Exception("内置方法格式错误，需为：com.xxx.ClassName.methodName");
        }
        
        String className = methodNameFull.substring(0, lastDotIndex);
        String methodName = methodNameFull.substring(lastDotIndex + 1);
        
        Object bean;
        try {
            // 先尝试从 Spring 容器获取，如果失败则尝试反射实例化
            String beanName = StringUtils.uncapitalize(className.substring(className.lastIndexOf(".") + 1));
            bean = SpringUtils.getBean(beanName);
        } catch (Exception e) {
            try {
                bean = SpringUtils.getBean(Class.forName(className));
            } catch (Exception e2) {
                bean = Class.forName(className).getDeclaredConstructor().newInstance();
            }
        }
        
        Method method = bean.getClass().getMethod(methodName);
        method.invoke(bean);
    }

    /**
     * 2-HTTP 接口调度
     */
    private static void invokeHttpInterface(SysJob sysJob) throws Exception
    {
        RestTemplate restTemplate = SpringUtils.getBean(RestTemplate.class);
        String httpUrl = sysJob.getHttpUrl();
        String methodStr = sysJob.getHttpMethod();
        String headersJson = sysJob.getHttpHeaders();
        String body = replacePlaceholders(sysJob.getHttpBody(), sysJob);
        String loadBalanceStrategy = sysJob.getLoadBalanceStrategy();

        HttpMethod httpMethod = HttpMethod.valueOf(methodStr.toUpperCase());
        HttpHeaders headers = new HttpHeaders();
        
        if (StringUtils.isNotEmpty(headersJson)) {
            Map<String, String> headerMap = objectMapper.readValue(headersJson, new TypeReference<>() {
            });
            headerMap.forEach(headers::add);
        }
        
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE) && StringUtils.isNotEmpty(body)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        
        String selectedUrl;
        if (StringUtils.isNotEmpty(httpUrl) && httpUrl.contains(",")) {
            String[] urls = httpUrl.split(",");
            LoadBalancerAlgorithm algorithm = LoadBalancerAlgorithm.fromName(loadBalanceStrategy);
            LoadBalancerClient loadBalancerClient = new LoadBalancerClient(algorithm);
            
            selectedUrl = loadBalancerClient.choose(urls);
            log.info("使用负载均衡策略：{}，从{}个URL中选择：{}", algorithm.getName(), urls.length, selectedUrl);
        } else {
            selectedUrl = replacePlaceholders(httpUrl, sysJob);
        }

        ResponseEntity<String> response = restTemplate.exchange(selectedUrl, httpMethod, entity, String.class);
        log.info("HTTP接口调用完成，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码：{}，响应内容：{}", sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), sysJob.getInvokeTarget(), selectedUrl, response.getStatusCode().value(), response.getBody());
        if (response.getStatusCode().isError()) {
            log.error("HTTP接口调用失败，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码：{}，响应内容：{}", sysJob.getJobId(), sysJob.getJobName(), sysJob.getJobGroup(), sysJob.getInvokeTarget(), selectedUrl, response.getStatusCode().value(), response.getBody());
            throw new Exception("HTTP 接口调用失败，sysJobId：{}，jobName：{}，jobGroup：{}，invokeTarget：{}，URL：{}，状态码：" + response.getStatusCode().value() + "，响应内容：" + response.getBody());
        }
    }

    /**
     * 替换占位符
     */
    private static String replacePlaceholders(String text, SysJob sysJob) {
        if (StringUtils.isEmpty(text)) return text;
        return text.replace("{jobId}", String.valueOf(sysJob.getJobId()))
                   .replace("{jobName}", sysJob.getJobName())
                   .replace("{jobGroup}", sysJob.getJobGroup());
    }

    /**
     * 调用任务方法
     *
     * @param bean 目标对象
     * @param methodName 方法名称
     * @param methodParams 方法参数
     */
    private static void invokeMethod(Object bean, String methodName, List<Object[]> methodParams)
            throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException
    {
        if (StringUtils.isNotNull(methodParams) && !methodParams.isEmpty())
        {
            Method method = bean.getClass().getMethod(methodName, getMethodParamsType(methodParams));
            method.invoke(bean, getMethodParamsValue(methodParams));
        }
        else
        {
            Method method = bean.getClass().getMethod(methodName);
            method.invoke(bean);
        }
    }

    /**
     * 校验是否为为class包名
     * 
     * @param invokeTarget 名称
     * @return true是 false否
     */
    public static boolean isValidClassName(String invokeTarget)
    {
        return StringUtils.countMatches(invokeTarget, ".") > 1;
    }

    /**
     * 获取bean名称
     * 
     * @param invokeTarget 目标字符串
     * @return bean名称
     */
    public static String getBeanName(String invokeTarget)
    {
        String beanName = StringUtils.substringBefore(invokeTarget, "(");
        return StringUtils.substringBeforeLast(beanName, ".");
    }

    /**
     * 获取bean方法
     * 
     * @param invokeTarget 目标字符串
     * @return method方法
     */
    public static String getMethodName(String invokeTarget)
    {
        String methodName = StringUtils.substringBefore(invokeTarget, "(");
        return StringUtils.substringAfterLast(methodName, ".");
    }

    /**
     * 获取method方法参数相关列表
     * 
     * @param invokeTarget 目标字符串
     * @return method方法相关参数列表
     */
    public static List<Object[]> getMethodParams(String invokeTarget)
    {
        String methodStr = StringUtils.substringBetween(invokeTarget, "(", ")");
        if (StringUtils.isEmpty(methodStr))
        {
            return null;
        }
        String[] methodParams = methodStr.split(",(?=([^\"']*[\"'][^\"']*[\"'])*[^\"']*$)");
        List<Object[]> clazz = new LinkedList<>();
        for (String methodParam : methodParams) {
            String str = StringUtils.trimToEmpty(methodParam);
            // String字符串类型，以'或"开头
            if (Strings.CS.startsWithAny(str, "'", "\"")) {
                clazz.add(new Object[]{StringUtils.substring(str, 1, str.length() - 1), String.class});
            }
            // boolean布尔类型，等于true或者false
            else if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                clazz.add(new Object[]{Boolean.valueOf(str), Boolean.class});
            }
            // long长整形，以L结尾
            else if (StringUtils.endsWith(str, "L")) {
                clazz.add(new Object[]{Long.valueOf(StringUtils.substring(str, 0, str.length() - 1)), Long.class});
            }
            // double浮点类型，以D结尾
            else if (StringUtils.endsWith(str, "D")) {
                clazz.add(new Object[]{Double.valueOf(StringUtils.substring(str, 0, str.length() - 1)), Double.class});
            }
            // 其他类型归类为整形
            else {
                clazz.add(new Object[]{Integer.valueOf(str), Integer.class});
            }
        }
        return clazz;
    }

    /**
     * 获取参数类型
     * 
     * @param methodParams 参数相关列表
     * @return 参数类型列表
     */
    public static Class<?>[] getMethodParamsType(List<Object[]> methodParams)
    {
        Class<?>[] classs = new Class<?>[methodParams.size()];
        int index = 0;
        for (Object[] os : methodParams)
        {
            classs[index] = (Class<?>) os[1];
            index++;
        }
        return classs;
    }

    /**
     * 获取参数值
     * 
     * @param methodParams 参数相关列表
     * @return 参数值列表
     */
    public static Object[] getMethodParamsValue(List<Object[]> methodParams)
    {
        Object[] classs = new Object[methodParams.size()];
        int index = 0;
        for (Object[] os : methodParams)
        {
            classs[index] = os[0];
            index++;
        }
        return classs;
    }
}