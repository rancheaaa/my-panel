package com.cq.agent.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

public interface IRequestHandler {
    void handle(ChannelHandlerContext ctx, FullHttpRequest request);
}
