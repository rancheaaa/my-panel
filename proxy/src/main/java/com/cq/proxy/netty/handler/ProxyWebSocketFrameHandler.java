package com.cq.proxy.netty.handler;

import com.cq.proxy.service.WebSocketBroadcastService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class ProxyWebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

  private static final AttributeKey<Boolean> WS_UPGRADED = AttributeKey.valueOf("proxy.ws.upgraded");

  private final WebSocketBroadcastService broadcastService;

  public ProxyWebSocketFrameHandler(WebSocketBroadcastService broadcastService) {
    this.broadcastService = broadcastService;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    broadcastService.channels().add(ctx.channel());
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) {
    broadcastService.channels().remove(ctx.channel());
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
    if (frame instanceof PingWebSocketFrame) {
      ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
      return;
    }
    if (frame instanceof PongWebSocketFrame) {
      return;
    }
    if (frame instanceof CloseWebSocketFrame) {
      ctx.close();
      return;
    }
    if (frame instanceof TextWebSocketFrame text) {
      if ("PING".equalsIgnoreCase(text.text())) {
        ctx.writeAndFlush(new TextWebSocketFrame("PONG"));
      }
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
      ctx.channel().attr(WS_UPGRADED).set(true);
      return;
    }
    if (evt instanceof IdleStateEvent) {
      Boolean upgraded = ctx.channel().attr(WS_UPGRADED).get();
      if (Boolean.TRUE.equals(upgraded)) {
        ctx.writeAndFlush(new PingWebSocketFrame());
      } else {
        ctx.fireUserEventTriggered(evt);
      }
    } else {
      ctx.fireUserEventTriggered(evt);
    }
  }
}
