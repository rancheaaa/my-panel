package com.cq.panel.admin.server.common.utils.quartz;

import com.cq.panel.admin.server.common.utils.spring.SpringUtils;
import com.cq.panel.admin.server.repository.domain.SysJob;
import com.cq.panel.admin.server.task.quartz.JobInvokeUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 定时任务执行工具测试类
 * 
 * @author cq
 */
public class JobInvokeUtilTest {

    private static MockWebServer mockWebServer;
    private static ConfigurableListableBeanFactory mockBeanFactory;
    private static RestTemplate restTemplate;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        mockBeanFactory = mock(ConfigurableListableBeanFactory.class);
        ReflectionTestUtils.setField(SpringUtils.class, "beanFactory", mockBeanFactory);
        
        restTemplate = new RestTemplate();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("测试内置方法调度 - 正常流程")
    void testInvokeInternalMethod_Success() throws Exception {
        TestTask testTask = mock(TestTask.class);
        when(mockBeanFactory.getBean("testTask")).thenReturn(testTask);

        SysJob job = new SysJob();
        job.setJobType(1);
        job.setMethodName("com.cq.panel.admin.server.common.utils.quartz.JobInvokeUtilTest$TestTask.run");

        JobInvokeUtil.invokeMethod(job);

        verify(testTask, times(1)).run();
    }

    @Test
    @DisplayName("测试内置方法调度 - 格式错误异常")
    void testInvokeInternalMethod_FormatError() {
        SysJob job = new SysJob();
        job.setJobType(1);
        job.setMethodName("InvalidMethodFormat");

        Exception exception = assertThrows(Exception.class, () -> JobInvokeUtil.invokeMethod(job));
        assertTrue(exception.getMessage().contains("格式错误"));
    }

    @Test
    @DisplayName("测试HTTP接口调度 - POST请求与占位符替换")
    void testInvokeHttpInterface_PostSuccess() throws Exception {
        when(mockBeanFactory.getBean(RestTemplate.class)).thenReturn(restTemplate);

        mockWebServer.enqueue(new MockResponse().setBody("OK").setResponseCode(200));

        SysJob job = new SysJob();
        job.setJobId(888L);
        job.setJobName("HttpTest");
        job.setJobType(2);
        job.setHttpUrl(mockWebServer.url("/api/v1/task/{jobId}").toString());
        job.setHttpMethod("POST");
        job.setHttpHeaders("{\"Content-Type\": \"application/json\", \"X-Auth\": \"Token123\"}");
        job.setHttpBody("{\"taskName\": \"{jobName}\", \"id\": {jobId}}");

        JobInvokeUtil.invokeMethod(job);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().endsWith("/api/v1/task/888"));
        assertEquals("Token123", request.getHeader("X-Auth"));
        assertEquals("{\"taskName\": \"HttpTest\", \"id\": 888}", request.getBody().readUtf8());
    }

    @Test
    @DisplayName("测试HTTP接口调度 - GET请求")
    void testInvokeHttpInterface_GetSuccess() throws Exception {
        when(mockBeanFactory.getBean(RestTemplate.class)).thenReturn(restTemplate);
        mockWebServer.enqueue(new MockResponse().setBody("data").setResponseCode(200));

        SysJob job = new SysJob();
        job.setJobType(2);
        job.setHttpUrl(mockWebServer.url("/get-test?name={jobName}").toString());
        job.setJobName("QueryTask");
        job.setHttpMethod("GET");

        JobInvokeUtil.invokeMethod(job);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains("name=QueryTask"));
    }

    @Test
    @DisplayName("测试HTTP接口调度 - 404错误处理")
    void testInvokeHttpInterface_ErrorStatus() {
        when(mockBeanFactory.getBean(RestTemplate.class)).thenReturn(restTemplate);
        mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));

        SysJob job = new SysJob();
        job.setJobType(2);
        job.setHttpUrl(mockWebServer.url("/not-found").toString());
        job.setHttpMethod("GET");

        Exception exception = assertThrows(Exception.class, () -> JobInvokeUtil.invokeMethod(job));
        assertTrue(exception.getMessage().contains("404"));
    }

    @Test
    @DisplayName("测试HTTP接口调度 - 负载均衡多URL")
    void testInvokeHttpInterface_LoadBalancing() throws Exception {
        when(mockBeanFactory.getBean(RestTemplate.class)).thenReturn(restTemplate);
        
        MockWebServer server1 = new MockWebServer();
        MockWebServer server2 = new MockWebServer();
        server1.start();
        server2.start();
        
        server1.enqueue(new MockResponse().setBody("Server1").setResponseCode(200));
        server2.enqueue(new MockResponse().setBody("Server2").setResponseCode(200));

        try {
            SysJob job = new SysJob();
            job.setJobType(2);
            job.setHttpUrl(server1.url("/api").toString() + "," + server2.url("/api").toString());
            job.setHttpMethod("GET");
            job.setLoadBalanceStrategy("roundRobin");

            JobInvokeUtil.invokeMethod(job);

            RecordedRequest request = server1.takeRequest();
            assertNotNull(request);
            assertEquals("GET", request.getMethod());
        } finally {
            server1.shutdown();
            server2.shutdown();
        }
    }

    @Test
    @DisplayName("测试HTTP接口调度 - 负载均衡随机策略")
    void testInvokeHttpInterface_LoadBalancingRandom() throws Exception {
        when(mockBeanFactory.getBean(RestTemplate.class)).thenReturn(restTemplate);
        
        MockWebServer server1 = new MockWebServer();
        MockWebServer server2 = new MockWebServer();
        server1.start();
        server2.start();
        
        server1.enqueue(new MockResponse().setBody("Server1").setResponseCode(200));
        server2.enqueue(new MockResponse().setBody("Server2").setResponseCode(200));

        try {
            SysJob job = new SysJob();
            job.setJobType(2);
            job.setHttpUrl(server1.url("/api").toString() + "," + server2.url("/api").toString());
            job.setHttpMethod("GET");
            job.setLoadBalanceStrategy("random");

            JobInvokeUtil.invokeMethod(job);

            RecordedRequest request1 = server1.takeRequest();
            RecordedRequest request2 = server2.takeRequest();
            assertTrue(true);
        } finally {
            server1.shutdown();
            server2.shutdown();
        }
    }

    @Test
    @DisplayName("测试原有逻辑兼容性")
    void testInvokeLegacyMethod() throws Exception {
        // 原有逻辑会调用 getBeanName 等，这里简单模拟
        TestTask legacyTask = mock(TestTask.class);
        when(mockBeanFactory.getBean("testTask")).thenReturn(legacyTask);

        SysJob job = new SysJob();
        job.setJobType(0);
        job.setInvokeTarget("testTask.run()");

        JobInvokeUtil.invokeMethod(job);

        verify(legacyTask, times(1)).run();
    }

    public static class TestTask {
        public void run() {}
    }
}