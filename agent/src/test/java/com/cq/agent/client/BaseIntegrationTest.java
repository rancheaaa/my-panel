package com.cq.agent.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 基础集成测试类，提供公共的HTTP请求方法
 *
 * @author cq 2026/3/18
 * @since 1.0.0
 */
public class BaseIntegrationTest {

    protected static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);
//    protected static final String AGENT_URL = "http://172.19.200.130:7777";
    protected static final String AGENT_URL = "http://172.31.140.63:7777";

    /**
     * 发送GET请求
     * @param urlString 请求URL
     * @return 响应内容（JsonObject形式）
     * @throws IOException  IOException
     */
    protected JsonObject sendGetRequest(String urlString) throws IOException {
        logger.info("Sending GET request: {}", urlString);

        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
//            connection.setRequestProperty("connection", "keep-alive");
            connection.setRequestProperty("connection", "close");
            connection.setRequestProperty("X-Trace-Id", UUID.randomUUID().toString().replace("-", ""));

            int responseCode = connection.getResponseCode();
            logger.info("GET request response code: {}", responseCode);

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String response = reader.readLine();
            logger.info("GET request response: {}", response);
            return JsonParser.parseString(response).getAsJsonObject();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            // Don't disconnect to keep the connection alive for reuse
        }
    }

    /**
     * 发送POST请求
     * @param urlString 请求URL
     * @param requestBody 请求体
     * @return 响应内容（JsonObject形式）
     * @throws IOException  IOException
     */
    protected JsonObject sendPostRequest(String urlString, String requestBody) throws IOException {
        logger.info("Sending POST request: {}", urlString);

        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("connection", "close");
            connection.setRequestProperty("X-Trace-Id", UUID.randomUUID().toString().replace("-", ""));
//            connection.setRequestProperty("connection", "keep-alive");

            // Send request body
            connection.getOutputStream().write(requestBody.getBytes());

            int responseCode = connection.getResponseCode();
            logger.info("POST request response code: {}", responseCode);

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String response = reader.readLine();
            logger.info("POST request response: {}", response);
            return JsonParser.parseString(response).getAsJsonObject();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            // Don't disconnect to keep the connection alive for reuse
        }
    }
}