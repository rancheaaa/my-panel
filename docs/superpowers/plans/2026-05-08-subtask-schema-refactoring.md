# batch_transfer_subtask Schema Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `batch_transfer_subtask` table: remove `file_path`, add `source_agent_id`, `source_agent_name`, `target_agent_name`, `source_path`, `target_path`. Proxy enriches at write-time, admin queries directly.

**Architecture:** Proxy looks up agent names from `agent_registry` and constructs full paths from `source_dir`/`target_dir` + `relativePath` when creating subtasks. Admin no longer needs to resolve node names via JOINs.

**Tech Stack:** Spring Boot, JdbcTemplate, MyBatis XML mapper, MySQL/H2, React 19, Ant Design 6

---

## File Structure

| Module | File | Action | Purpose |
|--------|------|--------|---------|
| admin | `src/main/resources/sql/schema.sql` | Modify | Update `batch_transfer_subtask` table |
| admin | `repository/domain/BatchTransferSubtask.java` | Modify | Update entity fields |
| admin | `src/main/resources/mapper/batch/BatchTransferSubtaskMapper.xml` | Modify | Update all SQL |
| admin | `web/controller/batch/BatchTransferController.java` | Modify | Simplify listSubtasks enrichment |
| proxy | `service/batch/BatchTaskScheduler.java` | Modify | Enrich subtask data at creation |
| proxy | `web/controller/batch/BatchInternalController.java` | Modify | Enrich subtask data, update lookup key |
| proxy | `service/batch/RetryScheduler.java` | Modify | Update SELECT and dispatch fields |
| proxy | `service/batch/ProgressAggregator.java` | Modify | Update composite key |
| frontend | `components/batch/SubtaskTable.jsx` | Modify | Use new fields for display |
| frontend | `components/batch/FileProgressBar.jsx` | Modify | Update tooltip |

---

### Task 1: Update Schema

**Files:**
- Modify: `my-panel-admin/src/main/resources/sql/schema.sql:812-848`

- [ ] **Step 1: Replace `batch_transfer_subtask` table definition**

Replace the entire CREATE TABLE statement (lines 812-848) with:

```sql
CREATE TABLE IF NOT EXISTS `batch_transfer_subtask` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` bigint NOT NULL COMMENT '关联的批量任务ID',
    `source_agent_id` varchar(50) NOT NULL COMMENT '源Agent ID',
    `source_agent_name` varchar(100) DEFAULT NULL COMMENT '源节点名称(Proxy补全)',
    `target_agent_id` varchar(50) NOT NULL COMMENT '目标Agent ID',
    `target_agent_name` varchar(100) DEFAULT NULL COMMENT '目标节点名称(Proxy补全)',
    `source_path` varchar(1000) NOT NULL COMMENT '源文件完整路径(sourceDir+relativePath)',
    `target_path` varchar(1000) NOT NULL COMMENT '目标文件完整路径(targetDir+relativePath)',
    `file_name` varchar(255) NOT NULL COMMENT '文件名(纯文件名,不含路径)',
    `file_size_bytes` bigint NOT NULL COMMENT '文件大小(字节)',
    `file_md5` char(32) DEFAULT NULL COMMENT '文件MD5校验值(32位十六进制)',
    `file_last_modified` datetime DEFAULT NULL COMMENT '文件最后修改时间',
    `status` varchar(20) NOT NULL DEFAULT 'QUEUED' COMMENT '子任务状态: QUEUED/SENDING/COMPLETED/FAILED/RETRYING/CANCELLED, 设计类型:ENUM',
    `transfer_id` varchar(100) DEFAULT NULL COMMENT '底层分块传输会话ID(关联AgentUploader的transferId)',
    `transferred_chunks` int NOT NULL DEFAULT 0 COMMENT '已传输的分块数',
    `total_chunks` int NOT NULL DEFAULT 0 COMMENT '总分块数',
    `transferred_bytes` bigint NOT NULL DEFAULT 0 COMMENT '已传输字节数',
    `speed_bytes_per_sec` bigint DEFAULT NULL COMMENT '当前传输速率(字节/秒)',
    `started_at` datetime DEFAULT NULL COMMENT '开始传输时间',
    `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
    `duration_ms` bigint DEFAULT NULL COMMENT '传输耗时(毫秒)',
    `error_code` varchar(50) DEFAULT NULL COMMENT '错误码',
    `error_message` text DEFAULT NULL COMMENT '错误详情',
    `error_stack_trace` text DEFAULT NULL COMMENT '异常堆栈(调试用)',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT 'Agent本地重试次数',
    `proxy_retry_count` int NOT NULL DEFAULT 0 COMMENT 'Proxy调度层重试次数',
    `last_retry_at` datetime DEFAULT NULL COMMENT '最后一次重试时间',
    `next_retry_after` datetime DEFAULT NULL COMMENT '下次可重试时间(Level 2)',
    `create_by` varchar(64) DEFAULT '' COMMENT '创建人(系统自动)',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新人(系统自动)',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_task_source_target` (`task_id`, `source_path`(255), `target_agent_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_target_status` (`target_agent_id`, `status`),
    KEY `idx_status_retry` (`status`, `next_retry_after`),
    KEY `idx_transfer_id` (`transfer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量子任务表(文件×目标Agent的笛卡尔积)';
```

- [ ] **Step 2: Compile admin module**

Run: `mvn clean compile -pl my-panel-admin -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add my-panel-admin/src/main/resources/sql/schema.sql
git commit -m "feat: refactor batch_transfer_subtask schema - remove file_path, add source/target fields"
```

---

### Task 2: Update Entity and Mapper

**Files:**
- Modify: `my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferSubtask.java`
- Modify: `my-panel-admin/src/main/resources/mapper/batch/BatchTransferSubtaskMapper.xml`

- [ ] **Step 1: Update entity class**

Replace the entity class with:

```java
package com.cq.panel.admin.server.repository.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class BatchTransferSubtask extends BaseEntity
{
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long taskId;
    private String sourceAgentId;
    private String sourceAgentName;
    private String targetAgentId;
    private String targetAgentName;
    private String sourcePath;
    private String targetPath;
    private String fileName;
    private Long fileSizeBytes;
    private String fileMd5;
    private Date fileLastModified;
    private String status;
    private String transferId;
    private Integer transferredChunks;
    private Integer totalChunks;
    private Long transferredBytes;
    private Long speedBytesPerSec;
    private Date startedAt;
    private Date completedAt;
    private Long durationMs;
    private String errorCode;
    private String errorMessage;
    private String errorStackTrace;
    private Integer retryCount;
    private Integer proxyRetryCount;
    private Date lastRetryAt;
    private Date nextRetryAfter;
}
```

- [ ] **Step 2: Update mapper XML**

Replace the entire `BatchTransferSubtaskMapper.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.cq.panel.admin.server.repository.mapper.BatchTransferSubtaskMapper">

    <resultMap type="BatchTransferSubtask" id="BatchTransferSubtaskResult">
        <result property="id"    column="id"    />
        <result property="taskId"    column="task_id"    />
        <result property="sourceAgentId"    column="source_agent_id"    />
        <result property="sourceAgentName"    column="source_agent_name"    />
        <result property="targetAgentId"    column="target_agent_id"    />
        <result property="targetAgentName"    column="target_agent_name"    />
        <result property="sourcePath"    column="source_path"    />
        <result property="targetPath"    column="target_path"    />
        <result property="fileName"    column="file_name"    />
        <result property="fileSizeBytes"    column="file_size_bytes"    />
        <result property="fileMd5"    column="file_md5"    />
        <result property="fileLastModified"    column="file_last_modified"    />
        <result property="status"    column="status"    />
        <result property="transferId"    column="transfer_id"    />
        <result property="transferredChunks"    column="transferred_chunks"    />
        <result property="totalChunks"    column="total_chunks"    />
        <result property="transferredBytes"    column="transferred_bytes"    />
        <result property="speedBytesPerSec"    column="speed_bytes_per_sec"    />
        <result property="startedAt"    column="started_at"    />
        <result property="completedAt"    column="completed_at"    />
        <result property="durationMs"    column="duration_ms"    />
        <result property="errorCode"    column="error_code"    />
        <result property="errorMessage"    column="error_message"    />
        <result property="errorStackTrace"    column="error_stack_trace"    />
        <result property="retryCount"    column="retry_count"    />
        <result property="proxyRetryCount"    column="proxy_retry_count"    />
        <result property="lastRetryAt"    column="last_retry_at"    />
        <result property="nextRetryAfter"    column="next_retry_after"    />
        <result property="createBy"    column="create_by"    />
        <result property="createTime"    column="create_time"    />
        <result property="updateBy"    column="update_by"    />
        <result property="updateTime"    column="update_time"    />
        <result property="remark"    column="remark"    />
    </resultMap>

    <sql id="selectBatchTransferSubtaskVo">
        select id, task_id, source_agent_id, source_agent_name, target_agent_id, target_agent_name,
               source_path, target_path, file_name, file_size_bytes, file_md5, file_last_modified,
               status, transfer_id, transferred_chunks, total_chunks, transferred_bytes,
               speed_bytes_per_sec, started_at, completed_at, duration_ms, error_code, error_message,
               error_stack_trace, retry_count, proxy_retry_count, last_retry_at, next_retry_after,
               create_by, create_time, update_by, update_time, remark
        from batch_transfer_subtask
    </sql>

    <select id="selectBatchTransferSubtaskById" parameterType="Long" resultMap="BatchTransferSubtaskResult">
        <include refid="selectBatchTransferSubtaskVo"/>
        where id = #{id}
    </select>

    <select id="selectBatchTransferSubtaskList" parameterType="BatchTransferSubtask" resultMap="BatchTransferSubtaskResult">
        <include refid="selectBatchTransferSubtaskVo"/>
        <where>
            <if test="taskId != null"> and task_id = #{taskId}</if>
            <if test="status != null and status != ''"> and status = #{status}</if>
            <if test="targetAgentId != null and targetAgentId != ''"> and target_agent_id = #{targetAgentId}</if>
            <if test="fileName != null and fileName != ''"> and file_name like concat('%', #{fileName}, '%')</if>
        </where>
        order by create_time desc
    </select>

    <select id="selectSubtasksByTaskId" parameterType="Long" resultMap="BatchTransferSubtaskResult">
        <include refid="selectBatchTransferSubtaskVo"/>
        where task_id = #{taskId} order by create_time desc
    </select>

    <select id="selectSubtasksByTaskIdAndStatus" resultMap="BatchTransferSubtaskResult">
        <include refid="selectBatchTransferSubtaskVo"/>
        where task_id = #{taskId} and status = #{status}
    </select>

    <select id="selectFailedSubtasksForRetry" resultMap="BatchTransferSubtaskResult">
        <include refid="selectBatchTransferSubtaskVo"/>
        where status = 'FAILED' and next_retry_after is not null and next_retry_after &lt;= #{beforeTime}
    </select>

    <select id="selectSubtaskSummaryByTaskId" parameterType="Long" resultType="map">
        select status as status, count(*) as count from batch_transfer_subtask where task_id = #{taskId} group by status
    </select>

    <insert id="insertBatchTransferSubtask" parameterType="BatchTransferSubtask" useGeneratedKeys="true" keyProperty="id">
        insert into batch_transfer_subtask
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="taskId != null">task_id,</if>
            <if test="sourceAgentId != null and sourceAgentId != ''">source_agent_id,</if>
            <if test="sourceAgentName != null and sourceAgentName != ''">source_agent_name,</if>
            <if test="targetAgentId != null and targetAgentId != ''">target_agent_id,</if>
            <if test="targetAgentName != null and targetAgentName != ''">target_agent_name,</if>
            <if test="sourcePath != null and sourcePath != ''">source_path,</if>
            <if test="targetPath != null and targetPath != ''">target_path,</if>
            <if test="fileName != null and fileName != ''">file_name,</if>
            <if test="fileSizeBytes != null">file_size_bytes,</if>
            <if test="fileMd5 != null">file_md5,</if>
            <if test="fileLastModified != null">file_last_modified,</if>
            <if test="status != null">status,</if>
            <if test="createBy != null">create_by,</if>
            <if test="createTime != null">create_time,</if>
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="taskId != null">#{taskId},</if>
            <if test="sourceAgentId != null and sourceAgentId != ''">#{sourceAgentId},</if>
            <if test="sourceAgentName != null and sourceAgentName != ''">#{sourceAgentName},</if>
            <if test="targetAgentId != null and targetAgentId != ''">#{targetAgentId},</if>
            <if test="targetAgentName != null and targetAgentName != ''">#{targetAgentName},</if>
            <if test="sourcePath != null and sourcePath != ''">#{sourcePath},</if>
            <if test="targetPath != null and targetPath != ''">#{targetPath},</if>
            <if test="fileName != null and fileName != ''">#{fileName},</if>
            <if test="fileSizeBytes != null">#{fileSizeBytes},</if>
            <if test="fileMd5 != null">#{fileMd5},</if>
            <if test="fileLastModified != null">#{fileLastModified},</if>
            <if test="status != null">#{status},</if>
            <if test="createBy != null">#{createBy},</if>
            <if test="createTime != null">#{createTime},</if>
        </trim>
    </insert>

    <insert id="batchInsertSubtasks" parameterType="java.util.List">
        insert into batch_transfer_subtask (task_id, source_agent_id, source_agent_name, target_agent_id, target_agent_name,
            source_path, target_path, file_name, file_size_bytes, file_md5, file_last_modified, status, create_time)
        values
        <foreach item="item" collection="list" separator=",">
            (#{item.taskId}, #{item.sourceAgentId}, #{item.sourceAgentName}, #{item.targetAgentId}, #{item.targetAgentName},
             #{item.sourcePath}, #{item.targetPath}, #{item.fileName}, #{item.fileSizeBytes}, #{item.fileMd5}, #{item.fileLastModified}, #{item.status}, #{item.createTime})
        </foreach>
    </insert>

    <update id="updateBatchTransferSubtask" parameterType="BatchTransferSubtask">
        update batch_transfer_subtask
        <trim prefix="SET" suffixOverrides=",">
            <if test="status != null">status = #{status},</if>
            <if test="transferId != null">transfer_id = #{transferId},</if>
            <if test="transferredChunks != null">transferred_chunks = #{transferredChunks},</if>
            <if test="totalChunks != null">total_chunks = #{totalChunks},</if>
            <if test="transferredBytes != null">transferred_bytes = #{transferredBytes},</if>
            <if test="speedBytesPerSec != null">speed_bytes_per_sec = #{speedBytesPerSec},</if>
            <if test="startedAt != null">started_at = #{startedAt},</if>
            <if test="completedAt != null">completed_at = #{completedAt},</if>
            <if test="durationMs != null">duration_ms = #{durationMs},</if>
            <if test="errorCode != null">error_code = #{errorCode},</if>
            <if test="errorMessage != null">error_message = #{errorMessage},</if>
            <if test="errorStackTrace != null">error_stack_trace = #{errorStackTrace},</if>
            <if test="retryCount != null">retry_count = #{retryCount},</if>
            <if test="proxyRetryCount != null">proxy_retry_count = #{proxyRetryCount},</if>
            <if test="lastRetryAt != null">last_retry_at = #{lastRetryAt},</if>
            <if test="nextRetryAfter != null">next_retry_after = #{nextRetryAfter},</if>
            <if test="updateBy != null">update_by = #{updateBy},</if>
            <if test="updateTime != null">update_time = #{updateTime},</if>
        </trim>
        where id = #{id}
    </update>

    <update id="updateSubtaskStatus">
        update batch_transfer_subtask set status = #{status}, update_time = CURRENT_TIMESTAMP where id = #{id}
    </update>

</mapper>
```

- [ ] **Step 3: Compile admin module**

Run: `mvn clean compile -pl my-panel-admin -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add my-panel-admin/src/main/java/com/cq/panel/admin/server/repository/domain/BatchTransferSubtask.java
git add my-panel-admin/src/main/resources/mapper/batch/BatchTransferSubtaskMapper.xml
git commit -m "feat: update BatchTransferSubtask entity and mapper for new schema"
```

---

### Task 3: Update Proxy - BatchTaskScheduler

**Files:**
- Modify: `my-panel-proxy/src/main/java/com/cq/proxy/service/batch/BatchTaskScheduler.java`

- [ ] **Step 1: Update `generateSubtasks()` method**

Replace the `generateSubtasks` method (lines 311-349). The key changes:
1. Accept `sourceAgentId` and `sourceDir` parameters
2. Look up agent names from `agent_registry`
3. Build `source_path` and `target_path`
4. Put new fields in subtask map

Replace the method signature at line 311 and the entire method body:

```java
private List<Map<String, Object>> generateSubtasks(Long taskId,
                                                   String sourceAgentId,
                                                   String sourceDir,
                                                   List<Map<String, Object>> scannedFiles,
                                                   Map<String, List<String>> resolvedTargets,
                                                   Map<String, String> agentDirMap,
                                                   Integer preserveDirStructure)
{
    // lookup agent names
    String sourceAgentName = lookupAgentName(sourceAgentId);
    Map<String, String> targetAgentNameCache = new java.util.LinkedHashMap<>();

    List<Map<String, Object>> subtasks = new ArrayList<>();
    long subtaskSeq = 1;
    for (Map<String, Object> file : scannedFiles)
    {
        String relativePath = String.valueOf(file.get("relativePath"));
        List<String> targets = resolvedTargets.getOrDefault(relativePath, List.of());
        for (String targetAgentId : targets)
        {
            Map<String, Object> subtask = new LinkedHashMap<>();
            subtask.put("subtaskId", subtaskSeq++);
            subtask.put("taskId", taskId);
            subtask.put("sourceAgentId", sourceAgentId);
            subtask.put("sourceAgentName", sourceAgentName);
            subtask.put("targetAgentId", targetAgentId);
            subtask.put("targetAgentName", targetAgentNameCache.computeIfAbsent(targetAgentId, this::lookupAgentName));
            subtask.put("filePath", relativePath);
            subtask.put("fileName", relativePath.contains("/") ? relativePath.substring(relativePath.lastIndexOf("/") + 1) : relativePath);
            subtask.put("sourcePath", buildSourcePath(sourceDir, relativePath));
            subtask.put("targetPath", buildTargetPath(agentDirMap.getOrDefault(targetAgentId, "/tmp"), relativePath, subtask.get("fileName"), preserveDirStructure));
            subtask.put("fileSizeBytes", file.get("sizeBytes"));
            if (file.get("md5") != null) subtask.put("fileMd5", String.valueOf(file.get("md5")));
            if (file.get("lastModified") != null) subtask.put("fileLastModified", file.get("lastModified"));
            subtask.put("targetDir", agentDirMap.getOrDefault(targetAgentId, "/tmp"));
            subtask.put("priority", 5);

            String targetApiUrl = resolveTargetAgentUrl(targetAgentId);
            if (targetApiUrl == null)
            {
                logger.warn("Skipping subtask for file={}, targetAgent[{}] - agent URL could not be resolved or agent is offline",
                        relativePath, targetAgentId);
                continue;
            }
            subtask.put("targetAgentApiUrl", targetApiUrl);

            subtasks.add(subtask);
        }
    }
    return subtasks;
}
```

- [ ] **Step 2: Add helper methods**

Add these methods after the `buildAgentDirMap` method (after line 388):

```java
private String lookupAgentName(String agentId)
{
    if (agentId == null) return null;
    try
    {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT node_name FROM agent_registry WHERE id = ?", agentId);
        return (String) row.get("node_name");
    }
    catch (Exception e)
    {
        logger.debug("Failed to lookup agent name for {}: {}", agentId, e.getMessage());
        return agentId;
    }
}

private String buildSourcePath(String sourceDir, String relativePath)
{
    if (sourceDir == null || sourceDir.isEmpty()) return relativePath;
    if (sourceDir.endsWith("/")) return sourceDir + relativePath;
    return sourceDir + "/" + relativePath;
}

private String buildTargetPath(String targetDir, String relativePath, Object fileName, Integer preserveDirStructure)
{
    if (targetDir == null) targetDir = "/tmp";
    String base = targetDir.endsWith("/") ? targetDir : targetDir + "/";
    if (preserveDirStructure != null && preserveDirStructure == 1)
    {
        return base + relativePath;
    }
    return base + (fileName != null ? fileName : relativePath);
}
```

- [ ] **Step 3: Update `startTask()` call to `generateSubtasks()`**

In the `startTask` method, update the call at line 132 from:

```java
List<Map<String, Object>> subtasks = generateSubtasks(taskId, files, resolvedTargets, agentDirMap);
```

to:

```java
List<Map<String, Object>> subtasks = generateSubtasks(taskId, sourceAgentId, sourceDir, files, resolvedTargets, agentDirMap, preserveDirStructure);
```

Also add `sourceDir` extraction before the call. Add after line 131:

```java
String sourceDir = null;
if (scanRequest instanceof Map)
{
    sourceDir = (String) ((Map<?, ?>) scanRequest).get("baseDir");
}
```

And change `preserveDirStructure` from `Integer` to be available in scope. The method already receives it as a parameter.

- [ ] **Step 4: Update `persistSubtasks()` method**

In the `persistSubtasks` method (lines 229-273), update the INSERT SQL to include new fields. Replace lines 245-259:

```java
if (fileMd5 != null || fileLastModified != null) {
    jdbcTemplate.update(
            "INSERT INTO batch_transfer_subtask (task_id, source_agent_id, source_agent_name, " +
                    "target_agent_id, target_agent_name, source_path, target_path, file_name, file_size_bytes, " +
                    "status, transferred_chunks, total_chunks, transferred_bytes, " +
                    "retry_count, proxy_retry_count, create_time, update_time, file_md5, file_last_modified) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 0, 0, 0, 0, NOW(), NOW(), ?, ?)",
            taskId,
            subtask.get("sourceAgentId"),
            subtask.get("sourceAgentName"),
            subtask.get("targetAgentId"),
            subtask.get("targetAgentName"),
            subtask.get("sourcePath"),
            subtask.get("targetPath"),
            fileName, fileSizeBytes, fileMd5, fileLastModified);
} else {
    jdbcTemplate.update(
            "INSERT INTO batch_transfer_subtask (task_id, source_agent_id, source_agent_name, " +
                    "target_agent_id, target_agent_name, source_path, target_path, file_name, file_size_bytes, " +
                    "status, transferred_chunks, total_chunks, transferred_bytes, " +
                    "retry_count, proxy_retry_count, create_time, update_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 0, 0, 0, 0, NOW(), NOW())",
            taskId,
            subtask.get("sourceAgentId"),
            subtask.get("sourceAgentName"),
            subtask.get("targetAgentId"),
            subtask.get("targetAgentName"),
            subtask.get("sourcePath"),
            subtask.get("targetPath"),
            fileName, fileSizeBytes);
}
```

- [ ] **Step 5: Compile proxy module**

Run: `mvn clean compile -pl my-panel-proxy -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add my-panel-proxy/src/main/java/com/cq/proxy/service/batch/BatchTaskScheduler.java
git commit -m "feat: enrich subtask creation with source/target agent info and full paths"
```

---

### Task 4: Update Proxy - BatchInternalController

**Files:**
- Modify: `my-panel-proxy/src/main/java/com/cq/proxy/web/controller/batch/BatchInternalController.java`

- [ ] **Step 1: Update `persistSubtasks()` endpoint**

Replace the `persistSubtasks` method (lines 70-134). Key changes:
1. Extract new fields from subtask map
2. Update INSERT SQL
3. Change lookup key from `file_path` to `source_path`

```java
@Operation(summary = "持久化子任务到数据库(定时扫描用)")
@PostMapping("/subtasks/persist")
public Map<String, Object> persistSubtasks(@RequestBody Map<String, Object> request)
{
    try
    {
        Number taskIdNum = request.get("taskId") != null ? ((Number) request.get("taskId")) : null;
        if (taskIdNum == null)
        {
            return Map.of("success", false, "message", "taskId is required");
        }
        Long taskId = taskIdNum.longValue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subtasks = (List<Map<String, Object>>) request.get("subtasks");
        if (subtasks == null || subtasks.isEmpty())
        {
            return Map.of("success", true, "message", "no subtasks to persist", "persistedSubtasks", List.of());
        }

        List<Map<String, Object>> persisted = new java.util.ArrayList<>();
        for (Map<String, Object> st : subtasks)
        {
            String sourceAgentId = st.get("sourceAgentId") != null ? String.valueOf(st.get("sourceAgentId")) : null;
            String sourceAgentName = st.get("sourceAgentName") != null ? String.valueOf(st.get("sourceAgentName")) : null;
            String targetAgentId = st.get("targetAgentId") != null ? String.valueOf(st.get("targetAgentId")) : null;
            String targetAgentName = st.get("targetAgentName") != null ? String.valueOf(st.get("targetAgentName")) : null;
            String sourcePath = st.get("sourcePath") != null ? String.valueOf(st.get("sourcePath")) : null;
            String targetPath = st.get("targetPath") != null ? String.valueOf(st.get("targetPath")) : null;
            String filePath = st.get("filePath") != null ? String.valueOf(st.get("filePath")) : null;
            String fileName = st.get("fileName") != null ? String.valueOf(st.get("fileName")) :
                    (filePath != null && filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath);
            long fileSizeBytes = st.get("fileSizeBytes") instanceof Number ? ((Number) st.get("fileSizeBytes")).longValue() : 0L;
            Object fileMd5Obj = st.get("fileMd5");
            String fileMd5 = fileMd5Obj != null ? String.valueOf(fileMd5Obj) : null;
            Object fileLastModifiedObj = st.get("fileLastModified");
            java.sql.Timestamp fileLastModified = fileLastModifiedObj instanceof java.util.Date ?
                    new java.sql.Timestamp(((java.util.Date) fileLastModifiedObj).getTime()) :
                    (fileLastModifiedObj instanceof Number ? new java.sql.Timestamp(((Number) fileLastModifiedObj).longValue()) : null);

            int rows = jdbcTemplate.update(
                    "INSERT INTO batch_transfer_subtask (task_id, source_agent_id, source_agent_name, " +
                            "target_agent_id, target_agent_name, source_path, target_path, file_name, file_size_bytes, " +
                            "status, transferred_chunks, total_chunks, transferred_bytes, " +
                            "retry_count, proxy_retry_count, create_time, update_time, file_md5, file_last_modified) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 0, 0, 0, 0, NOW(), NOW(), ?, ?) " +
                            "ON DUPLICATE KEY UPDATE status='QUEUED', transferred_chunks=0, total_chunks=0, " +
                            "transferred_bytes=0, retry_count=0, proxy_retry_count=0, update_time=NOW(), " +
                            "file_md5 = COALESCE(?, file_md5), file_last_modified = COALESCE(?, file_last_modified)",
                    taskId, sourceAgentId, sourceAgentName, targetAgentId, targetAgentName,
                    sourcePath, targetPath, fileName, fileSizeBytes, fileMd5, fileLastModified,
                    fileMd5, fileLastModified);

            Long id = rows > 0 ? jdbcTemplate.queryForObject(
                    "SELECT id FROM batch_transfer_subtask WHERE task_id=? AND source_path=? AND target_agent_id=? ORDER BY id DESC LIMIT 1",
                    Long.class, taskId, sourcePath, targetAgentId) : null;

            Map<String, Object> persistedSt = new java.util.LinkedHashMap<>(st);
            persistedSt.put("id", id);
            persistedSt.put("subtaskId", id);
            persisted.add(persistedSt);
        }

        logger.info("Persisted {} subtasks for task {}", persisted.size(), taskId);
        return Map.of("success", true, "taskId", taskId, "persistedSubtasks", persisted);
    }
    catch (Exception e)
    {
        logger.error("Failed to persist subtasks: {}", e.getMessage(), e);
        return Map.of("success", false, "message", e.getMessage());
    }
}
```

- [ ] **Step 2: Compile proxy module**

Run: `mvn clean compile -pl my-panel-proxy -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add my-panel-proxy/src/main/java/com/cq/proxy/web/controller/batch/BatchInternalController.java
git commit -m "feat: update BatchInternalController for new subtask schema"
```

---

### Task 5: Update Proxy - RetryScheduler and ProgressAggregator

**Files:**
- Modify: `my-panel-proxy/src/main/java/com/cq/proxy/service/batch/RetryScheduler.java`
- Modify: `my-panel-proxy/src/main/java/com/cq/proxy/service/batch/ProgressAggregator.java`

- [ ] **Step 1: Update RetryScheduler SELECT and dispatch**

In `RetryScheduler.java`, replace the SQL at line 39:

```java
String sql = "SELECT id, task_id, source_agent_id, source_agent_name, source_path, target_path, " +
        "file_name, file_size_bytes, target_agent_id, target_agent_name, " +
        "retry_count, proxy_retry_count FROM batch_transfer_subtask " +
        "WHERE status = 'FAILED' AND next_retry_after <= NOW() " +
        "AND task_id IN (SELECT id FROM batch_transfer_task WHERE status = 'RUNNING' AND retry_enabled = 1) " +
        "LIMIT 100";
```

Update the dispatch item construction (lines 76-84):

```java
Map<String, Object> retryItem = new java.util.LinkedHashMap<>();
retryItem.put("subtaskId", subtaskId);
retryItem.put("taskId", taskId);
retryItem.put("filePath", subtask.get("source_path"));
retryItem.put("fileName", subtask.get("file_name"));
retryItem.put("fileSizeBytes", subtask.get("file_size_bytes"));
retryItem.put("targetAgentId", targetAgentId);
retryItem.put("targetDir", agentTargetDir);
retryItem.put("sourceAgentId", subtask.get("source_agent_id"));
retryItem.put("sourceAgentName", subtask.get("source_agent_name"));
retryItem.put("targetAgentName", subtask.get("target_agent_name"));
retryItem.put("sourcePath", subtask.get("source_path"));
retryItem.put("targetPath", subtask.get("target_path"));
retryItem.put("priority", 5);
```

- [ ] **Step 2: Update ProgressAggregator composite key**

In `ProgressAggregator.java`, replace the `buildReportKey` method (lines 284-295):

```java
private String buildReportKey(Map<String, Object> report)
{
    Object subtaskId = report.get("subtaskId");
    if (subtaskId != null)
    {
        return "subtask:" + subtaskId;
    }
    Object taskId = report.get("taskId");
    Object filePath = report.get("filePath");
    Object sourcePath = report.get("sourcePath");
    Object targetAgentId = report.get("targetAgentId");
    String path = sourcePath != null ? sourcePath : filePath;
    return String.format("task:%s|target:%s|file:%s", taskId, targetAgentId, path);
}
```

- [ ] **Step 3: Compile proxy module**

Run: `mvn clean compile -pl my-panel-proxy -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add my-panel-proxy/src/main/java/com/cq/proxy/service/batch/RetryScheduler.java
git add my-panel-proxy/src/main/java/com/cq/proxy/service/batch/ProgressAggregator.java
git commit -m "feat: update RetryScheduler and ProgressAggregator for new subtask fields"
```

---

### Task 6: Simplify Admin Controller

**Files:**
- Modify: `my-panel-admin/src/main/java/com/cq/panel/admin/server/web/controller/batch/BatchTransferController.java`

- [ ] **Step 1: Simplify `listSubtasks()` method**

Replace lines 161-227 with a simplified version that uses subtask's own fields instead of resolving from external tables:

```java
@RequirePermission("batch:task:view")
@Operation(summary = "查询子任务列表")
@GetMapping("/{taskId}/subtasks")
public Result<Map<String, Object>> listSubtasks(@PathVariable Long taskId)
{
    List<BatchTransferSubtask> subtasks = batchTransferSubtaskService.selectByTaskId(taskId);

    BatchTransferTask task = batchTransferTaskService.selectById(taskId);
    if (task == null)
    {
        return Result.error("任务不存在");
    }

    Map<String, Object> result = new java.util.LinkedHashMap<>();
    result.put("subtasks", subtasks);

    // sourceAgentName直接从subtask获取，不再需要resolveNodeName
    String sourceNodeName = (subtasks != null && !subtasks.isEmpty() && subtasks.get(0).getSourceAgentName() != null)
            ? subtasks.get(0).getSourceAgentName() : task.getSourceAgentId();
    result.put("sourceDir", task.getSourceDir());
    result.put("sourceNodeName", sourceNodeName);

    // targetAgentInfoMap: dir信息仍来自task配置，nodeName直接从subtask获取
    Map<String, Map<String, String>> targetAgentInfoMap = buildTargetAgentInfoMap(
            task.getTargetAgents(), task.getTargetDirs());
    if (subtasks != null)
    {
        for (BatchTransferSubtask subtask : subtasks)
        {
            String agentId = subtask.getTargetAgentId();
            if (agentId != null && !agentId.isEmpty())
            {
                if (!targetAgentInfoMap.containsKey(agentId))
                {
                    Map<String, String> info = new java.util.LinkedHashMap<>();
                    info.put("dir", "-");
                    info.put("nodeName", subtask.getTargetAgentName() != null ? subtask.getTargetAgentName() : agentId);
                    targetAgentInfoMap.put(agentId, info);
                }
                else
                {
                    // 补充nodeName(如果task配置中没有)
                    Map<String, String> info = targetAgentInfoMap.get(agentId);
                    if (info.get("nodeName") == null || info.get("nodeName").equals("-"))
                    {
                        info.put("nodeName", subtask.getTargetAgentName() != null ? subtask.getTargetAgentName() : agentId);
                    }
                }
            }
        }
    }
    result.put("targetAgentInfoMap", targetAgentInfoMap);

    // 查询Agent侧传输状态, 以subtaskId为key
    try
    {
        List<Map<String, Object>> agentStates = jdbcTemplate.queryForList(
                "SELECT * FROM batch_transfer_agent_state WHERE task_id = ?", taskId);
        Map<Long, Map<String, Object>> agentStateMap = new java.util.LinkedHashMap<>();
        for (Map<String, Object> state : agentStates)
        {
            Object subtaskIdObj = state.get("subtask_id");
            if (subtaskIdObj instanceof Number)
            {
                agentStateMap.put(((Number) subtaskIdObj).longValue(), state);
            }
        }
        result.put("agentStateMap", agentStateMap);
    }
    catch (Exception e)
    {
        result.put("agentStateMap", Map.of());
    }

    return Result.success(result);
}
```

- [ ] **Step 2: Compile admin module**

Run: `mvn clean compile -pl my-panel-admin -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add my-panel-admin/src/main/java/com/cq/panel/admin/server/web/controller/batch/BatchTransferController.java
git commit -m "feat: simplify listSubtasks to use subtask's denormalized fields"
```

---

### Task 7: Update Frontend

**Files:**
- Modify: `my-panel-ui/src/components/batch/SubtaskTable.jsx`
- Modify: `my-panel-ui/src/components/batch/FileProgressBar.jsx`

- [ ] **Step 1: Update SubtaskTable columns**

In `SubtaskTable.jsx`, update the columns array (lines 89-118). Change the "发送节点" and "接收节点" columns to use subtask's own fields:

Replace lines 97-104:

```jsx
    { title: '发送节点', key: 'srcNode', width: 120, ellipsis: true,
      render: (_, r) => <Tooltip title={r.sourceAgentName || sourceNodeName}><span>{r.sourceAgentName || sourceNodeName}</span></Tooltip> },
    { title: '源路径', key: 'srcPath', width: 250, ellipsis: true,
      render: (_, r) => <Tooltip title={r.sourcePath}><span style={{ fontFamily: 'monospace', fontSize: 12 }}>{r.sourcePath || '-'}</span></Tooltip> },
    { title: '接收节点', key: 'tgtNode', width: 120, ellipsis: true,
      render: (_, r) => { const n = r.targetAgentName || getTargetNodeName(r.targetAgentId); return <Tooltip title={n}><span>{n}</span></Tooltip>; } },
    { title: '目标路径', key: 'tgtPath', width: 250, ellipsis: true,
      render: (_, r) => <Tooltip title={r.targetPath}><span style={{ fontFamily: 'monospace', fontSize: 12 }}>{r.targetPath || '-'}</span></Tooltip> },
```

- [ ] **Step 2: Update FileProgressBar tooltip**

In `FileProgressBar.jsx`, replace line 35:

```jsx
<Tooltip title={`${subtask.sourcePath || subtask.filePath} → ${subtask.targetPath || subtask.targetAgentId}`}>
```

And line 37:

```jsx
{subtask.fileName || subtask.sourcePath || subtask.filePath}
```

- [ ] **Step 3: Compile frontend**

Run: `cd my-panel-ui && npm run build 2>&1 | tail -5`
Expected: Build successful

- [ ] **Step 4: Commit**

```bash
git add my-panel-ui/src/components/batch/SubtaskTable.jsx
git add my-panel-ui/src/components/batch/FileProgressBar.jsx
git commit -m "feat: update frontend to use new subtask source/target fields"
```

---

### Task 8: Full Build Verification

- [ ] **Step 1: Compile all modules**

Run: `mvn clean compile -pl my-panel-admin,my-panel-proxy,agent -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 2: Build frontend**

Run: `cd my-panel-ui && npm run build`
Expected: Build successful
