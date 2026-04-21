package com.cq.panel.admin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import de.codecentric.boot.admin.server.config.EnableAdminServer;

/**
 * 启动程序
 *
 * @author cq
 */
@EnableAdminServer
@SpringBootApplication(scanBasePackages = { "com.cq.panel.admin.server", "com.cq.panel.authlite" })
public class App
{
    public static void main(String[] args)
    {
        SpringApplication.run(App.class, args);
    }
}
