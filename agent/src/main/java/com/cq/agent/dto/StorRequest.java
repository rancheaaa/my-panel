package com.cq.agent.dto;

/**
 * Request body for store file (path + content).
 */
public class StorRequest {

    private String path;
    private String content;
    private String encoding;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}
