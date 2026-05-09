# Agent Transfer State Reporting Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the enrichment of `batch_transfer_agent_state` (senderNodeName, receiverNodeName, taskName) from admin-side JOIN queries to proxy-side write-time denormalization.

**Architecture:** Agent reports raw transfer state to proxy. Proxy enriches with node names and task name by querying `agent_registry` and `batch_transfer_task` before upserting to DB. Admin and frontend query the table directly with no JOINs.

**Tech Stack:** Spring Boot, JdbcTemplate, MyBatis XML mapper, MySQL/H2

---

## File Structure

| Module | File | Action | Purpose |
|--------|------|--------|---------|
| admin | `src/main/resources/sql/schema.sql` | Modify | Add 3 columns to `batch_transfer_agent_state` |
| proxy | `web/controller/batch/BatchAdminController.java` | Modify | Add enrichment logic before upsert |
| admin | `src/main/resources/mapper/batch/BatchTransferAgentStateMapper.xml` | Modify | Remove LEFT JOINs, use simple SELECT |

---

### Task 1: Add Denormalized Columns to Schema

**Files:**
- Modify: `my-panel-admin/src/main/resources/sql/schema.sql:850-884`

- [ ] **Step 1: Add 3 columns to `batch_transfer_agent_state` table definition**

In `schema.sql`, add the following columns after `exception_desc` (line 865) and before `create_time_str` (line 866):

```sql
    `sender_node_name` varchar(100) DEFAULT NULL COMMENT '发送方节点名称(Proxy补全)',
    `receiver_node_name` varchar(100) DEFAULT NULL COMMENT '接收方节点名称(Proxy补全)',
    `task_name` varchar(200) DEFAULT NULL COMMENT '任务名称(Proxy补全)',
```

The full table definition should look like:

```sql
CREATE TABLE IF NOT EXISTS `batch_transfer_agent_state` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `agent_id` varchar(50) NOT NULL COMMENT 'Agent ID(来源Agent)',
    `transfer_id` varchar(100) NOT NULL COMMENT '传输会话ID',
    `subtask_id` bigint DEFAULT NULL COMMENT '关联的子任务ID',
    `task_id` bigint DEFAULT NULL COMMENT '关联的批量任务ID',
    `file_path` varchar(1000) DEFAULT NULL COMMENT '本地文件路径',
    `file_name` varchar(255) DEFAULT NULL COMMENT '文件名',
    `remote_target_path` varchar(1000) DEFAULT NULL COMMENT '远程目标路径',
    `status` varchar(30) NOT NULL DEFAULT 'PREPARED' COMMENT 'Agent侧传输状态',
    `total_size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    `chunk_size` int NOT NULL DEFAULT 0 COMMENT '分块大小(字节)',
    `total_chunks` int NOT NULL DEFAULT 0 COMMENT '总分块数',
    `transferred_chunks` int NOT NULL DEFAULT 0 COMMENT '已传输分块数',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
    `exception_desc` text DEFAULT NULL COMMENT '错误描述',
    `sender_node_name` varchar(100) DEFAULT NULL COMMENT '发送方节点名称(Proxy补全)',
    `receiver_node_name` varchar(100) DEFAULT NULL COMMENT '接收方节点名称(Proxy补全)',
    `task_name` varchar(200) DEFAULT NULL COMMENT '任务名称(Proxy补全)',
    `create_time_str` varchar(30) DEFAULT NULL COMMENT '任务创建时间(Agent侧)',
    `update_time_str` varchar(30) DEFAULT NULL COMMENT '最后更新时间(Agent侧)',
    `enqueued_time` varchar(30) DEFAULT NULL COMMENT '入队时间',
    `init_upload_start_time` varchar(30) DEFAULT NULL COMMENT '初始化上传开始时间',
    `init_upload_end_time` varchar(30) DEFAULT NULL COMMENT '初始化上传结束时间',
    `upload_chunks_start_time` varchar(30) DEFAULT NULL COMMENT '分块上传开始时间',
    `upload_chunks_end_time` varchar(30) DEFAULT NULL COMMENT '分块上传结束时间',
    `merge_chunks_start_time` varchar(30) DEFAULT NULL COMMENT '合并分块开始时间',
    `merge_chunks_end_time` varchar(30) DEFAULT NULL COMMENT '合并分块结束时间',
    `upload_success_time` varchar(30) DEFAULT NULL COMMENT '上传成功时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_id` (`transfer_id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_subtask_id` (`subtask_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent侧传输状态表(由Agent定期上报)';
```

- [ ] **Step 2: Compile admin module to verify schema syntax**

Run: `mvn clean compile -pl my-panel-admin -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add my-panel-admin/src/main/resources/sql/schema.sql
git commit -m "feat: add denormalized columns to batch_transfer_agent_state schema"
```

---

### Task 2: Add Enrichment Logic to Proxy

**Files:**
- Modify: `my-panel-proxy/src/main/java/com/cq/proxy/web/controller/batch/BatchAdminController.java:118-192`

- [ ] **Step 1: Replace `reportAgentState` method with enriched version**

Replace the `reportAgentState` method (lines 118-192) with the following implementation. The key changes:
1. Before the upsert loop, batch-query enrichment data from `agent_registry` and `batch_transfer_task`
2. Within the loop, resolve `receiverNodeName` per-transfer via subtask lookup
3. Add the 3 enriched fields to the INSERT and UPDATE statements

```java
@Operation(summary = "上报Agent侧传输状态")
@PostMapping("/agent-state")
public Map<String, Object> reportAgentState(@RequestBody Map<String, Object> request)
{
    String agentId = (String) request.get("agentId");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> transfers = (List<Map<String, Object>>) request.get("transfers");
    if (agentId == null || transfers == null || transfers.isEmpty())
    {
        return Map.of("success", false, "message", "agentId and transfers are required");
    }

    // --- enrichment: batch-query supplementary data ---
    // 1. sender node name (stable per agentId)
    String senderNodeName = null;
    try
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT node_name FROM agent_registry WHERE id = ?", agentId);
        if (!rows.isEmpty())
        {
            senderNodeName = (String) rows.get(0).get("node_name");
        }
    }
    catch (Exception e)
    {
        logger.debug("Failed to lookup sender node name for agent {}: {}", agentId, e.getMessage());
    }

    // 2. collect unique taskIds and lookup task names
    java.util.Map<Long, String> taskNameCache = new java.util.LinkedHashMap<>();
    java.util.Set<Long> subtaskIds = new java.util.LinkedHashSet<>();
    for (Map<String, Object> t : transfers)
    {
        Long taskId = t.get("taskId") != null ? ((Number) t.get("taskId")).longValue() : null;
        if (taskId != null && !taskNameCache.containsKey(taskId))
        {
            try
            {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT task_name FROM batch_transfer_task WHERE id = ?", taskId);
                taskNameCache.put(taskId, rows.isEmpty() ? null : (String) rows.get(0).get("task_name"));
            }
            catch (Exception e)
            {
                logger.debug("Failed to lookup task name for taskId {}: {}", taskId, e.getMessage());
                taskNameCache.put(taskId, null);
            }
        }
        // collect subtaskIds for receiver lookup
        String transferId = String.valueOf(t.get("transferId"));
        Long subtaskId = t.get("subtaskId") != null ? ((Number) t.get("subtaskId")).longValue() : parseSubtaskIdFromTransferId(transferId);
        if (subtaskId != null)
        {
            subtaskIds.add(subtaskId);
        }
    }

    // 3. lookup receiver node names via subtask -> target_agent_id -> agent_registry
    java.util.Map<Long, String> receiverNodeNameCache = new java.util.LinkedHashMap<>();
    if (!subtaskIds.isEmpty())
    {
        try
        {
            String placeholders = subtaskIds.stream().map(s -> "?").collect(java.util.stream.Collectors.joining(","));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT sub.id as subtask_id, ar.node_name as receiver_node_name " +
                    "FROM batch_transfer_subtask sub " +
                    "LEFT JOIN agent_registry ar ON ar.id = sub.target_agent_id " +
                    "WHERE sub.id IN (" + placeholders + ")",
                    subtaskIds.toArray());
            for (Map<String, Object> row : rows)
            {
                Long sid = ((Number) row.get("subtask_id")).longValue();
                receiverNodeNameCache.put(sid, (String) row.get("receiver_node_name"));
            }
        }
        catch (Exception e)
        {
            logger.debug("Failed to lookup receiver node names: {}", e.getMessage());
        }
    }
    // --- end enrichment ---

    int upserted = 0;
    for (Map<String, Object> t : transfers)
    {
        try
        {
            String transferId = String.valueOf(t.get("transferId"));
            if (transferId == null || transferId.isBlank()) continue;

            Long subtaskId = t.get("subtaskId") != null ? ((Number) t.get("subtaskId")).longValue() : parseSubtaskIdFromTransferId(transferId);
            Long taskId = t.get("taskId") != null ? ((Number) t.get("taskId")).longValue() : null;
            String taskName = taskId != null ? taskNameCache.get(taskId) : null;
            String receiverNodeName = subtaskId != null ? receiverNodeNameCache.get(subtaskId) : null;

            jdbcTemplate.update(
                    "INSERT INTO batch_transfer_agent_state " +
                            "(agent_id, transfer_id, subtask_id, task_id, file_path, file_name, remote_target_path, " +
                            "status, total_size, chunk_size, total_chunks, transferred_chunks, retry_count, exception_desc, " +
                            "sender_node_name, receiver_node_name, task_name, " +
                            "create_time_str, update_time_str, enqueued_time, " +
                            "init_upload_start_time, init_upload_end_time, " +
                            "upload_chunks_start_time, upload_chunks_end_time, " +
                            "merge_chunks_start_time, merge_chunks_end_time, upload_success_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "status=VALUES(status), total_size=VALUES(total_size), chunk_size=VALUES(chunk_size), " +
                            "total_chunks=VALUES(total_chunks), transferred_chunks=VALUES(transferred_chunks), " +
                            "retry_count=VALUES(retry_count), exception_desc=VALUES(exception_desc), " +
                            "sender_node_name=VALUES(sender_node_name), receiver_node_name=VALUES(receiver_node_name), " +
                            "task_name=VALUES(task_name), " +
                            "update_time_str=VALUES(update_time_str), " +
                            "upload_chunks_start_time=VALUES(upload_chunks_start_time), " +
                            "upload_chunks_end_time=VALUES(upload_chunks_end_time), " +
                            "merge_chunks_start_time=VALUES(merge_chunks_start_time), " +
                            "merge_chunks_end_time=VALUES(merge_chunks_end_time), " +
                            "upload_success_time=VALUES(upload_success_time)",
                    agentId, transferId, subtaskId, taskId,
                    (String) t.get("localFilePath"),
                    (String) t.get("fileName"),
                    (String) t.get("remoteTargetPath"),
                    t.get("status") != null ? String.valueOf(t.get("status")) : "UNKNOWN",
                    t.get("totalSize") != null ? ((Number) t.get("totalSize")).longValue() : 0L,
                    t.get("chunkSize") != null ? ((Number) t.get("chunkSize")).intValue() : 0,
                    t.get("totalChunks") != null ? ((Number) t.get("totalChunks")).intValue() : 0,
                    t.get("transferredChunks") != null ? ((Number) t.get("transferredChunks")).intValue() : 0,
                    t.get("retryCount") != null ? ((Number) t.get("retryCount")).intValue() : 0,
                    (String) t.get("exceptionDesc"),
                    senderNodeName, receiverNodeName, taskName,
                    (String) t.get("createTime"),
                    (String) t.get("updateTime"),
                    (String) t.get("enqueuedTime"),
                    (String) t.get("initUploadStartTime"),
                    (String) t.get("initUploadEndTime"),
                    (String) t.get("uploadChunksStartTime"),
                    (String) t.get("uploadChunksEndTime"),
                    (String) t.get("mergeChunksStartTime"),
                    (String) t.get("mergeChunksEndTime"),
                    (String) t.get("uploadSuccessTime")
            );
            upserted++;
        }
        catch (Exception e)
        {
            logger.warn("Failed to upsert agent state for transfer {}: {}", t.get("transferId"), e.getMessage());
        }
    }
    logger.debug("Agent[{}] reported {} transfer states, upserted={}", agentId, transfers.size(), upserted);
    return Map.of("success", true, "upserted", upserted);
}
```

- [ ] **Step 2: Compile proxy module**

Run: `mvn clean compile -pl my-panel-proxy -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add my-panel-proxy/src/main/java/com/cq/proxy/web/controller/batch/BatchAdminController.java
git commit -m "feat: add enrichment logic to proxy agent-state reporting"
```

---

### Task 3: Simplify Admin Mapper (Remove JOINs)

**Files:**
- Modify: `my-panel-admin/src/main/resources/mapper/batch/BatchTransferAgentStateMapper.xml`

- [ ] **Step 1: Replace `selectList` query with simple SELECT**

Replace lines 38-54 (the `selectList` query) with:

```xml
<select id="selectList" parameterType="BatchTransferAgentState" resultMap="BatchTransferAgentStateResult">
    select id, agent_id, transfer_id, subtask_id, task_id,
           file_path, file_name, remote_target_path, status,
           total_size, chunk_size, total_chunks, transferred_chunks,
           retry_count, exception_desc,
           sender_node_name, receiver_node_name, task_name,
           create_time_str, update_time_str,
           enqueued_time, init_upload_start_time, init_upload_end_time,
           upload_chunks_start_time, upload_chunks_end_time,
           merge_chunks_start_time, merge_chunks_end_time,
           upload_success_time, create_time, update_time
    from batch_transfer_agent_state
    <where>
        <if test="agentId != null and agentId != ''">and agent_id = #{agentId}</if>
        <if test="taskId != null">and task_id = #{taskId}</if>
        <if test="status != null and status != ''">and status = #{status}</if>
    </where>
    order by update_time desc
</select>
```

- [ ] **Step 2: Replace `selectByTaskId` query with simple SELECT**

Replace lines 56-68 (the `selectByTaskId` query) with:

```xml
<select id="selectByTaskId" parameterType="Long" resultMap="BatchTransferAgentStateResult">
    select id, agent_id, transfer_id, subtask_id, task_id,
           file_path, file_name, remote_target_path, status,
           total_size, chunk_size, total_chunks, transferred_chunks,
           retry_count, exception_desc,
           sender_node_name, receiver_node_name, task_name,
           create_time_str, update_time_str,
           enqueued_time, init_upload_start_time, init_upload_end_time,
           upload_chunks_start_time, upload_chunks_end_time,
           merge_chunks_start_time, merge_chunks_end_time,
           upload_success_time, create_time, update_time
    from batch_transfer_agent_state
    where task_id = #{taskId}
    order by update_time desc
</select>
```

- [ ] **Step 3: Compile admin module**

Run: `mvn clean compile -pl my-panel-admin -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add my-panel-admin/src/main/resources/mapper/batch/BatchTransferAgentStateMapper.xml
git commit -m "feat: remove JOINs from agent state mapper, use denormalized columns"
```

---

### Task 4: Full Build Verification

- [ ] **Step 1: Compile all modules (excluding distribution)**

Run: `mvn clean compile -pl my-panel-admin,my-panel-proxy,agent -DskipTests`
Expected: BUILD SUCCESS for all modules

- [ ] **Step 2: Commit any fixes if needed**

If compilation fails, fix and commit.