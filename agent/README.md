## Agent 模块说明

Agent 是一个独立运行的轻量级 HTTP 服务，用于：
- 远程执行系统命令（支持 Windows 与 Linux/Unix）
- 提供类 FTP 的文件操作接口（LIST/RETR/STOR 等）
- 支持大文件分片上传、断点续传
- 支持大文件高性能下载（基于 Netty 零拷贝 FileRegion）

整体入口在 [AgentApplication](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/AgentApplication.java#L23-L95)。

---

## 构建与运行

### 构建

在项目根目录执行：

```bash
mvn -pl agent clean package
```

生成物：
- `agent/target/agent-1.0.0.jar`：可执行 fat jar（由 maven-shade-plugin 生成）
- `agent/target/lib/`：依赖 jar（如果选择 `java -cp` 方式运行时使用）

### 运行

在 `agent` 模块所在目录（或根目录）执行：

```bash
java -jar agent/target/agent-1.0.0.jar
```

启动日志中会打印：
- 所在操作系统
- 监听端口
- 支持的 HTTP API 列表
- 当前文件根目录（`file.base.directory`）

服务默认监听端口：`8090`（可在配置文件中修改）。

---

## 配置说明（agent.properties）

配置文件默认从以下位置按顺序加载（后者覆盖前者）：
1. classpath 下的 `agent.properties`（内置默认配置）
2. 当前工作目录下的 `agent.properties`
3. `./config/agent.properties`

核心配置由 [AgentConfig](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/config/AgentConfig.java#L23-L47) 解析。

### 服务端配置

```properties
server.port=8090
server.boss.threads=1
server.worker.threads=0
```

- `server.port`：HTTP 服务端口
- `server.boss.threads`：Netty boss 线程数（accept 连接）
- `server.worker.threads`：Netty worker 线程数，为 0 时使用 Netty 默认值

### 命令执行配置

```properties
executor.thread.pool.size=10
executor.default.timeout.seconds=30
executor.max.timeout.seconds=3600
```

- `executor.thread.pool.size`：执行系统命令的线程池大小
- `executor.default.timeout.seconds`：默认命令超时时间
- `executor.max.timeout.seconds`：最大允许超时时间（用于保护）

### HTTP 连接配置

```properties
connection.idle.timeout.seconds=60
connection.max.content.length=10485760
```

- `connection.idle.timeout.seconds`：连接空闲超时时间，超时后自动关闭
- `connection.max.content.length`：单个 HTTP 请求最大内容长度（字节）

> 注意：启动时会自动校验 `connection.max.content.length` 不能小于 `file.chunk.size.bytes`，否则会被调整到不小于分片大小。

### 文件操作配置

```properties
file.base.directory=
file.allow.outside.base=true
file.max.size=104857600
file.chunk.size.bytes=4194304
upload.session.timeout.minutes=60
```

- `file.base.directory`：文件操作根目录，为空时默认使用系统用户 Home 目录
- `file.allow.outside.base`：是否允许访问根目录以外路径
  - `true`：允许（仅依赖操作系统权限）
  - `false`：所有访问路径必须在 `file.base.directory` 下，否则拒绝
- `file.max.size`：上传/下载允许的最大文件大小（字节）
- `file.chunk.size.bytes`：分片大小（字节），用于大文件上传/下载推荐分片大小
- `upload.session.timeout.minutes`：分片上传会话多久未访问视为过期（分钟）

---

## 整体架构与模块说明

### 1）Netty HTTP 服务器

入口： [HttpServer](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/server/HttpServer.java#L21-L111)

核心职责：
- 基于 `NioEventLoopGroup` 创建 Netty 服务器
- 在 Pipeline 中组装：
  - `IdleStateHandler`：空闲连接检测
  - `HttpServerCodec`：HTTP 编解码
  - `HttpObjectAggregator`：聚合 HTTP 消息，方便直接处理 `FullHttpRequest`
  - `FileHandler`：处理 `/api/file/*` 文件相关请求
  - `HttpServerHandler`：处理 `/api/execute`、`/api/health` 等命令执行请求

### 2）命令执行模块

主要类：
- `CommandExecutor`：基于 Apache Commons Exec 执行外部命令
- `HttpServerHandler`：暴露 `/api/execute` 和 `/api/health` HTTP 接口

典型流程：
1. 客户端调用 `/api/execute`，提交命令和参数
2. Handler 解析请求，交给 `CommandExecutor` 执行
3. 将命令的 stdout/stderr/exitCode 以 JSON 形式返回

（具体字段可通过直接阅读 `CommandExecutor` 和 `HttpServerHandler`，此处不展开）

### 3）文件操作模块（FTP 风格）

主要类：
- `FileService`：封装所有文件操作逻辑
- `FileHandler`：把 HTTP 请求映射到 `FileService` 方法
- `FileInfo`：统一的文件元数据模型

支持的主要命令（对应 HTTP 路径）：

- 列表与信息
  - `GET /api/file/syst`：系统信息（SYST）
  - `GET /api/file/feat`：支持的功能列表（FEAT）
  - `GET /api/file/list`：目录详细列表（LIST）
  - `GET /api/file/nlst`：仅文件名列表（NLST）
  - `GET /api/file/stat`：文件状态（STAT）
  - `GET /api/file/size`：文件大小（SIZE）
  - `GET /api/file/mdtm`：最后修改时间（MDTM）

- 文件内容
  - `GET /api/file/retr`：获取文件内容（支持 text/binary，返回 JSON+Base64）
  - `POST /api/file/stor`：保存文件（STOR）
  - `POST /api/file/stou`：使用唯一文件名保存（STOU）
  - `POST /api/file/appe`：追加写入（APPE）

- 其他操作
  - `DELETE /api/file/dele`：删除文件（DELE）
  - `POST /api/file/mkd`：创建目录（MKD）
  - `DELETE /api/file/rmd`：删除目录（RMD，支持递归）
  - `GET /api/file/pwd`：获取工作目录（PWD）
  - `POST /api/file/rename`：重命名（RNFR/RNTO）
  - `POST /api/file/copy`：拷贝文件
  - `POST /api/file/chmod`：修改权限（CHMOD，非 POSIX 系统以可读写执行位表示）
  - `GET /api/file/checksum`：计算 MD5/SHA1/SHA256
  - `GET /api/file/search`：文件搜索
  - `GET /api/file/disk`：磁盘空间信息

所有 API 的返回风格统一为：

```json
{
  "success": true,
  "data": { ... }
}
```

或：

```json
{
  "success": false,
  "error": "错误信息"
}
```

---

## 大文件分片上传与断点续传

### 设计目标

- 支持 TB 级大文件上传，不受单次 HTTP 请求大小限制
- 支持断点续传：中断后可查询已上传分片，只补传缺失部分
- 支持会话超时自动清理，避免磁盘被临时分片占满

### 核心类与数据结构

- [ChunkedTransferService](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/service/ChunkedTransferService.java)
  - 负责分片上传会话管理、分片落盘与合并、分片下载
- [UploadSession](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/model/UploadSession.java)
  - 上传会话模型，记录：
    - `transferId`、`targetPath`、`fileName`
    - `totalSize`、`totalChunks`、`chunkSize`
    - 已收到分片集合 `receivedChunks`
    - 进度、创建时间、最后访问时间、是否已合并、checksum 等
- `uploadSessions`：`ConcurrentHashMap<String, UploadSession>`，存放所有活跃会话
- `cleanupExecutor`：定时任务定期清理超时会话并删除其临时分片目录

所有与 HTTP 的交互由 [FileHandler](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/handler/FileHandler.java) 下列接口完成：

- `POST /api/file/chunk/init`
- `POST /api/file/chunk/upload`
- `POST /api/file/chunk/merge`
- `GET  /api/file/chunk/status`
- `POST /api/file/chunk/cancel`
- `GET  /api/file/chunk/sessions`
- `GET  /api/file/chunk/download/info`
- `GET  /api/file/chunk/download`

### 上传流程

客户端完整上传流程建议如下：

#### 1. 初始化上传会话

请求：

```http
POST /api/file/chunk/init
Content-Type: application/json

{
  "targetPath": "/path/to/save/big-file.dat",
  "fileName": "big-file.dat",
  "totalSize": 10737418240,
  "transferId": "abcdef123456..."
}
```

返回（示例）：

```json
{
  "success": true,
  "data": {
    "transferId": "abcdef123456...",
    "targetPath": "/path/to/save/big-file.dat",
    "fileName": "big-file.dat",
    "totalSize": 10737418240,
    "totalChunks": 2560,
    "chunkSize": 4194304,
    "tempDirectory": "/.../.agent_chunks/abcdef123456...",
    "completed": false,
    "merged": false,
    "progress": "0.00%"
  }
}
```

说明：
- `totalChunks` 和 `chunkSize` 由服务端根据 `totalSize` 和配置计算
- `transferId` 为后续上传的关键标识，可由客户端生成（如文件MD5）以支持断点续传，若不传则服务端随机生成。

#### 2. 按分片上传内容

客户端按 `chunkSize` 切分文件，每片依次上传：

```http
POST /api/file/chunk/upload
Content-Type: application/json

{
  "transferId": "abcdef123456...",
  "chunkIndex": 0,
  "content": "BASE64_DATA",
  "encoding": "base64"
}
```

注意：
- `chunkIndex` 从 0 开始
- 非最后一片：内容长度必须等于 `chunkSize`
- 最后一片：内容长度必须等于剩余字节数

返回（简化）：

```json
{
  "success": true,
  "data": {
    "transferId": "abcdef123456...",
    "chunkIndex": 0,
    "received": 1,
    "total": 2560,
    "progress": "0.04%",
    "completed": false
  }
}
```

#### 3. 查询上传状态（断点续传）

当上传中断或客户端需要恢复时，调用：

```http
GET /api/file/chunk/status?transferId=abcdef123456...
```

返回数据来自 `UploadSession.toMap()`，其中最关键字段：
- `receivedChunks`：已接收分片数量
- `missingChunks`：缺失分片索引列表（用于断点续传）
- `progress`：当前进度百分比

客户端只需重新上传 `missingChunks` 中的分片即可恢复上传。

#### 4. 合并所有分片

当所有分片都上传完成后（`completed=true`），调用：

```http
POST /api/file/chunk/merge
Content-Type: application/json

{ "transferId": "abcdef123456..." }
```

服务端行为：
- 按顺序读取 `chunk_0`, `chunk_1`, ...，使用 `FileChannel` 顺序写入目标文件
- 并行计算 MD5，合并完成后写入 `UploadSession.checksum`
- 清理会话临时目录

返回：

```json
{
  "success": true,
  "data": {
    "name": "big-file.dat",
    "path": "/path/to/save/big-file.dat",
    "size": 10737418240,
    "modifiedTime": "...",
    "checksum": "..."  // 仅在会话状态接口中返回
  }
}
```

#### 5. 取消上传

如果需要终止某个会话并清理已上传分片：

```http
POST /api/file/chunk/cancel
Content-Type: application/json

{ "transferId": "abcdef123456..." }
```

服务端会：
- 从 `uploadSessions` 中移除会话
- 删除该会话临时目录及其中所有 `chunk_*` 文件

#### 6. 查询所有活跃会话

获取当前所有未过期且未被显式取消的上传会话列表：

```http
GET /api/file/chunk/sessions
```

返回（示例）：

```json
{
  "success": true,
  "data": [
    {
      "transferId": "abcdef123456...",
      "targetPath": "/path/to/save/big-file.dat",
      "fileName": "big-file.dat",
      "totalSize": 10737418240,
      "totalChunks": 2560,
      "chunkSize": 4194304,
      "receivedChunks": 100,
      "missingChunks": [101, 102, ...],
      "progress": "3.91%",
      "completed": false,
      "merged": false,
      "createTime": 1678600000000,
      "lastAccessTime": 1678600500000
    }
  ]
}
```

#### 7. 会话自动清理

后台存在一个定时任务：
- 周期：每 5 分钟执行一次
- 行为：遍历所有 `UploadSession`，对 `System.currentTimeMillis() - lastAccessTime > sessionTimeoutMs` 的会话执行清理

会话访问（上传分片、查询状态、合并等）会更新 `lastAccessTime`，只要持续使用就不会被清理。

---

## 大文件分片下载与断点续传

### 设计思路

下载分 2 类接口：
- JSON + Base64 形式：适合已有 HTTP JSON 通道，易于前端接入
- 原始二进制 + 零拷贝：适合高性能场景，数据直接从内核缓存到 Socket，减少用户态拷贝

核心逻辑仍在 [ChunkedTransferService.downloadRange](file:///e:/java-project2/my-panel/agent/src/main/java/com/cq/agent/service/ChunkedTransferService.java#L241-L281) 中：
- 通过 `RandomAccessFile` 定位指定字节范围
- 返回 `ChunkedDownloadResult` 包含：
  - `data`：字节数组
  - `rangeStart` / `rangeEnd`
  - `totalSize`
  - `fileName`

### JSON + Base64 下载接口

1）获取下载信息：

```http
GET /api/file/chunk/download/info?path=/path/to/file
```

返回（示例）：

```json
{
  "success": true,
  "data": {
    "path": "/path/to/file",
    "fileName": "file",
    "size": 123456789,
    "chunkSize": 4194304,
    "totalChunks": 30,
    "supportsRange": true
  }
}
```

2）分片下载区间：

```http
GET /api/file/chunk/download?path=/path/to/file&start=0&end=4194303
```

返回：

```json
{
  "success": true,
  "data": "BASE64_DATA",
  "encoding": "base64",
  "rangeStart": 0,
  "rangeEnd": 4194303,
  "totalSize": 123456789,
  "fileName": "file"
}
```

客户端可以根据 `rangeStart` / `rangeEnd` / `totalSize` 自行实现断点续传和并行下载。

### 零拷贝二进制下载接口（推荐）

接口：`GET /api/file/retr-raw`

参数：
- `path`：文件路径
- `start`：起始位置（可选，默认 0）
- `length`：长度（可选，默认到文件结尾）

示例：

```http
GET /api/file/retr-raw?path=/path/to/file&start=0&length=1048576
```

响应：
- 状态码：
  - 完整文件：`200 OK`
  - 部分内容：`206 Partial Content`
- 响应头：
  - `Content-Type: application/octet-stream`
  - `Content-Length: <length>`
  - `Content-Disposition: attachment; filename="<fileName>"`
  - 如果为部分内容，还会返回：
    - `Content-Range: bytes <start>-<end>/<totalSize>`

实现细节（在 `FileHandler.handleRetrRaw` 中）：
- 使用 `RandomAccessFile` 获取 `FileChannel`
- 通过 Netty 的 `DefaultFileRegion` 将 FileChannel 直接写入 Socket
- 这是基于内核 `sendfile` 的零拷贝路径：
  - 数据不会经过 Java 堆内存，大幅减少 CPU 拷贝开销
  - 利用 PageCache 的顺序读写优化

**推荐用法：**
- 后端与前端之间对接时，如果可以处理二进制流，优先使用 `/api/file/retr-raw`
- 若只能处理 JSON，可继续使用 `/api/file/retr` 或 `/api/file/chunk/download`

---

## 安全性与限制

1）路径访问限制
- 所有文件访问最终都会通过 `resolvePath` 进行处理：
  - 绝对路径：直接规范化校验
  - 相对路径：相对 `file.base.directory` 规范化
- 当 `file.allow.outside.base=false` 时：
  - 任何解析后不在根目录下的路径都会抛出 `SecurityException` 并返回 "Access denied"

2）文件大小限制
- 读取/写入前都会检查 `file.max.size`
- 超过限制时直接拒绝操作，避免误传超大文件拖垮服务器

3）会话超时与资源回收
- 分片上传的临时文件统一放在 `${file.base.directory}/.agent_chunks` 下
- 使用后台定时任务按 `upload.session.timeout.minutes` 进行清理
- 进程退出时：
  - `ChunkedTransferService.shutdown()` 会停止清理线程
  - Netty `HttpServer` 的 `stop()` 会优雅关闭 Boss/Worker 线程组

---

## 集成建议

在上层系统（如 my-panel 后端或前端）使用时，可以按以下思路封装：

- 把 `agent` 看作一个独立的 File/Command Agent 服务：
  - 通过 HTTP 与其交互
  - 不在 Agent 内引入业务逻辑，只做通用能力

- 上传大文件：
  - 前端先调用 `/api/file/chunk/init` 获取 `transferId` 与 `chunkSize`
  - 使用 `chunkSize` 对文件分片，循环调用 `/api/file/chunk/upload`
  - 支持中途断线后，通过 `/api/file/chunk/status` 查询 `missingChunks` 再续传
  - 成功后调用 `/api/file/chunk/merge` 完成合并

- 下载大文件：
  - 若追求简单，使用 `/api/file/chunk/download` + Base64
  - 若追求性能，使用 `/api/file/retr-raw` 直接下载二进制流

根据这些接口，你可以在 my-panel 管理端中实现：
- 带进度条的大文件上传/下载
- 支持暂停/恢复的断点续传
- 多线程并行分片下载（与服务端 Chunk API 自然匹配）

如果后续需要，我可以再帮助你补充具体的 curl 示例、前端封装代码或 SDK。

