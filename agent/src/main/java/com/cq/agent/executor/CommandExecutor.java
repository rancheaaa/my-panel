package com.cq.agent.executor;

import com.cq.agent.config.AgentConfig;
import com.cq.panel.common.dto.agent.AgentExecuteCommandResponse;
import com.google.inject.Inject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CommandExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CommandExecutor.class);
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    private static final Charset OUTPUT_CHARSET = IS_WINDOWS
            ? Charset.forName(System.getProperty("sun.jnu.encoding", "GBK"))
            : StandardCharsets.UTF_8;

    @Getter
    private final long defaultTimeoutSeconds;
    @Getter
    private final long maxTimeoutSeconds;

    private final ExecutorService virtualThreadPool;

    @Inject
    public CommandExecutor(AgentConfig config) {
        this.defaultTimeoutSeconds = config.getDefaultTimeoutSeconds();
        this.maxTimeoutSeconds = config.getMaxTimeoutSeconds();
        this.virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();
        logger.info("CommandExecutor initialized for {} system, charset: {} (virtual thread pool)",
                IS_WINDOWS ? "Windows" : "Linux/Unix", OUTPUT_CHARSET);
    }

    public boolean isWindows() {
        return IS_WINDOWS;
    }

    public String getOsName() {
        return System.getProperty("os.name");
    }

    public AgentExecuteCommandResponse execute(String command) {
        return execute(command, defaultTimeoutSeconds);
    }

    public AgentExecuteCommandResponse execute(String command, long timeoutSeconds) {
        if (command == null || command.trim().isEmpty()) {
            return createErrorResponse(-1, null, "Command cannot be empty");
        }

        if (timeoutSeconds <= 0) {
            timeoutSeconds = defaultTimeoutSeconds;
        }
        if (timeoutSeconds > maxTimeoutSeconds) {
            timeoutSeconds = maxTimeoutSeconds;
            logger.warn("Requested timeout exceeds maximum, using max: {}s", maxTimeoutSeconds);
        }

        logger.info("Executing command: {}, timeout: {}s", command, timeoutSeconds);

        try {
            return executeInternal(command, timeoutSeconds);
        } catch (Exception e) {
            logger.error("Command execution failed: {}", command, e);
            return createErrorResponse(-1, null, "Command execution failed: " + e.getMessage());
        }
    }

    private AgentExecuteCommandResponse createErrorResponse(int exitCode, String output, String error) {
        AgentExecuteCommandResponse response = new AgentExecuteCommandResponse();
        response.setSuccess(false);
        response.setExitCode(exitCode);
        response.setOutput(output);
        response.setError(error);
        return response;
    }

    private AgentExecuteCommandResponse createSuccessResponse(int exitCode, String output) {
        AgentExecuteCommandResponse response = new AgentExecuteCommandResponse();
        response.setSuccess(true);
        response.setExitCode(exitCode);
        response.setOutput(output);
        response.setError(null);
        return response;
    }

    private AgentExecuteCommandResponse executeInternal(String command, long timeoutSeconds) throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        if (IS_WINDOWS) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("/bin/sh", "-c", command);
        }
        pb.redirectErrorStream(false);

        final Process process = pb.start();
        logger.info("Command {} Process started at pid [{}]", command, process.pid());

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killProcessTree(process);
            return createErrorResponse(-3, null, "Command execution interrupted");
        }

        if (!finished) {
            virtualThreadPool.submit(() -> {
                logger.warn("Command execution timed out after {}s: {}", timeoutSeconds, command);
                killProcessTree(process);
            });
            return createErrorResponse(-999, null,
                    "Command execution timed out after " + timeoutSeconds + " seconds");
        }

        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), OUTPUT_CHARSET))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdoutBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            logger.debug("stdout stream closed: {}", e.getMessage());
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), OUTPUT_CHARSET))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stderrBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            logger.debug("stderr stream closed: {}", e.getMessage());
        }

        int exitCode = process.exitValue();
        String stdout = stdoutBuilder.toString().trim();
        String stderr = stderrBuilder.toString().trim();

        closeProcessStreams(process);

        if (exitCode == 0) {
            return createSuccessResponse(exitCode, stdout);
        } else {
            String errorMsg = stderr.isEmpty() ? "Command exited with code " + exitCode : stderr;
            return createErrorResponse(exitCode, stdout, errorMsg);
        }
    }

    private void closeProcessStreams(Process process) {
        try {
            InputStream stdout = process.getInputStream();
            InputStream stderr = process.getErrorStream();
            OutputStream stdin = process.getOutputStream();

            if (stdout != null) stdout.close();
            if (stderr != null) stderr.close();
            if (stdin != null) stdin.close();
        } catch (IOException e) {
            logger.error("Error closing process streams: {}", e.getMessage());
        }
    }

    private void killProcessTree(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }

        long pid = process.pid();
        logger.info("ready to Kill process tree with pid [{}]", pid);

        if (IS_WINDOWS) {
            try {
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("taskkill failed: {}, using destroyForcibly", e.getMessage());
                process.destroyForcibly();
            }
        } else {
            try {
                new ProcessBuilder("kill", "-TERM", "-" + pid)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(100, TimeUnit.MILLISECONDS);

                if (process.isAlive()) {
                    new ProcessBuilder("kill", "-0", "-" + pid)
                            .redirectErrorStream(true)
                            .start()
                            .waitFor(50, TimeUnit.MILLISECONDS);

                    new ProcessBuilder("kill", "-9", "-" + pid)
                            .redirectErrorStream(true)
                            .start()
                            .waitFor(100, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                logger.warn("kill process group failed: {}, using destroyForcibly", e.getMessage());
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }

        logger.info("Kill process tree with pid [{}] done, process isAlive: {}", pid, process.isAlive());
    }

    public void shutdown() {
        try {
            virtualThreadPool.shutdown();
            if (!virtualThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                virtualThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("CommandExecutor shutdown");
    }
}