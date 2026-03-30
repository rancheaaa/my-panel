package com.cq.proxy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WebSocketBroadcastService {

  private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
  private final ObjectMapper objectMapper;

  public WebSocketBroadcastService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ChannelGroup channels() {
    return channels;
  }

  public void broadcastConfigUpdated(String serviceName, String environment, String configKey) {
    try {
      String json = objectMapper.writeValueAsString(Map.of(
          "type", "CONFIG_UPDATED",
          "serviceName", serviceName,
          "environment", environment,
          "configKey", configKey
      ));
      channels.writeAndFlush(new TextWebSocketFrame(json));
    } catch (Exception ignored) {
    }
  }
}

