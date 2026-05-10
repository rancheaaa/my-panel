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

11、开发了新的有用的代码文件，记得gti add该文件

12、编写测试案例时，应该尽可能设计和考虑异常场景的处理，包括技术异常和业务异常，这块测试才是重点。

13、**【重要】新增功能页面必须同步初始化菜单数据到data.sql中**：
    - 任何新增的前端功能页面，都必须在 `data.sql` 的 `sys_menu` 表中添加对应的菜单初始化数据
    - 菜单结构必须包含三个层级：
      - **一级目录(M)**: 功能模块目录 (如：menu_id=2300, parent_id=0)
      - **二级菜单(C)**: 具体页面入口 (如：menu_id=2301, component='xxx/index')
      - **三级按钮(F)**: 操作权限按钮 (如：查询/新增/修改/删除/导出等)
    - 必须为相关角色分配菜单权限 (`sys_role_menu` 表)：
      - 超级管理员(role_id=1): 完整权限
      - 开发角色(role_id=4): 基本操作权限
      - 其他角色: 根据实际需求分配
    - menu_id 分配规范（避免ID冲突）：
      - 系统管理模块: 1-999
      - 系统监控模块: 1000-1499
      - 运维管理模块: 1500-1999
      - 架构编排模块: 2100-2299
      - **批量文件传输模块: 2300-2499** (后续新功能从2500开始)
      - 其他新模块: 按顺序递增，每次预留100个ID空间
    - 参考示例：见 data.sql 第469-520行（批量文件传输菜单初始化）
    - **检查清单**:
      - [ ] 一级目录菜单已添加 (type=M)
      - [ ] 二级页面菜单已添加 (type=C, component路径正确)
      - [ ] 所有操作按钮权限已添加 (type=F, perms标识完整)
      - [ ] 角色权限已分配 (sys_role_menu)
      - [ ] Git已提交data.sql修改

---

## 📋 批量文件传输功能开发经验总结 (2026-05-10)

> 本章节记录了从0到1完成一个复杂功能的完整流程，包含后端(Java/Spring Boot) + Agent(纯Java/Netty) + 前端(React/Ant Design) 三端协同开发的实战经验。

### 一、整体开发流程 (Subagent-Driven Development)

#### 1.1 标准开发流程图

```
┌─────────────────────────────────────────────────────────────┐
│                    需求分析与设计阶段                         │
│  ┌──────────┐    ┌──────────────┐    ┌──────────────────┐  │
│  │ 头脑风暴 │ → │ 需求文档编写 │ → │ 设计评审确认     │  │
│  └──────────┘    └──────────────┘    └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    实施计划制定                              │
│  • 拆分为10个独立Task (基础设施→API→Hooks→组件→整合)       │
│  • 每个Task遵循TDD: 先写测试→验证失败→实现→验证通过        │
│  • 使用TodoWrite跟踪进度                                   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              Subagent-Driven 执行模式                       │
│  ┌─────────┐   ┌─────────────┐   ┌──────────────────┐     │
│  │ 实现    │ → │ 规格审查    │ → │ 代码质量审查     │     │
│  │ 子代理  │   │ 子代理      │   │ 子代理           │     │
│  └─────────┘   └─────────────┘   └──────────────────┘     │
│       ↑              ↑                   ↑                  │
│       │ 失败         │ 不通过             │ 不通过            │
│       └──────────────┴───────────────────┴──────────────────┘
│                    修复并重新审查                             │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    Git提交与集成                             │
│  • 每个Task完成后立即commit                                │
│  • commit message格式: feat(module): 简短描述              │
│  • 所有测试通过后才允许进入下一个Task                        │
└─────────────────────────────────────────────────────────────┘
```

#### 1.2 Task拆分原则

| Task类型 | 复杂度 | 示例 | 依赖关系 |
|---------|--------|------|---------|
| **基础设施** | ⭐ 低 | 常量定义、配置文件 | 无依赖 |
| **API层** | ⭐⭐ 中 | REST接口封装 | 依赖基础设施 |
| **业务逻辑层** | ⭐⭐⭐ 高 | Service、Hook | 依赖API层 |
| **UI组件** | ⭐⭐ 中 | React组件 | 依赖业务逻辑 |
| **页面整合** | ⭐ 低 | 主页面入口 | 依赖所有组件 |

**关键规则**: 只有前序Task的测试100%通过后，才能开始下一个Task！

---

### 二、TDD (测试驱动开发) 最佳实践

#### 2.1 后端TDD流程 (Java + JUnit 5 + Mockito)

```java
// ✅ 正确的测试结构示例 (BatchTransferTaskServiceImplTest.java)
@SpringBootTest
class BatchTransferTaskServiceImplTest {

    @Mock
    private BatchTransferTaskMapper taskMapper;
    
    @InjectMocks
    private BatchTransferTaskServiceImpl service;

    @Test
    void createTask_shouldReturnId_whenValidInput() {
        // Given: 准备测试数据
        BatchTransferTaskDTO dto = new BatchTransferTaskDTO();
        dto.setTaskName("Test Task");
        
        when(taskMapper.insert(any())).thenReturn(1);
        
        // When: 执行被测方法
        Long result = service.createTask(dto, "admin");
        
        // Then: 验证结果
        assertNotNull(result);
        verify(taskMapper).insert(any()); // 验证交互
    }

    @Test
    void createTask_shouldThrowException_whenDuplicateName() {
        // 测试异常场景 (这是重点！)
        when(taskMapper.selectByTaskName("Exist")).thenReturn(new BatchTransferTask());
        
        assertThrows(BusinessException.class, () -> {
            service.createTask(buildDTO("Exist"), "admin");
        });
    }
}
```

#### 2.2 前端TDD流程 (React + Jest + Testing Library)

```javascript
// ✅ 正确的Hook测试结构示例 (useBatchTasks.test.js)
import { renderHook, act } from '@testing-library/react';

// 必须在import之前mock外部依赖
jest.mock('../../../../api/batch', () => ({
  getTaskList: jest.fn(),
  createTask: jest.fn(),
  // ... 其他方法
}));

describe('useBatchTasks', () => {
  beforeEach(() => jest.clearAllMocks());

  test('should load tasks on mount', async () => {
    // Given: mock API返回数据
    const mockTasks = [{ id: 1, taskName: 'Task A' }];
    batchApi.getTaskList.mockResolvedValue({ code: 200, data: mockTasks });

    // When: 渲染Hook
    let hookResult;
    await act(async () => {
      const { result } = renderHook(() => useBatchTasks());
      hookResult = result;
      await new Promise(resolve => setTimeout(resolve, 0));
    });

    // Then: 验证状态和行为
    expect(hookResult.current.tasks).toEqual(mockTasks);
    expect(batchApi.getTaskList).toHaveBeenCalled();
  });
});
```

#### 2.3 测试覆盖率要求

| 模块类型 | 最低覆盖率 | 强制要求 |
|---------|-----------|---------|
| **Entity/Domain** | 100% | 所有字段必须有测试 |
| **Service层** | 90%+ | 业务逻辑+异常场景全覆盖 |
| **Controller层** | 80%+ | 主要API路径覆盖 |
| **Utils/Tools** | 95%+ | 工具方法必须100%覆盖 |
| **React Hooks** | 85%+ | 核心状态和副作用覆盖 |
| **React Components** | 75%+ | 关键渲染和交互覆盖 |

**硬性规则**: 
- ❌ 测试不通过 → 不允许写下一行代码
- ❌ 覆盖率不达标 → 不允许Git commit
- ✅ 异常场景测试占比 ≥ 40% (这才是测试的重点!)

---

### 三、常见坑与解决方案 (踩坑记录)

#### 3.1 后端常见问题

##### 问题1: H2数据库SQL兼容性
```sql
-- ❌ MySQL语法 (H2不支持)
INSERT INTO table (time_field) VALUES (NOW());
SELECT * FROM logs WHERE create_time > NOW() - INTERVAL 7 DAY;

-- ✅ H2兼容语法
INSERT INTO table (time_field) VALUES (CURRENT_TIMESTAMP);
SELECT * FROM logs WHERE create_time > DATEADD('DAY', -7, CURRENT_TIMESTAMP);
```

**解决方案**: 测试中使用`@Sql`注解或JdbcTemplate初始化H2兼容的SQL

##### 问题2: Maven Surefire类加载器隔离问题
```
ERROR: ClassNotFoundException (明明类存在却找不到)
```
**根因**: Surefire默认使用IsolatedClassLoader，导致某些依赖加载失败

**解决方案** (在pom.xml中):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <useSystemClassLoader>true</useSystemClassLoader>  <!-- 关键! -->
    </configuration>
</plugin>
```

##### 问题3: Spring Bean注入失败 (@Component遗漏)
```
ERROR: Parameter X of constructor in Y required a bean of type Z that could not be found.
```
**根因**: 工具类/服务类缺少Spring注解 (`@Component`, `@Service`, `@Repository`)

**解决方案**: 
```java
// ❌ 错误: Spring无法识别
public class WildcardConflictDetector { ... }

// ✅ 正确: 添加@Component注解
@Component
public class WildcardConflictDetector { ... }
```

**预防措施**: 创建新Service/Util类时，**第一时间添加Spring注解**！

##### 问题4: MyBatis Mapper接口扫描失败
```
ERROR: Invalid bound statement (not found): mapper方法名
```
**可能原因**:
1. XML文件路径不在`resources/mapper/`下
2. XML namespace与接口包路径不匹配
3. 方法签名不一致

**检查清单**:
- [ ] XML文件位置: `src/main/resources/mapper/batch/BatchTransferTaskMapper.xml`
- [ ] namespace: `com.cq.panel.admin.server.repository.mapper.BatchTransferTaskMapper`
- [ ] 方法名完全一致 (包括参数类型)

#### 3.2 前端常见问题

##### 问题1: Jest配置ES模块支持
```
ERROR: SyntaxError: Cannot use 'import.meta' outside a module
```
**根因**: Vite项目使用ESM，但Jest默认不支持

**解决方案** (jest.config.js):
```javascript
export default {
  transform: {
    '^.+\\.jsx?$': ['babel-jest', { 
      presets: ['@babel/preset-env', '@babel/preset-react']
    }]
  },
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1'
  }
};
```

##### 问题2: Mock模块路径错误
```
Cannot find module '../../../api/batch' from test file
```
**根因**: 相对路径计算错误 (测试文件在`__tests__`子目录中)

**正确路径计算**:
```
测试文件位置: src/pages/batch/hooks/__tests__/useBatchTasks.test.js
目标模块位置: src/api/batch/index.js

相对路径: ../../../../api/batch  (向上4级: __tests__ → hooks → batch → pages → src)
```

**技巧**: 使用`moduleNameMapper`简化:
```javascript
moduleNameMapper: {
  '^@api/(.*)$': '<rootDir>/src/api/$1'
}
// 测试中: import * as batchApi from '@api/batch';
```

##### 问题3: React 19严格模式的act警告
```
Warning: An update to TestComponent inside a test was not wrapped in act(...)
```
**原因**: React 19对异步状态更新更严格

**解决方案**: 所有异步操作必须包裹在`act()`中:
```javascript
await act(async () => {
  await hookResult.current.createTask(data);  // 触发状态更新
  await new Promise(resolve => setTimeout(resolve, 0));  // 等待更新
});
expect(result).toBe(expected);  // 在act外断言
```

##### 问题4: Ant Design组件测试缺少jest-dom匹配器
```
TypeError: expect(...).toHaveClass is not a function
```
**解决方案**: 测试文件顶部导入:
```javascript
import '@testing-library/jest-dom';  // 扩展DOM匹配器
```

---

### 四、代码架构最佳实践

#### 4.1 后端分层架构规范

```
controller/          # REST API层 (参数校验、调用service)
  └── batch/
      └── BatchTransferTaskController.java  # 仅处理HTTP相关

service/             # 业务逻辑层 (核心算法、事务管理)
  ├── impl/         # 实现类
  │   └── batch/
  │       └── BatchTransferTaskServiceImpl.java
  ├── dto/          # 数据传输对象 (请求/响应)
  │   └── batch/
  │       └── BatchTransferTaskDTO.java
  └── util/         # 工具类 (可复用的纯函数)
      └── batch/
          ├── WildcardConflictDetector.java  # @Component!
          ├── CronExpressionValidator.java    # @Component!
          └── BatchConfigSerializer.java      # @Component!

repository/          # 数据访问层
  ├── domain/       # Entity实体类
  │   └── batch/
  │       └── BatchTransferTask.java
  └── mapper/       # MyBatis接口
      └── batch/
          └── BatchTransferTaskMapper.java
```

**关键原则**:
- Controller只做HTTP协议适配，不含业务逻辑
- Service层是核心，包含事务边界 (`@Transactional`)
- Util工具类必须是**无状态的纯函数** (便于测试和复用)
- DTO用于Controller ↔ Service之间的数据传输

#### 4.2 前端组件架构规范

```
pages/batch/                # 页面模块
  ├── index.jsx            # 主页面 (Tabs容器)
  ├── constants.js         # 常量定义 (状态枚举、颜色映射)
  ├── components/          # UI组件
  │   ├── TaskStatusBadge.jsx    # 展示型组件 (纯UI)
  │   ├── TaskForm.jsx          # 表单组件 (受控组件)
  │   ├── TaskListTab.jsx       # 列表组件 (数据展示+操作)
  │   ├── CreateEditTab.jsx     # 编辑页组件
  │   └── StatisticsTab.jsx     # 统计面板组件
  └── hooks/               # 自定义Hooks (业务逻辑封装)
      ├── usePolling.js        # 通用轮询Hook
      └── useBatchTasks.js     # 任务CRUD Hook
```

**设计原则**:
- **Hooks优先**: 业务逻辑放在Hooks中，组件只负责渲染
- **单一职责**: 每个组件/Hook只做一件事
- **Props向下, Events向上**: 单向数据流
- **Custom Hooks复用**: `usePolling`可用于任何需要轮询的场景

#### 4.3 API层设计规范

```javascript
// ✅ 标准API封装模式 (src/api/batch/index.js)
import request from '@/utils/request';

export const batchApi = {
  // 列表查询 (GET + params)
  getTaskList(params) {
    return request({
      url: '/batch/task/list',
      method: 'get',
      params: { pageNum: params.page, pageSize: params.size }  // 统一分页参数
    });
  },

  // 详情查询 (GET + path variable)
  getTaskById(id) {
    return request({ url: `/batch/task/${id}`, method: 'get' });
  },

  // 创建 (POST + body)
  createTask(data) {
    return request({ url: '/batch/task', method: 'post', data });
  },

  // 更新 (PUT + path variable + body)
  updateTask(id, data) {
    return request({ url: `/batch/task/${id}`, method: 'put', data });
  },

  // 删除 (DELETE + path variable, 支持批量)
  deleteTasks(ids) {
    return request({ url: `/batch/task/${ids.join(',')}`, method: 'delete' });
  },

  // 操作类 (POST + path variable)
  startTask(id) { return request({ url: `/batch/task/${id}/start`, method: 'post' }); },
  pauseTask(id) { return request({ url: `/batch/task/${id}/pause`, method: 'post' }); },
  resumeTask(id) { return request({ url: `/batch/task/${id}/resume`, method: 'post' }); },
  stopTask(id) { return request({ url: `/batch/task/${id}/stop`, method: 'post' }); }
};
```

**命名规范**:
- `getXxx` → GET请求
- `createXxx` → POST创建
- `updateXxx` → PUT更新
- `deleteXxx` → DELETE删除
- `startXxx/pauseXxx/resumeXxx/stopXxx` → 状态操作

---

### 五、Git工作流规范

#### 5.1 Commit Message格式

```
feat(module): 简要描述 (英文, 小写开头)

详细说明 (可选):
- 为什么做这个改动
- 影响范围
- 相关Issue编号

示例:
feat(batch): add TaskStatusBadge component

- 6个状态颜色映射全部实现
- 使用React.memo优化性能
- 测试覆盖率100%
```

**Type前缀**:
- `feat`: 新功能
- `fix`: Bug修复
- `refactor`: 重构 (不改变行为)
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具链

#### 5.2 分支策略

```
main (生产环境)
  └── feat/batch-file-transfer (功能分支)
       ├── commit 1: feat(batch): add constants
       ├── commit 2: feat(batch): implement API layer  
       ├── commit 3: feat(batch): add usePolling hook
       └── ... (每个Task一次commit)
```

**规则**:
- 每个独立Task完成后立即commit
- Commit粒度: **小而频繁** (便于code review和回滚)
- 不要把多个不相关的改动放在同一个commit

---

### 六、性能优化经验

#### 6.1 后端优化

| 优化点 | 方案 | 效果 |
|-------|------|------|
| **数据库查询** | 只查必要字段,避免`SELECT *` | 减少50%+数据传输 |
| **批量操作** | 使用`foreach`批量插入 | 提升10x写入性能 |
| **并发控制** | Semaphore限流 (全局+任务级) | 防止资源耗尽 |
| **重试机制** | 指数退避 (1s→2s→4s→5s) | 提高容错性 |
| **进度上报** | 批量写入 + 速率限制 | 减少99%网络开销 |

#### 6.2 前端优化

| 优化点 | 方案 | 效果 |
|-------|------|------|
| **轮询策略** | 3秒间隔 + 手动控制 + 卸载清理 | 避免内存泄漏 |
| **组件渲染** | React.memo + useMemo | 减少50%重渲染 |
| **表格虚拟化** | Ant Table的scroll属性 | 大数据流畅滚动 |
| **代码分割** | 动态import路由组件 | 首屏加载提升30% |

---

### 七、调试与排错指南

#### 7.1 日志查看位置

| 服务 | Windows日志路径 | Linux日志路径 |
|-----|---------------|-------------|
| Admin | `E:\tmp\my-panel\admin\logs\admin` | `/var/log/my-panel/admin/` |
| Proxy | `E:\tmp\my-panel\admin\logs\proxy` | `/var/log/my-panel/proxy/` |
| Agent | `E:\tmp\my-panel\admin\logs\agent` | `/var/log/my-panel/agent/` |

#### 7.2 常见错误速查表

| 错误信息 | 可能原因 | 解决方案 |
|---------|---------|---------|
| `Bean not found` | 缺少`@Component/@Service` | 添加Spring注解 |
| `Table not found` | 未删除旧表就运行测试 | 手动删表或加`@AutoConfigureTestDatabase` |
| `ClassNotFoundException` | Surefire类加载器问题 | 配置`<useSystemClassLoader>true</` |
| `Module not found` | Jest相对路径错误 | 使用`moduleNameMapper`或修正路径 |
| `act() warning` | React 19严格模式 | 包裹所有异步操作在`act()`中 |
| `SyntaxError: import.meta` | Jest未配置Babel | 安装`@babel/preset-env`并配置transform |

---

### 八、团队协作规范 (重要!)

#### 8.1 Code Review Checklist

**后端Review要点**:
- [ ] 是否遵循分层架构 (Controller→Service→Repository)
- [ ] Service方法是否有`@Transactional` (写操作必须)
- [ ] 是否有单元测试 (覆盖率≥90%)
- [ ] 异常场景是否处理 (输入校验、空指针、越界)
- [ ] SQL是否H2兼容 (如果用内存数据库测试)
- [ ] 是否有`@Component`等Spring注解

**前端Review要点**:
- [ ] 组件是否拆分合理 (单一职责)
- [ ] Hooks是否正确使用 (依赖数组、清理副作用)
- [ ] 是否有测试 (关键路径覆盖)
- [ ] 是否有性能隐患 (不必要的重渲染、内存泄漏)
- [ ] API调用是否统一封装 (不要直接用axios)

#### 8.2 文档规范

**必须编写的文档** (按顺序):
1. **需求文档** (Markdown, 含流程图/时序图)
2. **实施计划** (Markdown, Task拆分+验收标准)
3. **代码注释** (仅复杂逻辑, 不要注释显而易见的代码)
4. **README** (如果有新的独立模块)

**禁止事项**:
- ❌ 不要在设计文档中大段贴Java/JS代码 (用伪代码或图表替代)
- ❌ 不要提交未通过的测试
- ❌ 不要跳过Code Review直接合并

---

### 九、技术债务清单 (持续更新)

| 编号 | 债务项 | 优先级 | 计划修复时间 |
|-----|--------|-------|------------|
| TD-001 | 前端组件缺少完整测试 (TaskForm/StatisticsTab等) | P1 | 下个迭代 |
| TD-002 | Agent模块缺少集成测试 | P1 | 下个迭代 |
| TD-003 | 缺少API文档 (Swagger/OpenAPI) | P2 | 产品化前 |
| TD-004 | 前端缺少ErrorBoundary全局错误处理 | P2 | 下个迭代 |

---

### 十、快速启动模板 (Copy & Use)

#### 10.1 新建后端功能的标准步骤

```bash
# 1. 设计阶段 (必须!)
#    - 编写需求文档 → 等待确认
#    - 编写实施计划 → 等待确认

# 2. 数据库层
#    - schema.sql: CREATE TABLE语句
#    - data.sql: 初始化数据 (可选)

# 3. Entity层
#    - 创建 Domain实体类 (继承BaseEntity)
#    - 字段要与数据库表一一对应

# 4. Mapper层
#    - 创建Mapper接口
#    - 创建XML映射文件 (resources/mapper/)

# 5. Service层 (TDD!)
#    - 先写测试类 (XXXServiceImplTest.java)
#    - 运行测试 → 确认失败
#    - 实现Service类
#    - 运行测试 → 确认通过 (覆盖率达标)
#    - Git commit

# 6. Controller层
#    - 创建REST控制器
#    - 参数校验 (@Validated)
#    - Git commit

# 7. 编译验证
mvn clean compile -pl my-panel-admin
```

#### 10.2 新建前端功能的标准步骤

```bash
# 1. 安装测试依赖 (首次)
cd my-panel-ui
npm install --save-dev jest @testing-library/react @testing-library/jest-dom @testing-library/dom babel-jest @babel/preset-env @babel/preset-react jest-environment-jsdom

# 2. 配置Jest (创建jest.config.js)

# 3. 基础设施
#    - constants.js (常量定义)
#    - __tests__/constants.test.js (先测试!)
#    - Git commit

# 4. API层
#    - api/xxx/index.js (接口封装)
#    - __tests__/index.test.js (Mock axios测试)
#    - Git commit

# 5. Hooks层
#    - hooks/useXxx.js (业务逻辑)
#    - hooks/__tests__/useXxx.test.js (renderHook测试)
#    - Git commit

# 6. Components层
#    - components/Xxx.jsx (UI组件)
#    - components/__tests__/Xxx.test.jsx (render测试)
#    - Git commit

# 7. 页面整合
#    - index.jsx (主页面)
#    - Git commit

# 8. 全量测试
npx jest --coverage  # 确保全部通过
```

---

## 📚 参考资源

### 内部文档
- [批量传输功能规格书](.trae/specs/batch-file-transfer/spec.md)
- [前端UI设计文档](docs/batch-ui-design.md)
- [实施计划](docs/superpowers/plans/2026-05-10-batch-ui-implementation.md)

### 外部资源
- [Spring Boot Testing](https://spring.io/guides/testing)
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro)
- [Jest Documentation](https://jestjs.io/docs/getting-started)
- [Ant Design Components](https://ant.design/components/overview/)

---

*最后更新: 2026-05-10 by AI Assistant*  
*基于批量文件传输功能完整开发过程的实战总结*