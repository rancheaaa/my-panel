package com.cq.panel.authlite;

public final class AuthContext {

    private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setCurrentUser(User user) {
        CURRENT.set(user);
    }

    public static User getCurrentUser() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}

