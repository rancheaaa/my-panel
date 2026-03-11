package com.cq.agent.executor;

import com.cq.agent.config.AgentConfig;
import org.apache.commons.exec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Command executor using Apache Commons Exec with timeout handling.
 * Supports both Windows and Linux/Unix systems.
 */
public class CommandExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    // Charset for command output
    private static final Charset OUTPUT_CHARSET = IS_WINDOWS
            ? Charset.forName(System.getProperty("sun.jnu.encoding", "GBK"))
            : StandardCharsets.UTF_8;

    private final long defaultTimeoutSeconds;
    private final long maxTimeoutSeconds;

    public CommandExecutor(AgentConfig config) {
        this.defaultTimeoutSeconds = config.getDefaultTimeoutSeconds();
        this.maxTimeoutSeconds = config.getMaxTimeoutSeconds();
        logger.info("CommandExecutor initialized for {} system, charset: {}",
                IS_WINDOWS ? "Windows" : "Linux/Unix", OUTPUT_CHARSET);
    }

    /**
     * Check if the current OS is Windows.
     */
    public boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Get the OS name.
     */
    public String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * Get the default timeout in seconds.
     */
    public long getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    /**
     * Get the maximum allowed timeout in seconds.
     */
    public long getMaxTimeoutSeconds() {
        return maxTimeoutSeconds;
    }

    /**
     * Execute a command with default timeout.
     */
    public CommandResult execute(String command) {
        return execute(command, defaultTimeoutSeconds);
    }

    /**
     * Execute a command with specified timeout.
     */
    public CommandResult execute(String command, long timeoutSeconds) {
        if (command == null || command.trim().isEmpty()) {
            return new CommandResult(-1, "", "Command cannot be empty");
        }

        // Validate timeout
        if (timeoutSeconds <= 0) {
            timeoutSeconds = defaultTimeoutSeconds;
        }
        if (timeoutSeconds > maxTimeoutSeconds) {
            timeoutSeconds = maxTimeoutSeconds;
            logger.warn("Requested timeout exceeds maximum, using max: {}s", maxTimeoutSeconds);
        }

        logger.info("Executing command: {}, timeout: {}s", command, timeoutSeconds);

        try {
            return executeWithCommonsExec(command, timeoutSeconds);
        } catch (Exception e) {
            logger.error("Command execution failed: {}", command, e);
            return new CommandResult(-1, "", "Command execution failed: " + e.getMessage());
        }
    }

    private CommandResult executeWithCommonsExec(String command, long timeoutSeconds) {
        // Build command line based on OS
        CommandLine cmdLine;
        if (IS_WINDOWS) {
            cmdLine = new CommandLine("cmd.exe");
            cmdLine.addArgument("/c");
            cmdLine.addArgument(command, false);
        } else {
            cmdLine = new CommandLine("/bin/sh");
            cmdLine.addArgument("-c");
            cmdLine.addArgument(command, false);
        }

        // Set up output streams
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr);

        // Create executor with timeout watchdog
        DefaultExecutor executor = DefaultExecutor.builder().get();
        executor.setStreamHandler(streamHandler);

        // Set up watchdog for timeout
        long timeoutMillis = timeoutSeconds * 1000;
        ExecuteWatchdog watchdog = ExecuteWatchdog.builder()
                .setTimeout(java.time.Duration.ofMillis(timeoutMillis))
                .get();
        executor.setWatchdog(watchdog);

        // Allow any exit value (we'll check it ourselves)
        executor.setExitValues(null);

        try {
            int exitCode = executor.execute(cmdLine);

            String output = stdout.toString(OUTPUT_CHARSET).trim();
            String error = stderr.toString(OUTPUT_CHARSET).trim();

            logger.info("Command completed with exit code: {}", exitCode);

            if (exitCode == 0) {
                return new CommandResult(exitCode, output, null);
            } else {
                String errorMessage = error.isEmpty() ? "Command exited with code " + exitCode : error;
                return new CommandResult(exitCode, output, errorMessage);
            }

        } catch (ExecuteException e) {
            // Check if this was caused by timeout
            if (watchdog.killedProcess()) {
                logger.warn("Command execution timed out after {}s: {}", timeoutSeconds, command);
                return new CommandResult(-1, stdout.toString(OUTPUT_CHARSET).trim(),
                        "Command execution timed out after " + timeoutSeconds + " seconds");
            }

            String output = stdout.toString(OUTPUT_CHARSET).trim();
            String error = stderr.toString(OUTPUT_CHARSET).trim();
            int exitCode = e.getExitValue();

            logger.warn("Command execution failed with exit code {}: {}", exitCode, command);
            return new CommandResult(exitCode, output,
                    error.isEmpty() ? "Command exited with code " + exitCode : error);

        } catch (IOException e) {
            logger.error("Failed to execute command: {}", command, e);
            return new CommandResult(-1, "", "Failed to execute command: " + e.getMessage());
        }
    }

    /**
     * Shutdown the executor (cleanup resources if any).
     */
    public void shutdown() {
        // Apache Commons Exec doesn't require explicit shutdown
        // but we keep this method for consistency
        logger.info("CommandExecutor shutdown");
    }

    /**
     * Command execution result.
     */
    public record CommandResult(int exitCode, String output, String error) {
        public boolean isSuccess() {
            return exitCode == 0 && error == null;
        }
    }
}
