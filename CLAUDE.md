# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

My-Panel is a modern admin panel built with React 19 + Spring Boot 4, inspired by RuoYi architecture. It supports dual-mode operation:
- **Standalone mode**: H2 database + Caffeine cache (zero dependencies for development)
- **Cluster mode**: MySQL + Redis (production deployment)

The project also includes a distributed agent system for remote file management and command execution.

## Build Commands

### Full Build (Backend + Frontend + Distribution)
```bash
mvn clean package -DskipTests
```
This produces `distribution/target/my-panel-1.0.0.zip` containing JDK, Nginx, and all services.

### Backend Only
```bash
mvn clean install -DskipTests          # Install all modules to local repo
mvn clean compile -pl my-panel-admin   # Compile single module
```

### Frontend Only
```bash
cd my-panel-ui
npm install
npm run dev       # Dev server (proxies /api to localhost:8888)
npm run build     # Production build
npm run lint      # ESLint check
```

### Run Tests
```bash
mvn test                                   # All tests
mvn test -pl my-panel-admin                # Admin module tests
mvn test -pl my-panel-proxy                # Proxy module tests
mvn test -Dtest=ClassName                  # Single test class
mvn test -Dtest=ClassName#methodName       # Single test method
```

### Run Applications (IDE or Command Line)
- **Admin**: `com.cq.panel.admin.server.App` (port 8888)
- **Proxy**: `com.cq.proxy.ProxyApplication` (port 9876)
- **Agent**: `com.cq.agent.AgentApplication` (standalone Netty server, configurable port)

## Module Architecture

```
my-panel/
├── my-panel-admin/       # Main Spring Boot admin server (port 8888)
├── my-panel-proxy/       # Registry & config center proxy service (port 9876)
├── agent/                # Standalone agent for remote command execution & file ops (Netty-based)
├── my-panel-auth-lite/   # Lightweight auth library (JWT + annotation-based authorization)
├── my-panel-common/      # Shared utilities & custom load balancer implementations
├── cli-tools/            # CLI tools for Redis and MySQL operations
├── distribution/         # Assembly packaging (JDK, Nginx, startup scripts)
└── my-panel-ui/          # React 19 frontend (Vite + Ant Design 6)
```

### Dependency Flow
```
my-panel-admin → my-panel-auth-lite, my-panel-common
my-panel-proxy → my-panel-common
agent → my-panel-common
distribution → my-panel-admin, my-panel-proxy, agent, cli-tools
```

## Backend Architecture (my-panel-admin)

Entry point: `com.cq.panel.admin.server.App`

### Package Structure
```
com.cq.panel.admin.server/
├── annotation/       # Custom annotations: @Anonymous, @Excel, @Log, @Sensitive
├── common/           # Constants, enums, utilities
├── config/           # Spring configuration classes
├── context/          # Thread-local auth/permission holders
├── datasource/       # Dynamic datasource support
├── manager/          # Shutdown hooks
├── quartz/           # Scheduled job execution
├── repository/
│   ├── domain/       # Entity classes (MyBatis POJOs)
│   └── mapper/       # MyBatis mapper interfaces
├── service/          # Business logic
└── web/controller/   # REST controllers
```

### Key Patterns
- **Controllers** extend `BaseController` for common response handling
- **Entities** extend `BaseEntity` (provides createBy, createTime, updateBy, updateTime)
- **MyBatis XML mappers** in `src/main/resources/mapper/` organized by domain (system/, rc/, architecture/, agent/, batch/, monitor/)
- **SQL initialization**: `src/main/resources/sql/schema.sql` + `data.sql` (runs on startup via `spring.sql.init.mode`)
- **Mode switching**: `app.mode` property controls standalone/cluster profile activation
- **Auth**: `my-panel-auth-lite` provides `@RequirePermission` / `@RequireRole` annotations with AOP enforcement
- **Password**: Frontend MD5+salt → Backend BCrypt double encryption
- **Virtual threads**: Enabled via `spring.threads.virtual.enabled=true`

### Controller Domains
- `system/` - Users, roles, menus, depts, dicts, configs, posts, notices
- `monitor/` - Dashboard, cache, online users, login/operation logs, jobs, alert rules
- `rc/` - Registry center (projects, environments, nodes, configs, access tokens)
- `architecture/` - Architecture diagrams with versioning, tags, favorites
- `agent/` - Agent registry and command history
- `batch/` - Batch file transfer operations and monitoring
- `common/` - Captcha, file upload/download

## Proxy Service (my-panel-proxy)

Entry point: `com.cq.proxy.ProxyApplication`

Acts as a lightweight registry and config center. Key services:
- `RegistryService` - Service instance registration/discovery
- `ConfigService` - Distributed configuration management
- `BatchTaskScheduler` / `RoutingScheduler` - Batch file transfer orchestration with multiple routing strategies (round-robin, random, region-based, broadcast)

Has JaCoCo coverage enforcement (80% instruction coverage on `com.cq.proxy.service.*`).

## Agent Module

Entry point: `com.cq.agent.AgentApplication` (pure Java, no Spring)

A standalone Netty-based HTTP server providing:
- **Command execution**: `POST /api/execute` (supports Windows & Linux)
- **File operations**: FTP-like API (LIST, RETR, STOR, DELE, MKD, etc.)
- **Chunked transfers**: Large file upload/download with RocksDB-backed persistent queues
- **Agent registration**: Auto-registers with proxy service via `AgentRegistryService`

Key classes:
- `HttpServer` / `CommonNettyHandler` - Netty HTTP server pipeline
- `handler/file/*` - Individual FTP command handlers
- `handler/upload/*` / `handler/download/*` - Chunked transfer handlers
- `client/upload/` - Client-side upload with persistent queue and retry logic

## Frontend Architecture (my-panel-ui)

- **Framework**: React 19 + Vite 7 + Ant Design 6
- **Routing**: Dynamic route generation from backend menu API (`src/router/utils.jsx`)
- **API layer**: `src/api/` organized by domain, using Axios with token interceptor
- **Mock data**: `src/mock/` provides development mock endpoints
- **Dev proxy**: Vite proxies `/api` → `http://localhost:8888`

### Frontend Structure
```
src/
├── api/          # API service modules
├── components/   # Reusable components (BrandIcon, IconSelect, TagsView, ResizableTable)
├── layout/       # MainLayout with sidebar navigation
├── mock/         # Mock data for development
├── pages/        # Page components organized by domain
│   ├── arch/     # Architecture diagram editor
│   ├── monitor/  # Monitoring dashboards
│   ├── op/       # Operations (agent manage, command history)
│   ├── rc/       # Registry center UI
│   └── system/   # System management
├── router/       # Route generation from backend menu config
└── utils/        # Utilities (request.js with Axios, crypto.js for MD5)
```

## Deployment

Distribution package (`distribution/`) includes startup scripts:
- `bin/my-panel-admin.sh` / `.bat` - Service management
- Commands: `install` (first-time setup), `start`, `stop`, `restart`
- Granular control: `app start` (Java only), `nginx start` (Nginx only)

Default accounts: admin/admin123, guest/guest123, cq/cq123456

## Tech Stack Versions
- Java 21, Spring Boot 4.0.4, MyBatis 4.0.1
- React 19.2.0, Vite 7.2.4, Ant Design 6.2.2
- Netty 4.2.5, RocksDB 6.10.2
- MySQL 8.4.0, H2 (embedded), Redis, Caffeine 3.2.0

## Project Rules

0、第一原则，不要过度设计，以TDD为原则，测试驱动开发，写新功能前先写测试案例（可以使用mockito或者真实连接到数据库），测试案例要全，边界条件要全，测试覆盖率、通过率要100%，surefire通过，只有测试案例都通过了，编写git commit提交信息，才能开发下一个。

1、进入cmd，使用bash可以进入wsl Debian系统，root跟默认用户的密码是!Cq199606，必要进入wsl的linux系统执行一些命令或者调试。
2、开发是在windows上进行的，admin的日志文件在E:\tmp\my-panel\admin\logs\admin，proxy的日志在E:\tmp\my-panel\admin\logs\proxy，agent日志在E:\tmp\my-panel\admin\logs\agent
3、由于是在开发阶段，任何新建表都要放在schema.sql中，任何初始化sql插入都要放在data.sql中，不要使用alt加或者改表结构，直接重建，有新表时我会手动删除现有数据库。
4、使用mvn clean install时，尽可能不要运行distribution的compile package install，因为打包这个模块很慢很耗时，除非我主动要求打包distribution。
5、如果是新功能，不要直接写代码，先头脑风暴，先做需求调研和分析，将需求文档和设计写出来，等我确认过了，再开始写代码。
6、写设计文档时，尽量不要使用大段的真实java代码，可以多用流程图或时序图来描述，少量java枚举类或java bean或Java配置类可以直接用java描述，很短的逻辑可以用java描述，大段的代码千万不要直接贴java代码。
7、markdown文件中的Mermaid图表的语法，确保与Typora 0.9.72 beta版本兼容
8、sql新建的表都要有这几个字段（由谁创建、由谁更新、创建时间、更新时间），名称不要错了，create_by、update_by、create_time、update_time、remark。

9、修改完java的代码，一定要mvn clean compile，修改了哪个模块编译哪个，确保修改的代码语法是对的。

10、修改后schema.sql中的建表语句，运行前或者运行测试类前，确保删除过修改过的表，这样就可以自动创建新表了。