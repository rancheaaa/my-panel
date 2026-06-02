package com.cq.agent;

import com.cq.agent.di.AgentBootstrap;
import com.cq.agent.di.AgentModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.LoggerFactory;

public class AgentApplication {

    public static void main(String[] args) {
        try {
            LoggerFactory.getLogger(AgentApplication.class);
            Injector injector = Guice.createInjector(new AgentModule());
            AgentBootstrap bootstrap = injector.getInstance(AgentBootstrap.class);
            bootstrap.start();
        } catch (Exception e) {
            System.err.println("Failed to start agent: " + e.getMessage());
            System.exit(1);
        }
    }
}
