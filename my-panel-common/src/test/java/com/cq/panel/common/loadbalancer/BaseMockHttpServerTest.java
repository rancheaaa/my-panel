package com.cq.panel.common.loadbalancer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 *
 * @author cq 2026/3/31 13:53
 * @since 1.0.0
 */
public class BaseMockHttpServerTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseMockHttpServerTest.class);

    protected static HttpServer server;
    protected static final int PORT = 38888;
    protected static final String BASE_URL = "http://localhost:" + PORT;
    protected static final Gson GSON = new Gson();
    protected static final Type MAP_TYPE_TOKEN = new TypeToken<Map<String, Object>>() {
    }.getType();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // JSON响应处理器
        server.createContext("/json", new JsonHandler());
        server.createContext("/health", new HealthHandler());

        // XML响应处理器
        server.createContext("/xml", new XmlHandler());

        // 文本响应处理器
        server.createContext("/text", new TextHandler());

        // 二进制响应处理器
        server.createContext("/binary", new BinaryHandler());

        // 自定义Content-Type处理器
        server.createContext("/custom", new CustomContentTypeHandler());

        // 错误响应处理器
        server.createContext("/error", new ErrorHandler());

        // POST请求处理器
        server.createContext("/post", new PostHandler());

        // PUT请求处理器
        server.createContext("/put", new PutHandler());

        // DELETE请求处理器
        server.createContext("/delete", new DeleteHandler());

        // PATCH请求处理器
        server.createContext("/patch", new PatchHandler());

        server.setExecutor(null); // 使用默认执行器
        server.start();
        System.out.println("Mock server started on port " + PORT);

        // 等待服务器完全启动
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
            System.out.println("Mock server stopped");
        }
    }

    // JSON处理器
    static class JsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = """
                    {
                        "name": "test",
                        "age": 25,
                        "active": true,
                        "tags": ["tag1", "tag2"]
                    }
                    """;

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Server", "MockServer/1.0");
            exchange.sendResponseHeaders(200, response.getBytes().length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    // 健康检查处理器
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = """
                    {
                        "status": "UP"
                    }
                    """;

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Server", "MockServer/1.0");
            exchange.sendResponseHeaders(200, response.getBytes().length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    // XML处理器
    static class XmlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <data>
                        <name>test</name>
                        <value>123</value>
                        <active>true</active>
                    </data>
                    """;

            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            exchange.getResponseHeaders().set("Server", "MockServer/1.0");
            exchange.sendResponseHeaders(200, response.getBytes().length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    // 文本处理器
    static class TextHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String response;

            if (path.endsWith("/number")) {
                response = "42";
            } else if (path.endsWith("/boolean")) {
                response = "true";
            } else {
                response = "Hello World";
            }

            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("Server", "MockServer/1.0");
            exchange.sendResponseHeaders(200, response.getBytes().length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    // 二进制处理器
    static class BinaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] response = "Binary data content".getBytes();

            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Server", "MockServer/1.0");
            exchange.sendResponseHeaders(200, response.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    // 自定义Content-Type处理器
    static class CustomContentTypeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Custom data response";

            exchange.getResponseHeaders().set("Content-Type", "custom/data");
            exchange.getResponseHeaders().set("Server", "MockServer/1.0");
            exchange.sendResponseHeaders(200, response.getBytes().length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    // 错误处理器
    static class ErrorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Not Found";

            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("Server", "MockServer/1.0");
            exchange.sendResponseHeaders(404, response.getBytes().length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    // POST请求处理器
    static class PostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String response = """
                        {
                            "received": true,
                            "method": "POST",
                            "headers": {
                                "Content-Type": "application/json"
                            }
                        }
                        """;

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Server", "MockServer/1.0");
                exchange.sendResponseHeaders(200, response.getBytes().length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    // PUT请求处理器
    static class PutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("PUT".equals(exchange.getRequestMethod())) {
                String response = """
                        {
                            "received": true,
                            "method": "PUT",
                            "headers": {
                                "Authorization": "Bearer test-token",
                                "X-Custom-Header": "custom-value"
                            }
                        }
                        """;

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Server", "MockServer/1.0");
                exchange.sendResponseHeaders(200, response.getBytes().length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    // DELETE请求处理器
    static class DeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("DELETE".equals(exchange.getRequestMethod())) {
                String response = "Deleted successfully";

                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.getResponseHeaders().set("Server", "MockServer/1.0");
                exchange.sendResponseHeaders(200, response.getBytes().length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    // PATCH请求处理器
    static class PatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("PATCH".equals(exchange.getRequestMethod())) {
                String response = """
                        {
                            "received": true,
                            "method": "PATCH",
                            "status": "active"
                        }
                        """;

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Server", "MockServer/1.0");
                exchange.sendResponseHeaders(200, response.getBytes().length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }
}
