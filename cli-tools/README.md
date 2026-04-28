# CLI Tools 使用手册

## 概述

CLI Tools 模块提供了两个命令行工具，用于管理和测试 MySQL 和 Redis 连接与操作。这些工具可以从配置文件中读取数据库配置，也可以通过命令行参数覆盖配置。

## 工具列表

| 工具 | JAR 文件 | 主类 |
|------|----------|------|
| MySQL 工具 | `cli-tools-*-mysql-standalone.jar` | `com.cq.panel.tools.mysql.MySqlCliTool` |
| Redis 工具 | `cli-tools-*-redis-standalone.jar` | `com.cq.panel.tools.redis.RedisCliTool` |

## 前置条件

### 配置文件

两个工具都依赖项目根目录下的 `config/application-cluster.yml` 配置文件。配置文件结构如下：

```yaml
spring:
  datasource:
    druid:
      master:
        url: jdbc:mysql://localhost:3306/my_panel?useUnicode=true&characterEncoding=utf8
        username: root
        password: password
  data:
    redis:
      host: localhost
      port: 6379
      password: password
      database: 0
```

---

## MySQL 工具

### 基本用法

```bash
java -jar cli-tools-1.0.0-mysql-standalone.jar [OPTIONS]
```

### 命令行参数

| 参数 | 说明 |
|------|------|
| `-c, --check` | 检查 MySQL 连接和端口可用性 |
| `-e, --execute <SQL>` | 执行单条 SQL 语句 |
| `-f, --file <FILE>` | 从文件中执行 SQL（每条语句以分号分隔） |
| `-H, --host <HOST>` | 覆盖配置文件中的 MySQL 主机地址 |
| `-P, --port <PORT>` | 覆盖配置文件中的 MySQL 端口 |
| `-u, --username <USER>` | 覆盖配置文件中的用户名 |
| `-p, --password <PASSWORD>` | 覆盖配置文件中的密码（交互式输入） |
| `--help` | 显示帮助信息 |
| `--version` | 显示版本信息 |

### 使用示例

#### 1. 检查 MySQL 连接

检查配置文件中的 MySQL 服务是否可达：

```bash
java -jar cli-tools-1.0.0-mysql-standalone.jar --check
```

输出示例：
```
Checking MySQL connection...
Host: localhost
Port: 3306
SUCCESS: Port 3306 is open and reachable
```

#### 2. 覆盖配置检查连接

使用命令行参数覆盖配置文件中的连接信息：

```bash
java -jar cli-tools-1.0.0-mysql-standalone.jar --check \
    --host 192.168.1.100 \
    --port 3306
```

#### 3. 执行单条 SQL

执行单条 SQL 查询语句：

```bash
java -jar cli-tools-1.0.0-mysql-standalone.jar --execute "SELECT * FROM sys_user LIMIT 1"
```

执行更新语句：

```bash
java -jar cli-tools-1.0.0-mysql-standalone.jar --execute "UPDATE sys_user SET status = 1 WHERE id = 1"
```

#### 4. 从文件执行 SQL

创建 SQL 文件 `test.sql`：

```sql
CREATE TABLE IF NOT EXISTS test_table (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL
);

INSERT INTO test_table (name) VALUES ('test1');
INSERT INTO test_table (name) VALUES ('test2');

SELECT * FROM test_table;
```

执行 SQL 文件：

```bash
java -jar cli-tools-1.0.0-mysql-standalone.jar --file /path/to/test.sql
```

#### 5. 完整参数示例

覆盖所有连接参数并执行 SQL：

```bash
java -jar cli-tools-1.0.0-mysql-standalone.jar \
    --host 192.168.1.100 \
    --port 3306 \
    --username root \
    --password mypassword \
    --execute "SELECT COUNT(*) FROM sys_user"
```

### 注意事项

- SQL 语句中的分号是可选的
- 支持多行 SQL 语句
- 注释行（以 `--` 或 `#` 开头）会被忽略
- 文件执行支持事务，遇到错误会自动回滚

---

## Redis 工具

### 基本用法

```bash
java -jar cli-tools-1.0.0-redis-standalone.jar [OPTIONS]
```

### 命令行参数

| 参数 | 说明 |
|------|------|
| `-c, --check` | 检查 Redis 连接和端口可用性 |
| `-e, --execute <COMMAND>` | 执行单条 Redis 命令 |
| `-f, --file <FILE>` | 从文件中执行 Redis 命令（每行一条命令） |
| `-H, --host <HOST>` | 覆盖配置文件中的 Redis 主机地址 |
| `-P, --port <PORT>` | 覆盖配置文件中的 Redis 端口 |
| `-a, --password <PASSWORD>` | 覆盖配置文件中的密码（交互式输入） |
| `-n, --database <DB>` | 覆盖配置文件中的数据库编号 |
| `--help` | 显示帮助信息 |
| `--version` | 显示版本信息 |

### 支持的 Redis 命令

#### 字符串操作
| 命令 | 用法 | 说明 |
|------|------|------|
| `GET` | `GET <key>` | 获取指定 key 的值 |
| `SET` | `SET <key> <value>` | 设置指定 key 的值 |
| `INCR` | `INCR <key>` | 将 key 中存储的数字值增 1 |
| `DECR` | `DECR <key>` | 将 key 中存储的数字值减 1 |

#### 哈希操作
| 命令 | 用法 | 说明 |
|------|------|------|
| `HGET` | `HGET <key> <field>` | 获取哈希表中指定字段的值 |
| `HSET` | `HSET <key> <field> <value>` | 设置哈希表字段的值 |
| `HGETALL` | `HGETALL <key>` | 获取哈希表中所有字段和值 |

#### 列表操作
| 命令 | 用法 | 说明 |
|------|------|------|
| `LPUSH` | `LPUSH <key> <value1> [value2...]` | 将一个或多个值插入列表头部 |
| `RPUSH` | `RPUSH <key> <value1> [value2...]` | 将一个或多个值插入列表尾部 |
| `LPOP` | `LPOP <key>` | 移除并返回列表头部元素 |
| `RPOP` | `RPOP <key>` | 移除并返回列表尾部元素 |
| `LRANGE` | `LRANGE <key> <start> <stop>` | 获取列表指定区间内的元素 |

#### 集合操作
| 命令 | 用法 | 说明 |
|------|------|------|
| `SADD` | `SADD <key> <member1> [member2...]` | 向集合添加一个或多个成员 |
| `SMEMBERS` | `SMEMBERS <key>` | 返回集合中的所有成员 |

#### 有序集合操作
| 命令 | 用法 | 说明 |
|------|------|------|
| `ZADD` | `ZADD <key> <score> <member>` | 向有序集合添加成员 |
| `ZRANGE` | `ZRANGE <key> <start> <stop>` | 返回有序集合指定区间内的成员 |

#### Key 操作
| 命令 | 用法 | 说明 |
|------|------|------|
| `DEL` | `DEL <key1> [key2...]` | 删除一个或多个 key |
| `EXISTS` | `EXISTS <key1> [key2...]` | 判断 key 是否存在 |
| `EXPIRE` | `EXPIRE <key> <seconds>` | 设置 key 的过期时间（秒） |
| `TTL` | `TTL <key>` | 返回 key 的剩余生存时间 |
| `KEYS` | `KEYS <pattern>` | 查找所有匹配模式的 key |

#### 服务器命令
| 命令 | 用法 | 说明 |
|------|------|------|
| `PING` | `PING` | 检查 Redis 连接是否正常 |
| `INFO` | `INFO` | 获取 Redis 服务器信息 |
| `DBSIZE` | `DBSIZE` | 返回当前数据库的 key 数量 |
| `FLUSHDB` | `FLUSHDB` | 清空当前数据库所有 key |
| `FLUSHALL` | `FLUSHALL` | 清空所有数据库的 key |

### 使用示例

#### 1. 检查 Redis 连接

```bash
java -jar cli-tools-1.0.0-redis-standalone.jar --check
```

输出示例：
```
Checking Redis connection...
Host: localhost
Port: 6379
SUCCESS: Port 6379 is open and reachable
```

#### 2. 执行字符串命令

```bash
# 设置值
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "SET mykey HelloWorld"

# 获取值
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "GET mykey"
```

#### 3. 执行哈希命令

```bash
# 设置哈希字段
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "HSET user:1000 name John age 30"

# 获取哈希字段
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "HGET user:1000 name"

# 获取所有字段和值
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "HGETALL user:1000"
```

#### 4. 执行列表命令

```bash
# 推送元素到列表
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "LPUSH mylist item1"
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "RPUSH mylist item2"

# 获取列表范围
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "LRANGE mylist 0 -1"
```

#### 5. 执行 Key 操作

```bash
# 检查 key 是否存在
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "EXISTS mykey"

# 设置过期时间
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "EXPIRE mykey 3600"

# 查看剩余生存时间
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "TTL mykey"

# 查找匹配模式的 key
java -jar cli-tools-1.0.0-redis-standalone.jar --execute "KEYS user:*"
```

#### 6. 从文件执行命令

创建命令文件 `redis_commands.txt`：

```
PING
SET testkey testvalue
GET testkey
EXISTS testkey
DEL testkey
KEYS *
```

执行命令文件：

```bash
java -jar cli-tools-1.0.0-redis-standalone.jar --file /path/to/redis_commands.txt
```

#### 7. 覆盖配置连接

```bash
java -jar cli-tools-1.0.0-redis-standalone.jar \
    --host 192.168.1.100 \
    --port 6380 \
    --password redispass \
    --database 1 \
    --check
```

### 注意事项

- 命令参数之间使用空格分隔
- 注释行（以 `#` 开头）会被忽略
- 命令不区分大小写，内部会自动转换为大写
- 文件执行时，每行作为一条独立命令处理

---

## 打包与使用

### 打包

在项目根目录执行：

```bash
cd e:\java-project2\my-panel
mvn clean package -pl cli-tools -am -DskipTests
```

打包完成后，JAR 文件位于：
- `cli-tools/target/cli-tools-1.0.0-mysql-standalone.jar`
- `cli-tools/target/cli-tools-1.0.0-redis-standalone.jar`

### 集成到 distribution

distribution 模块打包时会自动包含这两个工具 JAR：

```bash
cd e:\java-project2\my-panel
mvn clean package -pl distribution -am -DskipTests
```

打包后的 distribution 包中，工具位于 `tools/libs/` 目录。

---

## 退出码

| 退出码 | 说明 |
|--------|------|
| 0 | 操作成功 |
| 1 | 操作失败（连接失败、SQL 执行错误等） |

---

## 常见问题

### Q: 配置文件路径不正确

工具会在项目根目录下查找 `config/application-cluster.yml`。确保：
1. 从项目根目录运行工具，或
2. 使用 `java -Dproject.root=/path/to/root ...` 指定项目根目录

### Q: 交互式密码输入无响应

在某些终端环境下，交互式密码输入可能不工作。此时请使用 `--password` 参数直接提供密码：

```bash
java -jar cli-tools-1.0.0-mysql-standalone.jar --execute "..." --password mypassword
```

### Q: Redis 命令不支持

如果需要使用更多 Redis 命令，可以修改 [RedisCliTool.java](file:///e:/java-project2/my-panel/cli-tools/src/main/java/com/cq/panel/tools/redis/RedisCliTool.java#L140-L176) 中的 `executeRedisCommand` 方法添加新的命令支持。
