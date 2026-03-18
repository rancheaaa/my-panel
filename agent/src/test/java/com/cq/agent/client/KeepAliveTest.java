package com.cq.agent.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class to verify HTTP keep-alive functionality
 * This test sends multiple requests and checks if they reuse the same TCP connection
 */
public class KeepAliveTest {

    private static final Logger logger = LoggerFactory.getLogger(KeepAliveTest.class);
    private static final String AGENT_URL = "http://172.19.200.130:7777";

    @Test
    public void testKeepAlive() throws Exception {
        logger.info("Testing HTTP keep-alive functionality");
        
        List<String> responses = new ArrayList<>();
        
        // Create a URL connection with keep-alive enabled
        URL url = new URL(AGENT_URL + "/api/health");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setDoInput(true);
        connection.setRequestMethod("GET");
        connection.setDoOutput(false);
        
        try {
            // Send multiple requests over the same connection
            for (int i = 0; i < 5; i++) {
                logger.info("Sending request {}", i + 1);
                
                // For subsequent requests, we need to create a new connection but reuse the underlying socket
                if (i > 0) {
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("Connection", "keep-alive");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setDoInput(true);
                    connection.setRequestMethod("GET");
                    connection.setDoOutput(false);
                }
                
                int responseCode = connection.getResponseCode();
                logger.info("Response code for request {}: {}", i + 1, responseCode);
                
                // Read response
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    String responseStr = response.toString();
                    responses.add(responseStr);
                    logger.info("Response for request {}: {}", i + 1, responseStr);
                }
                
                // Don't disconnect to keep the connection alive
            }
        } finally {
            connection.disconnect();
        }
        
        // Verify all requests were successful
        assertTrue(responses.size() == 5, "Expected 5 responses, got " + responses.size());
        for (String response : responses) {
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();
            assertTrue(jsonResponse.has("status"), "Response should contain status field");
            assertTrue(jsonResponse.get("status").getAsString().equals("UP"), "Status should be UP");
        }
        
        logger.info("Keep-alive test completed successfully");
    }
    
    @Test
    public void testMultipleEndpointsWithKeepAlive() throws Exception {
        logger.info("Testing multiple endpoints with HTTP keep-alive");
        
        // Test 1: Health check
        logger.info("Testing /api/health");
        URL healthUrl = new URL(AGENT_URL + "/api/health");
        HttpURLConnection connection = (HttpURLConnection) healthUrl.openConnection();
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setDoInput(true);
        connection.setRequestMethod("GET");
        connection.setDoOutput(false);
        
        int responseCode1 = connection.getResponseCode();
        logger.info("Health check response code: {}", responseCode1);
        
        // Read response
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            logger.info("Health check response: {}", response.toString());
        }
        
        // Don't disconnect to keep the connection alive
        
        // Test 2: System info (using the same connection)
        logger.info("Testing /api/file/syst");
        URL systUrl = new URL(AGENT_URL + "/api/file/syst");
        connection = (HttpURLConnection) systUrl.openConnection();
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setDoInput(true);
        connection.setRequestMethod("GET");
        connection.setDoOutput(false);
        
        int responseCode2 = connection.getResponseCode();
        logger.info("System info response code: {}", responseCode2);
        
        // Read response
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            logger.info("System info response: {}", response.toString());
        }
        
        // Don't disconnect to keep the connection alive
        
        // Test 3: Working directory (using the same connection)
        logger.info("Testing /api/file/pwd");
        URL pwdUrl = new URL(AGENT_URL + "/api/file/pwd");
        connection = (HttpURLConnection) pwdUrl.openConnection();
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setDoInput(true);
        connection.setRequestMethod("GET");
        connection.setDoOutput(false);
        
        int responseCode3 = connection.getResponseCode();
        logger.info("Working directory response code: {}", responseCode3);
        
        // Read response
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            logger.info("Working directory response: {}", response.toString());
        }
        
        // Verify all requests were successful
        assertTrue(responseCode1 == 200, "Health check should return 200");
        assertTrue(responseCode2 == 200, "System info should return 200");
        assertTrue(responseCode3 == 200, "Working directory should return 200");
        
        connection.disconnect();
        
        logger.info("Multiple endpoints keep-alive test completed successfully");
    }
}