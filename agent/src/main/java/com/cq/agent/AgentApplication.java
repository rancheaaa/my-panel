package com.cq.agent;

import com.cq.agent.di.AppContext;
import com.cq.agent.di.AgentBootstrap;
import org.slf4j.LoggerFactory;

public class AgentApplication {

    public static void main(String[] args) {
        try {
            LoggerFactory.getLogger(AgentApplication.class);
            AppContext ctx = AppContext.getInstance();
            ctx.init();
            AgentBootstrap bootstrap = ctx.getAgentBootstrap();
            bootstrap.start();
        } catch (Exception e) {
            System.err.println("Failed to start agent: " + e.getMessage());
            System.exit(1);
        }
    }
}
