package com.cq.panel.authlite.filter;

import com.cq.panel.authlite.User;

public interface TokenValidator {
    User validate(String token);
}

