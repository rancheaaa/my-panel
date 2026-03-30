package com.cq.panel.authlite;

import java.util.Collections;
import java.util.Set;

public final class User {

    private final String username;
    private final Set<String> roles;
    private final Set<String> permissions;

    public User(String username, Set<String> roles, Set<String> permissions) {
        this.username = username;
        this.roles = roles == null ? Collections.emptySet() : Collections.unmodifiableSet(roles);
        this.permissions = permissions == null ? Collections.emptySet() : Collections.unmodifiableSet(permissions);
    }

    public String getUsername() {
        return username;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}

