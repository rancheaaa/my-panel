package com.cq.panel.common.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 端口工具类
 * 
 * @author cq
 */
public class PortUtils
{
    /**
     * 检查端口是否可用
     * 
     * @param port 端口号
     * @return true-可用 false-已占用
     */
    public static boolean isPortAvailable(int port)
    {
        try (ServerSocket socket = new ServerSocket())
        {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(port), 1);
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /**
     * 探测可用端口
     * 从指定端口开始探测，如果端口被占用则递增，直到找到可用端口或达到最大探测步数
     * 
     * @param startPort 起始端口
     * @param maxSteps 最大探测步数
     * @return 可用端口号，如果未找到则返回-1
     */
    public static int probeAvailablePort(int startPort, int maxSteps)
    {
        if (maxSteps <= 0)
        {
            maxSteps = 10;
        }

        for (int i = 0; i < maxSteps; i++)
        {
            int port = startPort + i;
            if (isPortAvailable(port))
            {
                return port;
            }
        }

        return -1;
    }
    
    public static PortDetectEnum portDetect(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            // 设置连接超时
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoLinger(true, 0);
            // 如果连接成功，立即关闭连接（我们只需要验证连接性）
            if (socket.isConnected()) {
                return PortDetectEnum.SUCCESS;
            } else {
                return PortDetectEnum.CONNECTION_FAILED;
            }
        } catch (java.net.ConnectException e) {
            // 连接被拒绝，通常表示端口未监听
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                return PortDetectEnum.PORT_NOT_LISTEN;
            }
            // 其他连接异常
            return PortDetectEnum.CONNECTION_FAILED;
        } catch (java.net.SocketTimeoutException e) {
            // 连接超时
            return PortDetectEnum.CONNECTION_TIMEOUT;
        } catch (java.net.NoRouteToHostException e) {
            // 无法路由到主机，主机不可达
            return PortDetectEnum.HOST_UNREACHABLE;
        } catch (java.net.UnknownHostException e) {
            // 未知主机
            return PortDetectEnum.UNKNOWN_HOST;
        } catch (java.net.SocketException e) {
            // 其他Socket异常
            if (e.getMessage() != null && e.getMessage().contains("Network is unreachable")) {
                return PortDetectEnum.NETWORK_UNREACHABLE;
            }
            return PortDetectEnum.SOCKET_ERROR;
        } catch (SecurityException e) {
            // 安全异常，可能没有权限访问网络
            return PortDetectEnum.SECURITY_ERROR;
        } catch (IllegalArgumentException e) {
            // 参数错误，如端口号超出范围
            if (e.getMessage() != null && e.getMessage().contains("port out of range")) {
                return PortDetectEnum.INVALID_PORT;
            }
            return PortDetectEnum.ILLEGAL_ARGUMENT;
        } catch (Exception e) {
            // 其他未知异常
            return PortDetectEnum.UNKNOWN_ERROR;
        }
    }

    public static boolean portDetectFast(String host, int port, int timeoutMs) {
        return portDetect(host, port, timeoutMs) == PortDetectEnum.SUCCESS;
    }
    
    public enum PortDetectEnum
    {
        SUCCESS,
        PORT_NOT_LISTEN,
        PORT_NOT_AVAILABLE,
        CONNECTION_TIMEOUT,
        CONNECTION_FAILED,
        HOST_UNREACHABLE,
        UNKNOWN_HOST,
        NETWORK_UNREACHABLE,
        SOCKET_ERROR,
        SECURITY_ERROR,
        INVALID_PORT,
        ILLEGAL_ARGUMENT,
        UNKNOWN_ERROR
    }
}