# Proxy 部署文档

## 环境要求

- JDK 21
- MySQL 8.0+（推荐）或 H2（开发/测试）

## 构建

```bash
mvn -pl proxy -DskipTests package
```

产物：`proxy/target/proxy-*.jar`

## MySQL 配置

在 `proxy/src/main/resources/application.properties` 中替换为 MySQL：

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/my_panel?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=root
spring.datasource.password=your_password
```

要求数据库表已存在，表结构参考 `my-panel-admin/src/main/resources/sql/schema.sql` 中 `rc_*` 表。

## 启动

```bash
java -Xms2g -Xmx2g -XX:+UseG1GC -jar proxy/target/proxy-*.jar
```

默认端口 `8888`，Netty 线程与 TCP 参数见 `application.properties`：

- `proxy.netty.boss-threads=2`
- `proxy.netty.worker-threads=8`
- `proxy.netty.so-backlog=1024`
- `proxy.netty.tcp-nodelay=true`
- `proxy.netty.so-keepalive=true`
- `proxy.netty.idle-seconds=30`

## 日志

- `logs/proxy/info.log`
- `logs/proxy/error.log`

