package com.bank.authorization.dto;

import lombok.Data;

import java.util.List;

@Data
public class AuthResponse {
    private String jwt;
    private List<String> authorities;
}
