package com.cq.proxy.netty.handler;

import com.cq.proxy.api.ApiResponse;
import com.cq.proxy.api.dto.ConfigUpdateRequest;
import com.cq.proxy.api.dto.ConfigValueResponse;
import com.cq.proxy.api.dto.ServiceRegisterRequest;
import com.cq.proxy.exception.BusinessException;
import com.cq.proxy.exception.SystemException;
import com.cq.proxy.service.ConfigService;
import com.cq.proxy.service.RegistryService;
import com.cq.proxy.service.WebSocketBroadcastService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProxyHttpHandler extends SimpleChannelInboundHandler<HttpRequest> {

  private static final Logger log = LoggerFactory.getLogger(ProxyHttpHandler.class);

  private final ObjectMapper objectMapper;
  private final RegistryService registryService;
  private final ConfigService configService;
  private final WebSocketBroadcastService webSocketBroadcastService;

  public ProxyHttpHandler(
      ObjectMapper objectMapper,
      RegistryService registryService,
      ConfigService configService,
      WebSocketBroadcastService webSocketBroadcastService
  ) {
    this.objectMapper = objectMapper;
    this.registryService = registryService;
    this.configService = configService;
    this.webSocketBroadcastService = webSocketBroadcastService;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) {
    if (!(msg instanceof FullHttpRequest req)) {
      return;
    }

    QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
    String path = decoder.path();
    Map<String, List<String>> params = decoder.parameters();

    try {
      if (HttpMethod.GET.equals(req.method()) && "/api/health".equals(path)) {
        NettyHttpUtil.writeJson(ctx, req, objectMapper, ApiResponse.ok(Map.of("status", "UP")));
        return;
      }

      if (HttpMethod.POST.equals(req.method()) && "/api/v1/registry/register".equals(path)) {
        ServiceRegisterRequest body = readBody(req, ServiceRegisterRequest.class);
        registryService.register(body);
        NettyHttpUtil.writeJson(ctx, req, objectMapper, ApiResponse.ok("REGISTERED", null));
        return;
      }

      if (HttpMethod.GET.equals(req.method()) && "/api/v1/registry/discover".equals(path)) {
        String serviceName = NettyHttpUtil.getQueryParam(params, "serviceName");
        String environment = NettyHttpUtil.getQueryParam(params, "environment");
        NettyHttpUtil.writeJson(ctx, req, objectMapper, ApiResponse.ok(registryService.discover(serviceName, environment)));
        return;
      }

      if (path.startsWith("/api/v1/config/")) {
        String configKey = path.substring("/api/v1/config/".length());
        if (configKey.isEmpty()) {
          throw new BusinessException(400, "configKey is required");
        }
        String environment = NettyHttpUtil.getQueryParam(params, "environment");
        String serviceName = NettyHttpUtil.getQueryParam(params, "serviceName");

        if (HttpMethod.GET.equals(req.method())) {
          ConfigValueResponse resp = configService.getConfig(configKey, environment, serviceName);
          NettyHttpUtil.writeJson(ctx, req, objectMapper, ApiResponse.ok(resp));
          return;
        }
        if (HttpMethod.PUT.equals(req.method())) {
          ConfigUpdateRequest update = readBody(req, ConfigUpdateRequest.class);
          ConfigValueResponse updated = configService.updateConfig(configKey, update);
          webSocketBroadcastService.broadcastConfigUpdated(update.serviceName(), update.environment(), configKey);
          NettyHttpUtil.writeJson(ctx, req, objectMapper, ApiResponse.ok("UPDATED", updated));
          return;
        }
      }

      NettyHttpUtil.writeJson(ctx, req, objectMapper, ApiResponse.fail(404, "NOT_FOUND"));
    } catch (BusinessException e) {
      NettyHttpUtil.writeJson(ctx, req, objectMapper, ApiResponse.fail(e.getCode(), e.getMessage()));
    } catch (Exception e) {
      log.error("Unhandled error for {} {}", req.method(), req.uri(), e);
      NettyHttpUtil.writeJson(ctx, req, objectMapper, ApiResponse.fail(500, "SYSTEM_ERROR"));
    }
  }

  private <T> T readBody(FullHttpRequest req, Class<T> type) {
    try {
      String json = req.content().toString(StandardCharsets.UTF_8);
      if (json == null || json.isBlank()) {
        throw new BusinessException(400, "request body is required");
      }
      return objectMapper.readValue(json, type);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new SystemException("invalid json", e);
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof IdleStateEvent) {
      ctx.close();
    } else {
      ctx.fireUserEventTriggered(evt);
    }
  }
}
