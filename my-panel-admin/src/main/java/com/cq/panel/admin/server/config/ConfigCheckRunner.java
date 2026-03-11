package com.cq.panel.admin.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ConfigCheckRunner implements CommandLineRunner {

    @Value("${app.mode:unknown}")
    private String appMode;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("========================================");
        System.out.println("Current app.mode: " + appMode);
        System.out.println("========================================");
    }
}
