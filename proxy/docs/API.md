# Proxy API

所有接口统一返回：

```json
{"code":200,"message":"OK","data":{}}
```

## 健康检查

`GET /api/health`

响应示例：

```json
{"code":200,"message":"OK","data":{"status":"UP"}}
```

## 服务注册

`POST /api/v1/registry/register`

请求体：

```json
{"serviceName":"order-service","environment":"dev","host":"127.0.0.1","port":8080}
```

## 服务发现

`GET /api/v1/registry/discover?serviceName=order-service&environment=dev`

响应 `data` 为实例列表：

```json
[
  {"serviceName":"order-service","environment":"dev","host":"10.0.0.1","port":8080,"available":true,"lastHeartbeat":"2026-01-01T00:00:00Z"}
]
```

## 配置获取

`GET /api/v1/config/{configKey}?environment={env}&serviceName={name}`

响应 `data`：

```json
{"configKey":"a.b","configValue":"v1","configDesc":"desc"}
```

## 配置更新

`PUT /api/v1/config/{configKey}`

请求体：

```json
{"environment":"dev","serviceName":"order-service","configValue":"v2","configDesc":"desc"}
```

成功后会向 WebSocket 连接广播：

```json
{"type":"CONFIG_UPDATED","serviceName":"order-service","environment":"dev","configKey":"a.b"}
```

## WebSocket

`GET /api/v1/ws`

- 心跳：服务端 30 秒空闲发送 `PING`（WebSocket ping frame）。
- 文本 `PING`：返回 `PONG`。

