 package com.cq.agent.server;

import com.cq.agent.config.AgentConfig;
import com.cq.agent.executor.CommandExecutor;
import com.cq.agent.handler.FileHandler;
import com.cq.agent.handler.HandlerFactory;
import com.cq.agent.handler.HttpServerHandler;
import com.cq.agent.service.ChunkedTransferService;
import com.cq.agent.service.FileService;
import com.cq.panel.common.utils.PortUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Netty-based HTTP server for the agent.
 */
public class HttpServer {

    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    private final AgentConfig config;
    private final CommandExecutor commandExecutor;
    private final HandlerFactory handlerFactory;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * -- GETTER --
     *  获取实际监听的端口号
     *
     */
    @Getter
    private int actualPort;

    public HttpServer(AgentConfig config, CommandExecutor commandExecutor, FileService fileService, ChunkedTransferService chunkedTransferService) {
        this.config = config;
        this.commandExecutor = commandExecutor;
        this.handlerFactory = new HandlerFactory(fileService, chunkedTransferService);
    }

    /**
     * Start the HTTP server.
     */
    public void start() throws InterruptedException {
        int bossThreads = config.getBossThreads();
        int workerThreads = config.getWorkerThreads();

        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = workerThreads > 0 ? new NioEventLoopGroup(workerThreads) : new NioEventLoopGroup();

        int idleTimeout = config.getConnectionIdleTimeoutSeconds();
        int maxContentLength = config.getMaxContentLength();
        int configuredPort = config.getServerPort();
        int maxProbeSteps = config.getPortProbeMaxSteps();

        int port = configuredPort;
        if (maxProbeSteps > 0) {
            int availablePort = PortUtils.probeAvailablePort(configuredPort, maxProbeSteps);
            if (availablePort == -1) {
                logger.error("无法在端口 {} 到 {} 范围内找到可用端口，最大探测步数：{}", 
                        configuredPort, configuredPort + maxProbeSteps - 1, maxProbeSteps);
                throw new RuntimeException("无法找到可用端口");
            }
            
            if (availablePort != configuredPort) {
                logger.info("配置端口 {} 已被占用，自动切换到端口 {}", configuredPort, availablePort);
            }
            
            port = availablePort;
        }

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new IdleStateHandler(idleTimeout, 0, 0, TimeUnit.SECONDS));
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(maxContentLength));
                        pipeline.addLast(new FileHandler(handlerFactory));
                        pipeline.addLast(new HttpServerHandler(commandExecutor));
                    }
                });

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannel = future.channel();
        actualPort = port;
        logger.info("Agent HTTP server started on port {}", actualPort);
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        logger.info("Stopping Agent HTTP server...");

        if (serverChannel != null) {
            serverChannel.close();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        commandExecutor.shutdown();
        logger.info("Agent HTTP server stopped");
    }

    /**
     * Wait for the server to close.
     */
    public void awaitTermination() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

}