package com.cq.proxy.netty;

import com.cq.proxy.config.ProxyNettyProperties;
import com.cq.proxy.netty.handler.ProxyExceptionHandler;
import com.cq.proxy.netty.handler.ProxyHttpHandler;
import com.cq.proxy.netty.handler.ProxyWebSocketFrameHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProxyServerInitializer extends ChannelInitializer<SocketChannel> {

  private final ProxyNettyProperties properties;
  private final ProxyHttpHandler httpHandler;
  private final ProxyWebSocketFrameHandler webSocketFrameHandler;
  private final ProxyExceptionHandler exceptionHandler;

  public ProxyServerInitializer(
      ProxyNettyProperties properties,
      ProxyHttpHandler httpHandler,
      ProxyWebSocketFrameHandler webSocketFrameHandler,
      ProxyExceptionHandler exceptionHandler
  ) {
    this.properties = properties;
    this.httpHandler = httpHandler;
    this.webSocketFrameHandler = webSocketFrameHandler;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  protected void initChannel(SocketChannel ch) {
    ch.pipeline()
        .addLast("idle", new IdleStateHandler(0, 0, properties.idleSeconds(), TimeUnit.SECONDS))
        .addLast("decoder", new HttpRequestDecoder())
        .addLast("encoder", new HttpResponseEncoder())
        .addLast("aggregator", new HttpObjectAggregator(1024 * 1024))
        .addLast("ws-protocol", new WebSocketServerProtocolHandler("/api/v1/ws", null, true))
        .addLast("ws-handler", webSocketFrameHandler)
        .addLast("http-handler", httpHandler)
        .addLast("exception", exceptionHandler);
  }
}

