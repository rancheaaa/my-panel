-- 批量传输功能 Mock 数据 (H2兼容版本)
-- 用于Spring Boot集成测试

-- 1. 插入批量传输任务 Mock 数据 (3条)
INSERT INTO batch_transfer_task (
    task_name, task_description, source_agent_id, source_agent_name,
    source_dir, target_dirs, include_patterns, exclude_patterns,
    scan_cron_expression, max_scan_files, target_agent_ids, target_agent_names,
    retry_enabled, retry_max_days, retry_interval_min, max_retry_count,
    retry_backoff_type, post_transfer_action, backup_dir, backup_mode,
    preserve_dir_structure, transfer_mode, routing_strategy, routing_config,
    status, started_at, create_by, create_time, update_by, update_time, remark, deleted
) VALUES 
    ('日志备份任务', '每日备份应用日志到备用节点', 'agent-001', 'root@10.240.85.177:7777',
     '/var/log/app', '/backup/logs/node1;/backup/logs/node2', '["*.log"]', '["debug*.log","temp*"]',
     '0 */5 * * * ?', 10000, '["agent-002","agent-003"]', '["root@node2:7777","root@node3:7777"]',
     1, 7, 30, 10, 'EXPONENTIAL', 'NONE', NULL, 'COPY',
     1, 'ONE_TO_MANY', 'BROADCAST', NULL,
     'RUNNING', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, '', NULL, '测试任务1', 0),
    
    ('配置同步任务', '同步配置文件到所有节点', 'agent-001', 'root@10.240.85.177:7777',
     '/etc/app/config', '/etc/backup/config', '["*.properties","*.yaml","*.xml"]', NULL,
     NULL, 5000, '["agent-002","agent-003","agent-004"]', '["root@node2:7777","root@node3:7777","root@node4:7777"]',
     1, 3, 15, 5, 'LINEAR', 'DELETE', '/tmp/archived', 'MOVE',
     0, 'ONE_TO_MANY', 'ROUND_ROBIN', NULL,
     'READY', NULL, 'admin', CURRENT_TIMESTAMP, '', NULL, '测试任务2', 0),
    
    ('数据归档任务', '归档历史数据到存储节点', 'agent-002', 'root@10.240.85.178:7777',
     '/data/archive', '/storage/archive/2026', '["*.csv","*.json"]', '["*.tmp","*.bak"]',
     '0 0 2 * * ?', 20000, '["agent-003"]', '["root@node3:7777"]',
     1, 14, 60, 20, 'EXPONENTIAL', 'BACKUP', '/archive/backup', 'COPY',
     1, 'ONE_TO_ONE', 'BROADCAST', '{"region":"us-east-1"}',
     'PAUSED', DATEADD('DAY', -1, CURRENT_TIMESTAMP), 'admin', DATEADD('DAY', -2, CURRENT_TIMESTAMP), '', DATEADD('DAY', -1, CURRENT_TIMESTAMP), '已暂停的任务', 0);

-- 2. 插入子任务 Mock 数据 (5条)
INSERT INTO batch_transfer_subtask (
    task_id, source_file_path, target_agent_id, target_dir, status,
    transferred_chunks, total_chunks, transferred_bytes, total_bytes,
    error_message, retry_count, next_retry_at, transfer_id, create_time, update_time
) VALUES 
    (1, '/var/log/app/application.log', 'agent-002', '/backup/logs/node1', 'COMPLETED',
     100, 100, 10485760, 10485760, NULL, 0, NULL, 'transfer-abc-001', DATEADD('HOUR', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP),
    
    (1, '/var/log/app/debug.log', 'agent-002', '/backup/logs/node1', 'FAILED',
     50, 100, 5242880, 10485760, 'Connection reset by peer', 3, DATEADD('MINUTE', 30, CURRENT_TIMESTAMP), 'transfer-abc-002', DATEADD('MINUTE', -30, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP),
    
    (1, '/var/log/app/error.log', 'agent-003', '/backup/logs/node2', 'SENDING',
     30, 80, 3145728, 8388608, NULL, 0, NULL, 'transfer-abc-003', DATEADD('MINUTE', -5, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP),
    
    (1, '/var/log/app/system.log', 'agent-003', '/backup/logs/node2', 'QUEUED',
     0, 0, 0, 0, NULL, 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    
    (2, '/etc/app/config/application.properties', 'agent-002', '/etc/backup/config', 'QUEUED',
     0, 0, 0, 0, NULL, 0, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 3. 插入事件队列表 Mock 数据 (4条)
INSERT INTO batch_sync_event (
    event_type, task_id, source_agent_id, payload, status,
    retry_count, error_message, created_at, processed_at, expire_at
) VALUES 
    ('TASK_CREATED', 1, 'agent-001', '{"taskId":1,"taskName":"日志备份任务","status":"READY"}', 'COMPLETED',
     0, NULL, DATEADD('HOUR', -1, CURRENT_TIMESTAMP), DATEADD('MINUTE', -59, CURRENT_TIMESTAMP), DATEADD('HOUR', 23, CURRENT_TIMESTAMP)),
    
    ('TASK_STATUS_CHANGED', 1, 'agent-001', '{"taskId":1,"oldStatus":"READY","newStatus":"RUNNING"}', 'COMPLETED',
     0, NULL, DATEADD('MINUTE', -59, CURRENT_TIMESTAMP), DATEADD('MINUTE', -58, CURRENT_TIMESTAMP), DATEADD('HOUR', 23, CURRENT_TIMESTAMP)),
    
    ('TASK_CREATED', 2, 'agent-001', '{"taskId":2,"taskName":"配置同步任务","status":"READY"}', 'PROCESSING',
     0, NULL, DATEADD('MINUTE', -5, CURRENT_TIMESTAMP), NULL, DATEADD('HOUR', 23, DATEADD('MINUTE', 55, CURRENT_TIMESTAMP))),
    
    ('TASK_UPDATED', 3, 'agent-002', '{"taskId":3,"taskName":"数据归档任务","status":"PAUSED"}', 'PENDING',
     0, NULL, DATEADD('MINUTE', -1, CURRENT_TIMESTAMP), NULL, DATEADD('HOUR', 23, DATEADD('MINUTE', 59, CURRENT_TIMESTAMP)));
