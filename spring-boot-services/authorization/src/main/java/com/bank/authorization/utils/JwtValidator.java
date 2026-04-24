package com.bank.authorization.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtValidator {

    private final JwtTokenUtil jwtTokenUtil;

    public void validate(String token, String requiredRole) {
        if (!jwtTokenUtil.validateToken(token) || !jwtTokenUtil.getAuthoritiesFromToken(token).contains(requiredRole)) {
            throw new SecurityException("Invalid token or insufficient permissions");
        }
    }
}
