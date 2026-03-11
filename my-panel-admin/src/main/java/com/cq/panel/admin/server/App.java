package com.cq.panel.admin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import de.codecentric.boot.admin.server.config.EnableAdminServer;

/**
 * 启动程序
 * 
 * @author cq
 */
@EnableAdminServer
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class App
{
    public static void main(String[] args)
    {
//        System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(App.class, args);
    }
}
