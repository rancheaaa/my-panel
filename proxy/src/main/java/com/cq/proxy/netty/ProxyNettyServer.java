package com.cq.proxy.netty;

import com.cq.proxy.config.ProxyNettyProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProxyNettyServer {

  private static final Logger log = LoggerFactory.getLogger(ProxyNettyServer.class);

  private final ProxyNettyProperties properties;
  private final ProxyServerInitializer initializer;

  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Channel serverChannel;

  public ProxyNettyServer(ProxyNettyProperties properties, ProxyServerInitializer initializer) {
    this.properties = properties;
    this.initializer = initializer;
  }

  public synchronized void start() {
    if (serverChannel != null) {
      return;
    }

    bossGroup = new NioEventLoopGroup(properties.bossThreads());
    workerGroup = new NioEventLoopGroup(properties.workerThreads());

    try {
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .option(ChannelOption.SO_BACKLOG, properties.soBacklog())
          .childOption(ChannelOption.SO_KEEPALIVE, properties.soKeepalive())
          .childOption(ChannelOption.TCP_NODELAY, properties.tcpNodelay())
          .childHandler(initializer);

      serverChannel = bootstrap.bind(properties.port()).syncUninterruptibly().channel();
      log.info("Proxy Netty server started on {}", properties.port());
    } catch (Exception e) {
      stop();
      throw e;
    }
  }

  public synchronized void stop() {
    try {
      if (serverChannel != null) {
        serverChannel.close().syncUninterruptibly();
      }
    } finally {
      serverChannel = null;
      if (bossGroup != null) {
        bossGroup.shutdownGracefully();
        bossGroup = null;
      }
      if (workerGroup != null) {
        workerGroup.shutdownGracefully();
        workerGroup = null;
      }
      log.info("Proxy Netty server stopped");
    }
  }
}

