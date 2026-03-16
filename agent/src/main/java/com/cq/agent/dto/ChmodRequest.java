package com.cq.agent.dto;

/**
 * Request body for chmod.
 */
public class ChmodRequest {

    private String path;
    private String permissions;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }
}
