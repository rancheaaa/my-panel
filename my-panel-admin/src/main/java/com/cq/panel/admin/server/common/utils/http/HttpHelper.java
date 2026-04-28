package com.cq.panel.admin.server.common.utils.http;

import jakarta.servlet.ServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 通用http工具封装
 * 
 * @author cq
 */
public class HttpHelper
{
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpHelper.class);

    public static String getBodyString(ServletRequest request)
    {
        StringBuilder sb = new StringBuilder();
        try (InputStream inputStream = request.getInputStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            LOGGER.warn("getBodyString出现问题！");
        }
        return sb.toString();
    }
}
