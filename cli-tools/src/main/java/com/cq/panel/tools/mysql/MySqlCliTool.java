package com.cq.panel.tools.mysql;

import com.cq.panel.common.utils.SystemEnvUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.Callable;

@SuppressWarnings("all")
@Command(name = "mysql-tool", mixinStandardHelpOptions = true, version = "mysql-tool 1.0",
        description = "MySQL command line tool for connection checking and SQL execution")
public class MySqlCliTool implements Callable<Integer> {

    private static volatile String CONFIG_PATH;

    static {
        try {
            final File file = new File(MySqlCliTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String absolutePath = file.getAbsolutePath();
            String rootPath = Paths.get(absolutePath).getParent().getParent().getParent().normalize().toAbsolutePath().toString();
            CONFIG_PATH = Paths.get(rootPath, "config", "application-cluster.yml").toString();
        } catch (URISyntaxException e) {
            CONFIG_PATH = Paths.get(SystemEnvUtils.getProjectRootPath(), "config", "application-cluster.yml").toString();
        }
    }

    @Option(names = {"-c", "--check"}, description = "Check MySQL connection and port availability")
    private boolean checkConnection;

    @Option(names = {"-e", "--execute"}, paramLabel = "<SQL>", description = "Execute SQL statement")
    private String executeSql;

    @Option(names = {"-f", "--file"}, paramLabel = "<FILE>", description = "Execute SQL from file")
    private String sqlFile;

    @Option(names = {"-H", "--host"}, paramLabel = "<HOST>", description = "MySQL host (override config)")
    private String hostOverride;

    @Option(names = {"-P", "--port"}, paramLabel = "<PORT>", description = "MySQL port (override config)")
    private Integer portOverride;

    @Option(names = {"-u", "--username"}, paramLabel = "<USER>", description = "MySQL username (override config)")
    private String usernameOverride;

    @Option(names = {"-p", "--password"}, paramLabel = "<PASSWORD>", description = "MySQL password (override config)", interactive = true)
    private String passwordOverride;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MySqlCliTool()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Map<String, Object> config = loadConfig();
        Map<String, Object> datasource = extractDatasourceConfig(config);

        String host = hostOverride != null ? hostOverride : extractHost(datasource);
        int port = portOverride != null ? portOverride : extractPort(datasource);
        String url = extractUrl(datasource);
        String username = usernameOverride != null ? usernameOverride : extractUsername(datasource);
        String password = passwordOverride != null ? passwordOverride : extractPassword(datasource);

        if (hostOverride != null) {
            url = buildUrl(host, port, extractDatabaseName(datasource));
        }

        if (checkConnection) {
            return checkConnection(host, port);
        } else if (executeSql != null) {
            return executeSql(url, username, password, executeSql) ? 0 : 1;
        } else if (sqlFile != null) {
            return executeSqlFile(url, username, password, sqlFile) ? 0 : 1;
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
    private Map<String, Object> extractDatasourceConfig(Map<String, Object> config) {
        Map<String, Object> spring = (Map<String, Object>) config.get("spring");
        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
        Map<String, Object> druid = (Map<String, Object>) datasource.get("druid");
        return (Map<String, Object>) druid.get("master");
    }

    private String extractHost(Map<String, Object> datasource) {
        String url = (String) datasource.get("url");
        String[] parts = url.split("/")[2].split(":");
        return parts[0];
    }

    private int extractPort(Map<String, Object> datasource) {
        String url = (String) datasource.get("url");
        String[] parts = url.split("/")[2].split(":");
        return Integer.parseInt(parts[1]);
    }

    private String extractUrl(Map<String, Object> datasource) {
        return (String) datasource.get("url");
    }

    private String extractDatabaseName(Map<String, Object> datasource) {
        String url = (String) datasource.get("url");
        String[] parts = url.split("/");
        String dbAndParams = parts[3];
        return dbAndParams.split("\\?")[0];
    }

    private String extractUsername(Map<String, Object> datasource) {
        return (String) datasource.get("username");
    }

    private String extractPassword(Map<String, Object> datasource) {
        return (String) datasource.get("password");
    }

    private String buildUrl(String host, int port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull" +
                "&autoReconnect=true&serverTimezone=Asia/Shanghai";
    }

    private int checkConnection(String host, int port) {
        System.out.println("Checking MySQL connection...");
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
                System.err.println("Possible cause: MySQL service may not be running or port is blocked by firewall");
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

    private boolean executeSql(String url, String username, String password, String sql) {
        System.out.println("Executing SQL: " + sql);

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {

            boolean isResultSet = stmt.execute(sql);

            if (isResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    for (int i = 1; i <= columnCount; i++) {
                        System.out.print(metaData.getColumnName(i) + "\t");
                    }
                    System.out.println();

                    while (rs.next()) {
                        for (int i = 1; i <= columnCount; i++) {
                            System.out.print(rs.getString(i) + "\t");
                        }
                        System.out.println();
                    }
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                System.out.println("Affected rows: " + updateCount);
            }

            System.out.println("SQL executed successfully");
            return true;

        } catch (SQLException e) {
            System.err.println("SQL execution failed: " + e.getMessage());
            return false;
        }
    }

    private boolean executeSqlFile(String url, String username, String password, String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("SQL file not found: " + filePath);
            return false;
        }

        System.out.println("Executing SQL file: " + filePath);

        try (Connection conn = DriverManager.getConnection(url, username, password);
             BufferedReader reader = new BufferedReader(new FileReader(file))) {

            StringBuilder sqlBuilder = new StringBuilder();
            String line;
            int lineCount = 0;
            int executedCount = 0;

            conn.setAutoCommit(false);

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--") || line.startsWith("#")) {
                    continue;
                }

                sqlBuilder.append(line).append(" ");

                if (line.endsWith(";")) {
                    String sql = sqlBuilder.toString().trim();
                    sql = sql.substring(0, sql.length() - 1);

                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(sql);
                        executedCount++;
                    } catch (SQLException e) {
                        System.err.println("Error executing SQL at line " + lineCount + ": " + e.getMessage());
                        conn.rollback();
                        return false;
                    }

                    sqlBuilder = new StringBuilder();
                }

                lineCount++;
            }

            conn.commit();
            System.out.println("Successfully executed " + executedCount + " SQL statements");
            return true;

        } catch (SQLException | IOException e) {
            System.err.println("Failed to execute SQL file: " + e.getMessage());
            return false;
        }
    }
}
