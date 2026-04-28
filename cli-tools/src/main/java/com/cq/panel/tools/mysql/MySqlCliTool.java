package com.cq.panel.tools.mysql;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.ExcelWriter;
import com.cq.panel.common.utils.SystemEnvUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
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
            CONFIG_PATH = Paths.get(rootPath, "config", "common.properties").toString();
        } catch (URISyntaxException e) {
            CONFIG_PATH = Paths.get(SystemEnvUtils.getProjectRootPath(), "config", "common.properties").toString();
        }
    }

    @Option(names = {"-c", "--check"}, description = "Check MySQL connection and port availability")
    private boolean checkConnection;

    @Option(names = {"-e", "--execute"}, paramLabel = "<SQL>", description = "Execute SQL statement")
    private String executeSql;

    @Option(names = {"-f", "--file"}, paramLabel = "<FILE>", description = "Execute SQL from file")
    private String sqlFile;

    @Option(names = {"-o", "--output"}, paramLabel = "<DIR>", description = "Output directory for results (default: current directory)")
    private String outputDir;

    @Option(names = {"-t", "--type"}, paramLabel = "<TYPE>", description = "Output file type: txt, xls, xlsx (default: txt)")
    private String outputType = "txt";

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
        Properties config = loadConfig();

        String host = hostOverride != null ? hostOverride : extractHost(config);
        int port = portOverride != null ? portOverride : extractPort(config);
        String url = extractUrl(config);
        String username = usernameOverride != null ? usernameOverride : extractUsername(config);
        String password = passwordOverride != null ? passwordOverride : extractPassword(config);

        if (hostOverride != null) {
            url = buildUrl(host, port, extractDatabaseName(config));
        }

        if (checkConnection) {
            return checkConnection(host, port);
        } else if (executeSql != null) {
            return executeSql(url, username, password, executeSql) ? 0 : 1;
        } else if (sqlFile != null) {
            return executeSqlFile(url, username, password, sqlFile, outputDir, outputType) ? 0 : 1;
        } else {
            System.out.println("No action specified. Use --help for usage information.");
            return 1;
        }
    }

    private Properties loadConfig() throws IOException {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists()) {
            throw new FileNotFoundException("Config file not found: " + CONFIG_PATH);
        }

        Properties props = new Properties();
        try (InputStream inputStream = new FileInputStream(configFile)) {
            props.load(inputStream);
        }
        return props;
    }

    private String extractHost(Properties config) {
        String url = config.getProperty("common.datasource.url");
        String[] parts = url.split("/")[2].split(":");
        return parts[0];
    }

    private int extractPort(Properties config) {
        String url = config.getProperty("common.datasource.url");
        String[] parts = url.split("/")[2].split(":");
        if (parts.length > 1) {
            return Integer.parseInt(parts[1].split("\\?")[0]);
        }
        return 3306;
    }

    private String extractUrl(Properties config) {
        return config.getProperty("common.datasource.url");
    }

    private String extractDatabaseName(Properties config) {
        String url = config.getProperty("common.datasource.url");
        String[] parts = url.split("/");
        if (parts.length > 3) {
            String dbAndParams = parts[3];
            return dbAndParams.split("\\?")[0];
        }
        return "";
    }

    private String extractUsername(Properties config) {
        return config.getProperty("common.datasource.username");
    }

    private String extractPassword(Properties config) {
        return config.getProperty("common.datasource.password");
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
        System.out.println("URL: " + url);
        System.out.println("Username: " + username);

        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "30000");

        try (Connection conn = DriverManager.getConnection(url, props);
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
            Throwable cause = e.getCause();
            if (cause != null) {
                System.err.println("Caused by: " + cause.getMessage());
            }
            return false;
        }
    }

    private static class QueryResult {
        String sql;
        String[] headers;
        List<String[]> data;
        String error;
        int rowCount;

        QueryResult(String sql) {
            this.sql = sql;
            this.headers = null;
            this.data = new ArrayList<>();
            this.error = null;
            this.rowCount = 0;
        }
    }

    private boolean executeSqlFile(String url, String username, String password, String filePath, String outputDir, String outputType) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("SQL file not found: " + filePath);
            return false;
        }

        String outputDirPath = outputDir != null ? outputDir.trim() : ".";
        File outputDirFile = new File(outputDirPath);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String outputFile = new File(outputDirPath, "mysql_output_" + timestamp + "." + outputType).getAbsolutePath();

        System.out.println("Executing SQL file: " + filePath);
        System.out.println("Output file: " + outputFile);
        System.out.println("Output type: " + outputType);

        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "30000");

        List<QueryResult> allQueryResults = new ArrayList<>();
        QueryResult currentQuery = null;

        try (Connection conn = DriverManager.getConnection(url, props);
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
                    currentQuery = new QueryResult(sql);

                    try (Statement stmt = conn.createStatement()) {
                        boolean isResultSet = stmt.execute(sql);

                        if (isResultSet) {
                            try (ResultSet rs = stmt.getResultSet()) {
                                ResultSetMetaData metaData = rs.getMetaData();
                                int columnCount = metaData.getColumnCount();

                                String[] headerRow = new String[columnCount];
                                for (int i = 1; i <= columnCount; i++) {
                                    headerRow[i - 1] = metaData.getColumnName(i);
                                }
                                currentQuery.headers = headerRow;

                                while (rs.next()) {
                                    String[] dataRow = new String[columnCount];
                                    for (int i = 1; i <= columnCount; i++) {
                                        dataRow[i - 1] = rs.getString(i);
                                    }
                                    currentQuery.data.add(dataRow);
                                }
                                currentQuery.rowCount = currentQuery.data.size();
                            }
                        } else {
                            int updateCount = stmt.getUpdateCount();
                            currentQuery.rowCount = updateCount;
                        }
                    } catch (SQLException e) {
                        currentQuery.error = e.getMessage();
                    }

                    allQueryResults.add(currentQuery);
                    executedCount++;
                    sqlBuilder = new StringBuilder();
                }

                lineCount++;
            }

            conn.commit();

            writeOutput(allQueryResults, outputFile, outputType);

            System.out.println("Results written to: " + outputFile);
            System.out.println("Successfully executed " + executedCount + " SQL statements");
            return true;

        } catch (SQLException | IOException e) {
            System.err.println("Failed to execute SQL file: " + e.getMessage());
            return false;
        }
    }

    private void writeOutput(List<QueryResult> queryResults, String outputFile, String outputType) throws IOException {
        switch (outputType.toLowerCase()) {
            case "xls", "xlsx" -> writeExcel(queryResults, outputFile, outputType);
            default -> writeTxt(queryResults, outputFile);
        }
    }

    private void writeTxt(List<QueryResult> queryResults, String outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("================================================================================");
            writer.println("                              MySQL Query Results");
            writer.println("================================================================================");
            writer.println();

            int queryIndex = 0;
            for (QueryResult qr : queryResults) {
                queryIndex++;
                writer.println("--------------------------------------------------------------------------------");
                writer.println("[" + queryIndex + "] " + truncateSql(qr.sql));
                writer.println("--------------------------------------------------------------------------------");

                if (qr.error != null) {
                    writer.println("ERROR: " + qr.error);
                } else if (qr.headers != null) {
                    for (int i = 0; i < qr.headers.length; i++) {
                        writer.print(qr.headers[i]);
                        if (i < qr.headers.length - 1) writer.print("\t");
                    }
                    writer.println();

                    for (String[] row : qr.data) {
                        for (int i = 0; i < row.length; i++) {
                            writer.print(row[i] != null ? row[i] : "null");
                            if (i < row.length - 1) writer.print("\t");
                        }
                        writer.println();
                    }
                    writer.println("Total rows: " + qr.rowCount);
                } else {
                    writer.println("Query executed successfully. Affected rows: " + qr.rowCount);
                }
                writer.println();
            }

            writer.println("================================================================================");
            writer.println("Total queries: " + queryResults.size());
        }
    }

    private String truncateSql(String sql) {
        if (sql.length() > 100) {
            return sql.substring(0, 100) + "...";
        }
        return sql;
    }

    private void writeExcel(List<QueryResult> queryResults, String outputFile, String type) {
        ExcelTypeEnum excelType = "xls".equals(type) ? ExcelTypeEnum.XLS : ExcelTypeEnum.XLSX;

        List<QueryResult> selectQueries = new ArrayList<>();
        for (QueryResult qr : queryResults) {
            if (qr.headers != null) {
                selectQueries.add(qr);
            }
        }

        if (selectQueries.isEmpty()) {
            List<List<String>> emptyData = new ArrayList<>();
            List<String> row = new ArrayList<>();
            row.add("No SELECT query results to display");
            emptyData.add(row);
            EasyExcel.write(outputFile)
                    .head(generateExcelHead(new String[]{"Message"}))
                    .excelType(excelType)
                    .sheet("Results")
                    .doWrite(emptyData);
            return;
        }

        WriteCellStyle headerStyle = createHeaderStyle();

        try (ExcelWriter excelWriter = EasyExcel.write(outputFile)
                .excelType(excelType)
                .build()) {

            int sheetIndex = 0;
            for (QueryResult qr : selectQueries) {
                sheetIndex++;
                String sheetName = "Query #" + sheetIndex;

                WriteSheet sheet = EasyExcel.writerSheet(sheetIndex, sheetName)
                        .build();

                List<List<String>> data = new ArrayList<>();

                data.add(createInfoRow("SQL: " + qr.sql));

                List<String> headerRow = new ArrayList<>();
                for (String h : qr.headers) {
                    headerRow.add(h);
                }
                data.add(headerRow);

                for (String[] row : qr.data) {
                    List<String> dataRow = new ArrayList<>();
                    for (String cell : row) {
                        dataRow.add(cell != null ? cell : "");
                    }
                    data.add(dataRow);
                }

                data.add(createInfoRow("Total rows: " + qr.rowCount));

                excelWriter.write(data, sheet);
            }
        }
    }

    private WriteCellStyle createHeaderStyle() {
        WriteCellStyle headerStyle = new WriteCellStyle();
        WriteFont headerFont = new WriteFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setWriteFont(headerFont);
        return headerStyle;
    }

    private List<String> createInfoRow(String text) {
        List<String> row = new ArrayList<>();
        row.add(text);
        return row;
    }

    private List<String> createEmptyRow() {
        List<String> row = new ArrayList<>();
        row.add("");
        return row;
    }

    private List<List<String>> generateExcelHead(String[] headers) {
        List<List<String>> head = new ArrayList<>();
        for (String h : headers) {
            List<String> col = new ArrayList<>();
            col.add(h);
            head.add(col);
        }
        return head;
    }

    private List<List<String>> createExcelHead(String[] headers) {
        List<List<String>> head = new ArrayList<>();
        if (headers != null) {
            for (String header : headers) {
                List<String> col = new ArrayList<>();
                col.add(header);
                head.add(col);
            }
        }
        return head;
    }
}