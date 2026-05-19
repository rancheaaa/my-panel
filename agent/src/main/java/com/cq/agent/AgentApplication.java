package com.cq.agent;

import com.cq.agent.di.AgentBootstrap;
import com.cq.agent.di.AgentModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentApplication {

    private static final Logger logger = LoggerFactory.getLogger(AgentApplication.class);

    public static void main(String[] args) {
        try {
            Injector injector = Guice.createInjector(new AgentModule());
            AgentBootstrap bootstrap = injector.getInstance(AgentBootstrap.class);
            bootstrap.start();
        } catch (Exception e) {
            logger.error("Failed to start agent", e);
            System.exit(1);
        }
    }
}
