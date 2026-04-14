# proxy

独立运行的注册配置中心代理服务（Netty 4.x + Spring Boot + MyBatis-Plus + Caffeine）。

## 运行

在项目根目录：

```bash
mvn -pl proxy -DskipTests package
java -Xms2g -Xmx2g -XX:+UseG1GC -jar proxy/target/proxy-*.jar
```

默认监听：`0.0.0.0:8888`。

## 接口

- `GET /api/health`
- `POST /api/v1/registry/register`
- `GET /api/v1/registry/discover?serviceName={name}&environment={env}`
- `GET /api/v1/config/{configKey}?environment={env}&serviceName={name}`
- `PUT /api/v1/config/{configKey}`
- `GET /api/v1/ws`（WebSocket，推送配置变更）

详细说明见：`docs/API.md` 与 `docs/openapi.yaml`。

