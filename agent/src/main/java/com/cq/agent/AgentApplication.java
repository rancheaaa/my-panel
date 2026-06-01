package com.cq.agent;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter2;
import com.cq.agent.di.AgentBootstrap;
import com.cq.agent.di.AgentModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.LoggerFactory;
import java.util.Objects;

public class AgentApplication {

    public static void main(String[] args) {
        try {
            initLogback();
            LoggerFactory.getLogger(AgentApplication.class);
            Injector injector = Guice.createInjector(new AgentModule());
            AgentBootstrap bootstrap = injector.getInstance(AgentBootstrap.class);
            bootstrap.start();
        } catch (Exception e) {
            System.err.println("Failed to start agent: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void initLogback() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        String configPath = System.getProperty("logback.configurationFile", "logback.xml");
        try {
            configurator.doConfigure(Objects.requireNonNull(AgentApplication.class.getClassLoader().getResource(configPath)));
        } catch (Exception e) {
            System.err.println("Failed to load logback config: " + configPath + ", using default");
        }
        final StatusPrinter2 statusPrinter2 = new StatusPrinter2();
        statusPrinter2.printInCaseOfErrorsOrWarnings(loggerContext);
    }
}
