# 批量文件传输设计文档评审与优化建议 Spec

## Why

对批量文件传输系统的详细设计文档（2026-04-30-batch-file-transfer-design.md）进行系统性专业评审，识别潜在的设计缺陷、遗漏、风险点及优化机会，确保文档质量达到生产级标准，为后续实施提供可靠的技术基础。

## What Changes

### 评审维度与发现的问题

#### 1. 架构设计层面 (7个问题)

**问题 A1: 缺少Proxy高可用架构说明**
- **严重程度**: HIGH
- **现状**: 文档提到"Proxy可部署多实例"但未详细说明
- **影响**: 生产环境单点故障风险
- **建议**: 补充Proxy集群部署方案、任务调度锁机制（分布式锁/数据库乐观锁）、状态同步策略

**问题 A2: Agent端RocksDB依赖未充分论证**
- **严重程度**: MEDIUM
- **现状**: 直接选择RocksDB作为队列持久化方案
- **影响**: 增加Agent部署复杂度（需安装RocksDB native库）
- **建议**: 
  - 评估替代方案：LevelDB（Java原生）、SQLite、文件系统+序列化
  - 或明确RocksDB选型理由并提供部署指南

**问题 A3: 缺少数据一致性保障机制**
- **严重程度**: HIGH
- **现状**: 未说明Agent重启后如何保证不重复执行已完成的子任务
- **影响**: 可能导致文件重复传输或数据损坏
- **建议**: 补充幂等性设计方案（transfer_id去重机制）

**问题 A4: 缺少任务优先级与资源隔离策略**
- **严重程度**: MEDIUM
- **现状**: 所有任务共享同一线程池和带宽资源
- **影响**: 高优先级任务可能被低优先级任务阻塞
- **建议**: 引入任务优先级队列、资源配额机制

**问题 A5: 缺少断点续传的详细设计**
- **严重程度**: MEDIUM
- **现状**: 仅提到"复用现有分块上传逻辑"但未说明批量场景下的特殊处理
- **影响**: 大文件传输失败后重传效率低
- **建议**: 明确chunk粒度、元数据持久化位置、恢复流程

**问题 A6: 缺少消息可靠性保障**
- **严重程度**: HIGH
- **现状**: Proxy→Agent的HTTP调用无重试/确认机制
- **影响**: 分发指令可能丢失导致子任务遗漏
- **建议**: 增加分发结果确认、失败重试、最终一致性检查

**问题 A7: 缺少容量规划与限流保护**
- **严重程度**: MEDIUM
- **现状**: 无全局并发控制、无请求速率限制
- **影响**: 系统过载时可能雪崩
- **建议**: 增加令牌桶/滑动窗口限流、熔断降级机制

---

#### 2. 数据模型层面 (5个问题)

**问题 D1: 子任务表缺少业务唯一标识**
- **严重程度**: MEDIUM
- **现状**: 主键仅是自增ID，无业务含义
- **影响**: 分布式环境下难以追踪和去重
- **建议**: 增加task_seq_no字段（如"TASK1001-SUB005"）

**问题 D2: 缺少文件版本/变更检测机制**
- **严重程度**: LOW
- **现状**: 仅记录file_md5但无版本概念
- **影响**: 无法判断源文件是否在传输过程中被修改
- **建议**: 增加file_version或scan_snapshot_time字段

**问题 D3: 队列快照表缺少数据生命周期管理细节**
- **严重程度**: LOW
- **现状**: 提到"保留最近7天"但无自动化清理策略
- **影响**: 数据量持续增长可能影响查询性能
- **建议**: 明确定时清理Job的cron表达式、清理批次大小

**问题 D4: JSON字段使用过度**
- **严重程度**: MEDIUM
- **现状**: target_agents, include_patterns, error_distribution等使用JSON
- **影响**: 查询效率低、难以建立索引、不利于统计分析
- **建议**: 高频查询字段考虑拆分为关联表或使用专门的JSON索引

**问题 D5: 缺少软删除/归档策略**
- **严重程度**: LOW
- **现状**: task表有deleted标志但无归档机制
- **影响**: 历史数据累积影响性能
- **建议**: 增加历史表或分区归档策略

---

#### 3. API设计层面 (6个问题)

**问题 API1: 缺少API版本管理策略**
- **严重程度**: MEDIUM
- **现状**: URL路径为/api/v1/batch/但无版本演进计划
- **影响**: 未来接口升级可能导致兼容性问题
- **建议**: 明确版本化策略（URL versioning vs Header versioning）、废弃周期

**问题 API2: 错误响应格式不统一**
- **严重程度**: MEDIUM
- **现状**: 部分接口返回code/message/errors结构，部分返回简单错误信息
- **影响**: 前端错误处理逻辑复杂
- **建议**: 统一为{code, message, data, errors[], traceId}格式

**问题 API3: 缺少分页参数校验边界**
- **严重程度**: LOW
- **现状**: page/size参数仅有@Min/@Max注解
- **影响**: 恶意请求可能导致大量数据返回
- **建议**: 增加最大页数限制、默认值合理性验证

**问题 API4: 缺少请求幂等性设计**
- **严重程度**: MEDIUM
- **现状**: POST创建任务、PUT启动任务等操作无幂等键
- **影响**: 网络超时重试可能导致重复操作
- **建议**: 关键写操作增加Idempotency-Key头或客户端生成requestId

**问题 API5: WebSocket连接管理缺失**
- **严重程度**: HIGH
- **现状**: 提到WebSocket推送但无连接认证、心跳、重连机制
- **影响**: 安全风险、连接不稳定
- **建议**: 补充WebSocket握手认证、心跳检测、断线重连、消息ACK机制

**问题 API6: 内部API安全性不足**
- **严重程度**: HIGH
- **现状**: Proxy↔Agent通信仅提到"Internal Token"但无具体实现
- **影响**: 内部接口被攻击可能导致系统瘫痪
- **建议**: 明确mTLS证书管理、Token轮换策略、IP白名单

---

#### 4. 安全性层面 (4个问题)

**问题 S1: 路径遍历防护不够全面**
- **严重程度**: HIGH
- **现状**: 仅检查".."但未处理符号链接、Windows路径特殊情况
- **影响**: 可能绕过安全检查访问敏感文件
- **建议**: 
  - 禁止符号链接跟随（NOFOLLOW_LINKS选项）
  - Windows路径标准化处理
  - 白名单机制替代黑名单

**问题 S2: 缺少传输加密详细方案**
- **严重程度**: MEDIUM
- **现状**: 提到"HTTPS/TLS"但无密钥管理、证书轮换策略
- **影响**: 密钥泄露风险、运维复杂度未知
- **建议**: 补充TLS版本要求（1.2+）、证书管理流程、密钥存储方案

**问题 S3: 缺少敏感数据脱敏规则**
- **严重程度**: LOW
- **现状**: API响应包含完整路径、IP地址等敏感信息
- **影响**: 日志泄露、前端XSS风险
- **建议**: 定义脱敏规则（路径显示前3级、IP显示前两段）

**问题 S4: 缺少RBAC权限细化**
- **严重程度**: MEDIUM
- **现状**: 权限粒度较粗（batch:task:create等）
- **影响**: 无法实现细粒度访问控制（如只能查看自己创建的任务）
- **建议**: 增加数据权限（行级权限）、操作审计增强

---

#### 5. 性能与可扩展性层面 (5个问题)

**问题 P1: 子任务笛卡尔积爆炸风险**
- **严重程度**: CRITICAL
- **现状**: 1200 files × 20 targets = 24000 subtasks，全量插入DB
- **影响**: DB写入瓶颈、内存溢出、事务超时
- **建议**: 
  - 分批生成和插入（每批1000条）
  - 考虑懒加载/按需生成策略
  - 设置硬性上限并提示用户

**问题 P2: 进度上报频率过高**
- **严重程度**: MEDIUM
- **现状**: 每5秒上报 + 每个chunk完成都上报
- **影响**: Proxy端IO压力大、DB写入频繁
- **建议**: 
  - 批量上报聚合（10秒一次）
  - 基于变化阈值触发（进度变化>5%才上报）
  - Agent本地缓存+定时flush

**问题 P3: 缺少数据库分片/分区具体方案**
- **严重程度**: MEDIUM
- **现状**: 提到"按taskId哈希分区"但无具体实现
- **影响**: 单表数据量过大时性能下降
- **建议**: 提供具体的分区函数、分区数量计算公式、历史数据迁移脚本

**问题 P4: Redis缓存策略过于简单**
- **严重程度**: LOW
- **现状**: 固定TTL缓存，无缓存穿透/击穿/雪崩防护
- **影响**: 高并发下缓存失效导致DB压力骤增
- **建议**: 增加布隆过滤器防穿透、互斥锁防击穿、随机TTL防雪崩

**问题 P5: 缺少大文件传输优化策略**
- **严重程度**: MEDIUM
- **现状**: 默认分块大小未定义、无并行分块传输
- **影响**: 大文件（>1GB）传输效率低
- **建议**: 自适应分块大小（根据网络延迟动态调整）、多连接并行传输

---

#### 6. 可观测性与运维层面 (6个问题)

**问题 O1: 日志规范缺失**
- **严重程度**: MEDIUM
- **现状**: 未定义日志级别、格式、采样率
- **影响**: 问题排查困难、日志量失控
- **建议**: 
  - 定义日志格式（JSON结构化）
  - 规范日志级别使用（DEBUG/INFO/WARN/ERROR）
  - 敏感信息过滤规则
  - 日志采样策略（DEBUG日志1%采样）

**问题 O2: 告警通知渠道不够完善**
- **严重程度**: LOW
- **现状**: 提到邮件/钉钉/Webhook但无模板、频率限制、升级策略
- **影响**: 告警风暴、关键告警被忽略
- **建议**: 
  - 告警收敛/抑制规则
  - 多级通知升级（5分钟→15分钟→1小时→电话）
  - 告警静默期配置

**问题 O3: 缺少健康检查端点设计**
- **严重程度**: MEDIUM
- **现状**: 无/health、/ready等端点定义
- **影响**: K8s/Docker无法正确进行服务探活
- **建议**: 增加：
  - /health/liveness（存活探针）
  - /health/readiness（就绪探针）
  - 自定义检查项（DB连接、RocksDB状态、队列深度）

**问题 O4: 缺少链路追踪方案**
- **严重程度**: MEDIUM
- **现状**: 跨组件调用无traceId传递
- **影响**: 性能瓶颈定位困难、问题根因分析耗时
- **建议**: 集成OpenTelemetry/Jaeger，定义trace上下文传播协议

**问题 O5: 缺少灰度发布与回滚策略**
- **严重程度**: MEDIUM
- **现状**: 实施计划中无灰度、回滚步骤
- **影响**: 新版本上线风险高
- **建议**: 
  - 特性开关（Feature Flag）控制新功能
  - 金丝雀发布策略
  - 自动化回滚脚本
  - 数据库变更回滚方案

**问题 O6: 运维手册内容不足**
- **严重程度**: LOW
- **现状**: Phase 4提到"运维手册"但文档中无具体内容
- **影响**: 运维人员上手慢
- **建议**: 在文档附录中补充：
  - 常见问题FAQ
  - 故障排查checklist
  - 性能调优指南
  - 应急预案（Agent离线、DB主从切换等）

---

#### 7. 测试策略层面 (3个问题)

**问题 T1: 缺少测试策略章节**
- **严重程度**: HIGH
- **现状**: 无单元测试、集成测试、E2E测试的具体方案
- **影响**: 质量保障体系不完整
- **建议**: 新增测试章节，包括：
  - 单元测试覆盖率目标（>80%）
  - Mock策略（外部依赖mock方案）
  - 集成测试场景清单（至少20个核心场景）
  - 性能基准测试用例
  - 混沌测试（模拟Agent宕机、网络分区）

**问题 T2: 缺少数据迁移与兼容性测试**
- **严重程度**: MEDIUM
- **现状**: 无版本升级数据迁移脚本、无向后兼容性测试
- **影响**: 升级风险高
- **建议**: 
  - Flyway/Liquibase版本化管理DDL
  - 升级回滚测试用例
  - API兼容性测试矩阵

**问题 T3: 缺少异常场景测试用例**
- **严重程度**: MEDIUM
- **现状**: 仅描述正常流程
- **影响**: 边界条件bug遗漏
- **建议**: 补充异常场景：
  - 扫描中途Agent宕机
  - 传输过程中网络闪断
  - 目标磁盘空间不足
  - 并发启动同一任务
  - 配置参数非法值组合

---

## Impact

### 受影响的文档章节

- 第3章 架构设计（需补充HA、一致性、容量规划）
- 第4章 数据模型（需优化字段设计、增加约束）
- 第6章 API设计（需统一格式、增加安全机制）
- 第11章 错误处理（需细化分类、增加恢复策略）
- 第12章 性能优化（需补充具体实施方案）
- 第13章 安全性设计（需加强防护措施）
- 第14章 监控告警（需完善运维体系）
- 第17章 实施计划（需增加测试阶段）

### 受影响的代码范围

- Proxy端: BatchTaskScheduler, QueueMonitor, ProgressAggregator
- Agent端: BatchTransferQueueManager, BatchAwareAgentUploader, BatchFileScanner
- Admin端: BatchTaskController, BatchTransferService
- 全局: 安全中间件、日志框架、监控埋点

## ADDED Requirements

### Requirement: 架构增强

The system SHALL provide high availability support for Proxy component with distributed locking mechanism.

#### Scenario: Proxy Leader Election
- **WHEN** multiple Proxy instances are deployed
- **THEN** only one instance shall act as scheduler leader at any given time
- **AND** automatic failover shall occur within 30 seconds of leader failure

### Requirement: Data Consistency Guarantee

The system SHALL ensure idempotent execution of batch transfer subtasks to prevent duplicate file transfers.

#### Scenario: Agent Restart Recovery
- **WHEN** an Agent restarts during active transfers
- **THEN** it SHALL recover in-progress tasks from persistent queue without duplicating completed work
- **AND** verify transfer integrity using stored transfer metadata

### Requirement: Security Hardening

The system SHALL implement defense-in-depth security for internal API communication between Proxy and Agents.

#### Scenario: mTLS Authentication
- **WHEN** Proxy initiates connection to Agent
- **THEN** mutual TLS authentication SHALL be performed using dedicated certificates
- **AND** certificate rotation SHALL be automated with zero-downtime deployment

### Requirement: Observability Enhancement

The system SHALL provide comprehensive observability including structured logging, distributed tracing, and health check endpoints.

#### Scenario: End-to-End Trace
- **WHEN** a batch transfer task is executed
- **THEN** a unique traceId SHALL be propagated across all components (Admin → Proxy → Agent → Target)
- **AND** all log entries and metrics SHALL be correlated with this traceId

## MODIFIED Requirements

### Requirement: Subtask Generation Performance (原FR-03修改)

**原始需求**: Proxy接收文件列表后，生成子任务的笛卡尔积（文件 × 目标Agent），并下发给Source Agent。

**修改后需求**: 
- Proxy SHALL generate subtasks in batches (max 1000 per batch) to prevent memory overflow
- AND SHALL implement lazy generation strategy for large-scale tasks (>5000 files)
- AND SHALL provide progress feedback during subtask generation phase
- AND SHALL validate total subtask count against configured maximum (default: 100000) before execution

### Requirement: Progress Reporting Optimization (原FR-05修改)

**原始需求**: Target Agent在接收文件过程中周期性向Proxy上报进度，Proxy聚合后推送给Admin UI。

**修改后需求**:
- Agent SHALL aggregate progress updates locally and report to Proxy every 10 seconds (configurable)
- AND SHALL implement change-based reporting trigger when progress delta exceeds 5%
- AND Proxy SHALL batch-process incoming reports to reduce DB write frequency
- AND SHALL maintain real-time progress cache with TTL=5s for low-latency queries

## REMOVED Requirements

无需要移除的需求。
