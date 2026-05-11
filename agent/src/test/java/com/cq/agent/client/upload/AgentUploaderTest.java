package com.cq.agent.client.upload;

import com.cq.agent.config.AgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentUploaderTest {

    @TempDir
    Path tempDir;

    private AgentUploader uploader;
    private PersistentMap<String, UploadTask> taskInflightMap;

    @BeforeEach
    void setUp() throws Exception {
        AgentConfig agentConfig = new AgentConfig();
        
        Path queueDbPath = tempDir.resolve("upload_queue_db");
        Path mapDbPath = tempDir.resolve("upload_map_db");
        
        agentConfig.setUploadQueueDbPath(queueDbPath.toString());
        agentConfig.setUploadMapDbPath(mapDbPath.toString());
        
        uploader = new AgentUploader(agentConfig);
        uploader.init();
        
        taskInflightMap = getTaskInflightMap(uploader);
    }

    @AfterEach
    void tearDown() {
        if (taskInflightMap != null) {
            try {
                taskInflightMap.close();
            } catch (Exception e) {
                System.err.println("Error closing taskInflightMap: " + e.getMessage());
            }
        }
        if (uploader != null) {
            try {
                uploader.shutdown();
            } catch (Exception e) {
                System.err.println("Error shutting down uploader: " + e.getMessage());
            }
        }
    }

    @Test
    void testGetInflightTasksCount_Empty() {
        int count = uploader.getInflightTasksCount();
        assertEquals(0, count, "Inflight tasks count should be 0 when no tasks exist");
    }

    @Test
    void testGetAllInflightTasks_Empty() {
        List<UploadTask> tasks = uploader.getAllInflightTasks();
        assertNotNull(tasks, "Tasks list should not be null");
        assertTrue(tasks.isEmpty(), "Tasks list should be empty when no tasks exist");
    }

    @Test
    void testGetInflightTasks_InvalidPage() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            uploader.getInflightTasks(0, 10);
        });
        assertTrue(exception.getMessage().contains("page must be >= 1"), 
                "Should throw exception for page < 1");
    }

    @Test
    void testGetInflightTasks_InvalidPageSize_TooSmall() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            uploader.getInflightTasks(1, 0);
        });
        assertTrue(exception.getMessage().contains("pageSize must be between 1 and 1000"), 
                "Should throw exception for pageSize < 1");
    }

    @Test
    void testGetInflightTasks_InvalidPageSize_TooLarge() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            uploader.getInflightTasks(1, 1001);
        });
        assertTrue(exception.getMessage().contains("pageSize must be between 1 and 1000"), 
                "Should throw exception for pageSize > 1000");
    }

    @Test
    void testGetInflightTasks_SinglePage() throws IOException {
        int taskCount = 5;
        createTestTasks(taskCount);
        
        List<UploadTask> tasks = uploader.getInflightTasks(1, 10);
        assertEquals(taskCount, tasks.size(), "Should return all tasks on single page");
        
        for (UploadTask task : tasks) {
            assertNotNull(task.getLocalFilePath());
            assertNotNull(task.getRemoteTargetPath());
            assertNotNull(task.getTransferId());
        }
    }

    @Test
    void testGetInflightTasks_MultiplePages() throws IOException {
        int taskCount = 25;
        int pageSize = 10;
        createTestTasks(taskCount);
        
        int expectedPage1Count = Math.min(pageSize, taskCount);
        int expectedPage2Count = Math.min(pageSize, taskCount - pageSize);
        int expectedPage3Count = Math.max(0, taskCount - 2 * pageSize);
        
        List<UploadTask> page1 = uploader.getInflightTasks(1, pageSize);
        assertEquals(expectedPage1Count, page1.size(), "Page 1 should have correct number of tasks");
        
        List<UploadTask> page2 = uploader.getInflightTasks(2, pageSize);
        assertEquals(expectedPage2Count, page2.size(), "Page 2 should have correct number of tasks");
        
        List<UploadTask> page3 = uploader.getInflightTasks(3, pageSize);
        assertEquals(expectedPage3Count, page3.size(), "Page 3 should have correct number of tasks");
        
        List<UploadTask> page4 = uploader.getInflightTasks(4, pageSize);
        assertTrue(page4.isEmpty(), "Page 4 should be empty");
    }

    @Test
    void testGetInflightTasks_PaginationConsistency() throws IOException {
        int taskCount = 30;
        int pageSize = 10;
        createTestTasks(taskCount);
        
        int totalPages = (int) Math.ceil((double) taskCount / pageSize);
        int totalRetrieved = 0;
        
        for (int page = 1; page <= totalPages; page++) {
            List<UploadTask> tasks = uploader.getInflightTasks(page, pageSize);
            totalRetrieved += tasks.size();
        }
        
        assertEquals(taskCount, totalRetrieved, "Total tasks retrieved across all pages should match total tasks");
    }

    @Test
    void testGetAllInflightTasks_MultipleTasks() throws IOException {
        int taskCount = 15;
        createTestTasks(taskCount);
        
        List<UploadTask> allTasks = uploader.getAllInflightTasks();
        assertEquals(taskCount, allTasks.size(), "Should return all tasks");
        
        for (UploadTask task : allTasks) {
            assertNotNull(task.getLocalFilePath());
            assertNotNull(task.getRemoteTargetPath());
            assertNotNull(task.getTransferId());
            assertNotNull(task.getTraceId());
        }
    }

    @Test
    void testGetInflightTasksCount_MultipleTasks() throws IOException {
        int taskCount = 20;
        createTestTasks(taskCount);
        
        int count = uploader.getInflightTasksCount();
        assertEquals(taskCount, count, "Inflight tasks count should match created tasks");
    }

    @Test
    void testGetInflightTasks_GetAllConsistency() throws IOException {
        int taskCount = 12;
        createTestTasks(taskCount);
        
        List<UploadTask> allTasks = uploader.getAllInflightTasks();
        List<UploadTask> pagedTasks = uploader.getInflightTasks(1, taskCount);
        
        assertEquals(allTasks.size(), pagedTasks.size(), 
                "getAllInflightTasks and getInflightTasks should return same number of tasks");
    }

    @Test
    void testGetInflightTasks_TaskFields() throws IOException {
        createTestTasks(1);
        
        List<UploadTask> tasks = uploader.getInflightTasks(1, 10);
        assertEquals(1, tasks.size());
        
        UploadTask task = tasks.get(0);
        assertNotNull(task.getCreateTime(), "Task should have createTime");
        assertNotNull(task.getUpdateTime(), "Task should have updateTime");
        assertNotNull(task.getEnqueuedTime(), "Task should have enqueuedTime");
        assertNotNull(task.getListenerClassName(), "Task should have listenerClassName");
        assertNotNull(task.getStatus(), "Task should have status");
        assertTrue(task.getTotalSize() > 0, "Task should have positive totalSize");
    }

    private void createTestTasks(int count) throws IOException {
        for (int i = 0; i < count; i++) {
            String localPath = tempDir.resolve("test_file_" + i + ".txt").toString();
            String remotePath = "/remote/test_file_" + i + ".txt";
            
            Files.writeString(Path.of(localPath), "Test content " + i);
            
            UploadTask task = new UploadTask(localPath, remotePath, 100,  "http://127.0.0.1:8080/", "admin");
            task.setTransferId("transfer-" + i);
            task.setTraceId("trace-" + i);
            task.setListenerClassName("com.cq.agent.client.upload.TestUploadListener");
            task.setEnqueuedTime("2026-03-23 10:00:00.000");
            task.updateTimestamp();
            
            taskInflightMap.put(task.getTransferId(), task);
        }
    }

    private PersistentMap<String, UploadTask> getTaskInflightMap(AgentUploader uploader) throws Exception {
        Field field = findFieldInHierarchy(AgentUploader.class, "taskInflightMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        PersistentMap<String, UploadTask> map = (PersistentMap<String, UploadTask>) field.get(uploader);
        return map;
    }

    private Field findFieldInHierarchy(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found in hierarchy of " + clazz.getName());
    }

    static class TestUploadListener implements UploadListener {
        @Override
        public void onProgress(int total, int uploaded, double progress) {
        }

        @Override
        public void onComplete(UploadTask result) {
        }

        @Override
        public void onError(String message) {
        }
    }
}