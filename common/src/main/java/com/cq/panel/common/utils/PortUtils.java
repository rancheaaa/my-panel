package com.cq.panel.common.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

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
}