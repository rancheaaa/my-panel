# Agent Transfer State Reporting Optimization

## Background

The `batch_transfer_agent_state` table stores agent-side transfer state, reported every 15 seconds. Currently, the admin-side mapper enriches this data with 3 fields via LEFT JOINs on every query:

- `sender_node_name` (from `agent_registry` by `agent_id`)
- `receiver_node_name` (from `agent_registry` via `batch_transfer_subtask.target_agent_id`)
- `task_name` (from `batch_transfer_task` by `task_id`)

This design moves the enrichment responsibility to the proxy (write-time), so admin and frontend can use simple SELECT queries.

## Design Principles

1. Agent reports only data it owns (already backed by RocksDB-persisted `taskInflightMap`)
2. Proxy enriches with supplementary info at write-time
3. Admin and frontend query the table directly, no JOINs

## Architecture

```
Agent (RocksDB taskInflightMap)
  |
  |-- POST /api/v1/batch/agent-state -->  Proxy
  |                                         |
  |                                         |-- enrich: senderNodeName, receiverNodeName, taskName
  |                                         |-- INSERT ... ON DUPLICATE KEY UPDATE
  |                                         v
  |                                    batch_transfer_agent_state (denormalized)
  |                                         ^
  |                                         |
  Admin <-- simple SELECT * ---------------+
  Frontend <-- query via admin API --------+
```

## Schema Changes

Add 3 columns to `batch_transfer_agent_state`:

```
sender_node_name   varchar(100)   -- 发送方节点名称(从agent_registry查询)
receiver_node_name varchar(100)   -- 接收方节点名称(从agent_registry经subtask查询)
task_name          varchar(200)   -- 任务名称(从batch_transfer_task查询)
```

## Proxy Enrichment Logic

When `BatchAdminController.reportAgentState()` receives a report batch:

1. Collect all unique `agentId`, `taskId`, `subtaskId` from the report
2. Batch query enrichment data:
   - `agent_registry` by agentId -> nodeName (for sender)
   - `batch_transfer_task` by taskId -> taskName
   - `batch_transfer_subtask` by subtaskId -> targetAgentId, then `agent_registry` by targetAgentId -> nodeName (for receiver)
3. Cache results within the report batch to avoid N+1 queries
4. Include enriched fields in the INSERT ... ON DUPLICATE KEY UPDATE statement

### Caching Strategy

Within a single report batch (typically 15-50 transfers):
- `Map<String, String> agentNodeNameCache` -- agentId -> nodeName
- `Map<Long, String> taskNameCache` -- taskId -> taskName
- `Map<Long, String> receiverNodeNameCache` -- subtaskId -> receiverNodeName

These are ephemeral (per-request), no cross-request caching needed.

## Admin Changes

### BatchTransferAgentStateMapper.xml

Remove LEFT JOINs from `selectList` and `selectByTaskId`. Change to simple SELECT:

```sql
select id, agent_id, transfer_id, subtask_id, task_id,
       file_path, file_name, remote_target_path, status,
       total_size, chunk_size, total_chunks, transferred_chunks,
       retry_count, exception_desc,
       sender_node_name, receiver_node_name, task_name,
       create_time_str, update_time_str,
       enqueued_time, init_upload_start_time, init_upload_end_time,
       upload_chunks_start_time, upload_chunks_end_time,
       merge_chunks_start_time, merge_chunks_end_time,
       upload_success_time,
       create_time, update_time
from batch_transfer_agent_state
```

### BatchTransferAgentState entity

No changes needed -- `senderNodeName`, `receiverNodeName`, `taskName` fields remain, they're just populated from DB columns now instead of JOIN aliases.

## Agent Changes

None. `reportAgentTransferState()` already reads from RocksDB-backed `taskInflightMap`.

## Frontend Changes

None. The entity fields and API responses remain the same.

## Files to Modify

| Module | File | Change |
|--------|------|--------|
| admin | `schema.sql` | Add 3 columns to `batch_transfer_agent_state` |
| admin | `BatchTransferAgentStateMapper.xml` | Remove LEFT JOINs, simple SELECT |
| proxy | `BatchAdminController.java` | Add enrichment logic before upsert |