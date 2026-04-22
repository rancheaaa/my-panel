package com.cq.panel.tools.redis;

import com.cq.panel.common.utils.SystemEnvUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.yaml.snakeyaml.Yaml;
import redis.clients.jedis.Jedis;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@SuppressWarnings("all")
@Command(name = "redis-tool", mixinStandardHelpOptions = true, version = "redis-tool 1.0",
        description = "Redis command line tool for connection checking and command execution")
public class RedisCliTool implements Callable<Integer> {

    private static volatile String CONFIG_PATH;

    static {
        try {
            final File file = new File(RedisCliTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String absolutePath = file.getAbsolutePath();
            String rootPath = Paths.get(absolutePath).getParent().getParent().getParent().normalize().toAbsolutePath().toString();
            CONFIG_PATH = Paths.get(rootPath, "config", "application-cluster.yml").toString();
        } catch (URISyntaxException e) {
            CONFIG_PATH = Paths.get(SystemEnvUtils.getProjectRootPath(), "config", "application-cluster.yml").toString();
        }
    }

    @Option(names = {"-c", "--check"}, description = "Check Redis connection and port availability")
    private boolean checkConnection;

    @Option(names = {"-e", "--execute"}, paramLabel = "<COMMAND>", description = "Execute Redis command (e.g., 'GET key', 'SET key value')")
    private String executeCommand;

    @Option(names = {"-f", "--file"}, paramLabel = "<FILE>", description = "Execute Redis commands from file (one command per line)")
    private String commandFile;

    @Option(names = {"-H", "--host"}, paramLabel = "<HOST>", description = "Redis host (override config)")
    private String hostOverride;

    @Option(names = {"-P", "--port"}, paramLabel = "<PORT>", description = "Redis port (override config)")
    private Integer portOverride;

    @Option(names = {"-a", "--password"}, paramLabel = "<PASSWORD>", description = "Redis password (override config)", interactive = true)
    private String passwordOverride;

    @Option(names = {"-n", "--database"}, paramLabel = "<DB>", description = "Redis database number (override config)")
    private Integer databaseOverride;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RedisCliTool()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Map<String, Object> config = loadConfig();
        Map<String, Object> redisConfig = extractRedisConfig(config);

        String host = hostOverride != null ? hostOverride : extractHost(redisConfig);
        int port = portOverride != null ? portOverride : extractPort(redisConfig);
        String password = passwordOverride != null ? passwordOverride : extractPassword(redisConfig);
        int database = databaseOverride != null ? databaseOverride : extractDatabase(redisConfig);

        if (checkConnection) {
            return checkConnection(host, port);
        } else if (executeCommand != null) {
            return executeCommand(host, port, password, database, executeCommand) ? 0 : 1;
        } else if (commandFile != null) {
            return executeCommandFile(host, port, password, database, commandFile) ? 0 : 1;
        } else {
            System.out.println("No action specified. Use --help for usage information.");
            return 1;
        }
    }

    @SuppressWarnings("all")
    private Map<String, Object> loadConfig() throws IOException {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists()) {
            throw new FileNotFoundException("Config file not found: " + CONFIG_PATH);
        }

        Yaml yaml = new Yaml();
        try (InputStream inputStream = new FileInputStream(configFile)) {
            return yaml.load(inputStream);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRedisConfig(Map<String, Object> config) {
        Map<String, Object> spring = (Map<String, Object>) config.get("spring");
        Map<String, Object> data = (Map<String, Object>) spring.get("data");
        return (Map<String, Object>) data.get("redis");
    }

    private String extractHost(Map<String, Object> redisConfig) {
        return (String) redisConfig.get("host");
    }

    private int extractPort(Map<String, Object> redisConfig) {
        return Integer.parseInt(redisConfig.get("port").toString());
    }

    private String extractPassword(Map<String, Object> redisConfig) {
        Object password = redisConfig.get("password");
        return password != null ? password.toString() : null;
    }

    private int extractDatabase(Map<String, Object> redisConfig) {
        return Integer.parseInt(redisConfig.get("database").toString());
    }

    private int checkConnection(String host, int port) {
        System.out.println("Checking Redis connection...");
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            System.out.println("SUCCESS: Port " + port + " is open and reachable");
            return 0;
        } catch (java.net.ConnectException e) {
            String message = e.getMessage();
            if (message != null && message.toLowerCase().contains("connection refused")) {
                System.err.println("FAILED: Connection refused - Port " + port + " on host " + host + " is not accepting connections");
                System.err.println("Possible cause: Redis service may not be running or port is blocked by firewall");
            } else {
                System.err.println("FAILED: Cannot connect to " + host + ":" + port);
                System.err.println("Error: " + message);
            }
            return 1;
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("FAILED: Connection to " + host + ":" + port + " timed out");
            System.err.println("Possible cause: Firewall may be blocking the connection or network latency is too high");
            return 1;
        } catch (java.net.NoRouteToHostException e) {
            System.err.println("FAILED: No route to host " + host);
            System.err.println("Possible cause: Network is unreachable or host IP address is incorrect");
            return 1;
        } catch (java.net.UnknownHostException e) {
            System.err.println("FAILED: Unknown host " + host);
            System.err.println("Possible cause: Host name cannot be resolved - check your DNS configuration");
            return 1;
        } catch (IOException e) {
            System.err.println("FAILED: Cannot connect to " + host + ":" + port);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private boolean executeCommand(String host, int port, String password, int database, String command) {
        System.out.println("Executing Redis command: " + command);

        try (Jedis jedis = new Jedis(host, port)) {
            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
            }
            jedis.select(database);

            String[] parts = command.split("\\s+");
            String cmd = parts[0].toUpperCase();
            String[] args = new String[parts.length - 1];
            System.arraycopy(parts, 1, args, 0, parts.length - 1);

            Object result = executeRedisCommand(jedis, cmd, args);
            printResult(result);

            System.out.println("Command executed successfully");
            return true;

        } catch (Exception e) {
            System.err.println("Command execution failed: " + e.getMessage());
            return false;
        }
    }

    private Object executeRedisCommand(Jedis jedis, String command, String[] args) {
        return switch (command) {
            case "GET" -> jedis.get(args[0]);
            case "SET" -> jedis.set(args[0], args[1]);
            case "DEL" -> jedis.del(args);
            case "EXISTS" -> jedis.exists(args);
            case "EXPIRE" -> jedis.expire(args[0], Integer.parseInt(args[1]));
            case "TTL" -> jedis.ttl(args[0]);
            case "KEYS" -> jedis.keys(args[0]);
            case "HGET" -> jedis.hget(args[0], args[1]);
            case "HSET" -> jedis.hset(args[0], args[1], args[2]);
            case "HGETALL" -> jedis.hgetAll(args[0]);
            case "LPUSH" ->
                    jedis.lpush(args[0], args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0]);
            case "RPUSH" ->
                    jedis.rpush(args[0], args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0]);
            case "LPOP" -> jedis.lpop(args[0]);
            case "RPOP" -> jedis.rpop(args[0]);
            case "LRANGE" -> jedis.lrange(args[0], Long.parseLong(args[1]), Long.parseLong(args[2]));
            case "SADD" ->
                    jedis.sadd(args[0], args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0]);
            case "SMEMBERS" -> jedis.smembers(args[0]);
            case "ZADD" -> jedis.zadd(args[0], Double.parseDouble(args[1]), args[2]);
            case "ZRANGE" -> jedis.zrange(args[0], Long.parseLong(args[1]), Long.parseLong(args[2]));
            case "INCR" -> jedis.incr(args[0]);
            case "DECR" -> jedis.decr(args[0]);
            case "PING" -> jedis.ping();
            case "INFO" -> jedis.info();
            case "DBSIZE" -> jedis.dbSize();
            case "FLUSHDB" -> jedis.flushDB();
            case "FLUSHALL" -> jedis.flushAll();
            default -> throw new IllegalArgumentException("Unsupported Redis command: " + command);
        };
    }

    private void printResult(Object result) {
        switch (result) {
            case null -> System.out.println("(nil)");
            case String s -> System.out.println(result);
            case Long l -> System.out.println("(integer) " + result);
            case Boolean b -> System.out.println(b ? "OK" : "(nil)");
            case Set<?> set -> {
                if (set.isEmpty()) {
                    System.out.println("(empty list or set)");
                } else {
                    int i = 0;
                    for (Object item : set) {
                        System.out.println((i + 1) + ") " + item);
                        i++;
                    }
                }
            }
            case List<?> list -> {
                if (list.isEmpty()) {
                    System.out.println("(empty list or set)");
                } else {
                    int i = 0;
                    for (Object item : list) {
                        System.out.println((i + 1) + ") " + item);
                        i++;
                    }
                }
            }
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    System.out.println("(empty list or set)");
                } else {
                    int i = 1;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        System.out.println(i + ") " + entry.getKey());
                        System.out.println(i + ") " + entry.getValue());
                        i += 2;
                    }
                }
            }
            default -> System.out.println(result);
        }
    }

    private boolean executeCommandFile(String host, int port, String password, int database, String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("Command file not found: " + filePath);
            return false;
        }

        System.out.println("Executing Redis commands from file: " + filePath);

        try (Jedis jedis = new Jedis(host, port);
             BufferedReader reader = new BufferedReader(new FileReader(file))) {

            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
            }
            jedis.select(database);

            String line;
            int lineCount = 0;
            int executedCount = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                try {
                    String[] parts = line.split("\\s+");
                    String cmd = parts[0].toUpperCase();
                    String[] args = new String[parts.length - 1];
                    System.arraycopy(parts, 1, args, 0, parts.length - 1);

                    Object result = executeRedisCommand(jedis, cmd, args);
                    System.out.println("Line " + lineCount + ": " + formatResult(result));
                    executedCount++;
                } catch (Exception e) {
                    System.err.println("Error executing command at line " + lineCount + ": " + e.getMessage());
                }

                lineCount++;
            }

            System.out.println("Successfully executed " + executedCount + " commands");
            return true;

        } catch (IOException e) {
            System.err.println("Failed to execute command file: " + e.getMessage());
            return false;
        }
    }

    private String formatResult(Object result) {
        if (result == null) {
            return "(nil)";
        } else if (result instanceof java.util.Set || result instanceof java.util.List || result instanceof java.util.Map) {
            return result.toString();
        } else {
            return result.toString();
        }
    }
}
