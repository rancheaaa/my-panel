package com.cq.proxy.netty.handler;

import com.cq.proxy.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class NettyHttpUtil {

  private NettyHttpUtil() {
  }

  static String getQueryParam(Map<String, List<String>> params, String name) {
    List<String> values = params.get(name);
    if (values == null || values.isEmpty()) {
      return null;
    }
    return decode(values.getFirst());
  }

  static void writeJson(ChannelHandlerContext ctx, FullHttpRequest req, ObjectMapper mapper, ApiResponse<?> body) {
    try {
      byte[] bytes = mapper.writeValueAsBytes(body);
      FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
      resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
      resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
      boolean keepAlive = HttpUtil.isKeepAlive(req);
      if (keepAlive) {
        resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        ctx.writeAndFlush(resp);
      } else {
        ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
      }
    } catch (Exception e) {
      FullHttpResponse resp = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1,
          HttpResponseStatus.INTERNAL_SERVER_ERROR,
          Unpooled.copiedBuffer("{\"code\":500,\"message\":\"serialization error\",\"data\":null}", CharsetUtil.UTF_8));
      resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
      ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
    }
  }

  private static String decode(String value) {
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return value;
    }
  }
}

