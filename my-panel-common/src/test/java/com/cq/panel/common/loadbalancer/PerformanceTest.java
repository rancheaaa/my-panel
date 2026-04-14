package com.cq.panel.common.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能测试独立测试类
 */
@DisplayName("性能测试")
class PerformanceTest extends BaseMockHttpServerTest {
    
    @Test
    @DisplayName("多请求并发测试")
    void testConcurrentRequests() throws InterruptedException {
        HttpClient client = new ApacheHttpClient();
        
        Thread[] threads = new Thread[5];
        final boolean[] success = new boolean[5];
        
        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    HttpResponse<String> response = client.get(BASE_URL + "/json", String.class);
                    success[index] = (response != null && response.getStatusCode() == 200);
                } catch (Exception e) {
                    success[index] = false;
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 验证所有请求都成功
        for (boolean s : success) {
            assertTrue(s, "所有并发请求应该成功");
        }
    }
}