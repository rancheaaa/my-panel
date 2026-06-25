# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Rules

0、第一原则，不要过度设计，以TDD为原则，测试驱动开发，写新功能前先写测试案例（可以使用mockito或者真实连接到数据库），测试案例要全，边界条件要全，测试覆盖率、通过率要100%，surefire通过，只有测试案例都通过了，主程序运行正常（一定要做，确保主程序始终是正常运行的），编写git commit提交信息，才能开发下一个。前端代码如果只是页面或者简单逻辑，直接写代码即可，牢记。第二原则，如果我描述的需求你理解下来有歧义或者不确定，一定要主动询问我，理解清楚后再动手写代码。

1、开发了新的java组件或者类，一定要集成进主程序，不要只在测试案例中使用了或者压根就没使用；重要牢记。
2、进入cmd，使用bash可以进入wsl Debian系统，root跟默认用户的密码是!Cq199606，必要进入wsl的linux系统执行一些命令或者调试。开发是在windows上进行的，admin的日志文件在E:\tmp\my-panel\admin\logs\admin，proxy的日志在E:\tmp\my-panel\admin\logs\proxy，agent日志在E:\tmp\my-panel\admin\logs\agent。`mvn compile -pl agent ; mvn test -pl agent`多命令分隔符使用分号，不要使用&& ，请牢记。
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

```
- 任何新增的前端功能页面，都必须在 `data.sql` 的 `sys_menu` 表中添加对应的菜单初始化数据
    - 菜单结构必须包含三个层级：
  - **一级目录(M)**: 功能模块目录 (如：menu_id=2300, parent_id=0)
  - **二级菜单(C)**: 具体页面入口 (如：menu_id=2301, component='xxx/index')
  - **三级按钮(F)**: 操作权限按钮 (如：查询/新增/修改/删除/导出等)
```

14、编写完前端代码完成后，要运行npm run lint，确保没有语法错误，才能算完成。

15、所有controller service mapper dto vo 等入参和出参，包括泛型里具体的类型，不能是Map类型，必须是具体的java bean，方便预知所有字段。

16、【重要】所有单元测试代码，必须强制使用H2数据库，所以所有sql和建表语句得兼容h2和mysql，虽然已经配置了H2兼容Mysql的语法。

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

## 

## Backend Architecture (my-panel-admin)

Entry point: `com.cq.panel.admin.server.App`

### Key Patterns
- **Controllers** extend `BaseController` for common response handling
- **Entities** extend `BaseEntity` (provides createBy, createTime, updateBy, updateTime)
- **MyBatis XML mappers** in `src/main/resources/mapper/` organized by domain (system/, rc/, architecture/, agent/, batch/, monitor/)
- **SQL initialization**: `src/main/resources/sql/schema.sql` + `data.sql` (runs on startup via `spring.sql.init.mode`)
- **Mode switching**: `app.mode` property controls standalone/cluster profile activation
- **Auth**: `my-panel-auth-lite` provides `@RequirePermission` / `@RequireRole` annotations with AOP enforcement
- **Password**: Frontend MD5+salt → Backend BCrypt double encryption
- **Virtual threads**: Enabled via `spring.threads.virtual.enabled=true`



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



