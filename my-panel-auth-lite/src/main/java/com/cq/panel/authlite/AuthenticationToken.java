package com.cq.panel.authlite;

public final class AuthenticationToken {

    private final Object principal;
    private final Object credentials;
    private final boolean authenticated;

    public AuthenticationToken(Object principal, Object credentials, boolean authenticated) {
        this.principal = principal;
        this.credentials = credentials;
        this.authenticated = authenticated;
    }

    public Object getPrincipal() {
        return principal;
    }

    public Object getCredentials() {
        return credentials;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}

