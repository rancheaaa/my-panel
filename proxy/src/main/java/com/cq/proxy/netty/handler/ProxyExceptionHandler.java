package com.cq.proxy.netty.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class ProxyExceptionHandler extends ChannelInboundHandlerAdapter {

  private static final Logger log = LoggerFactory.getLogger(ProxyExceptionHandler.class);

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("Netty pipeline error", cause);
    ctx.close();
  }
}

